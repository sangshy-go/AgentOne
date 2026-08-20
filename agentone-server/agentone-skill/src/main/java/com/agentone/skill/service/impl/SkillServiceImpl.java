package com.agentone.skill.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.skill.core.ConfirmTokenStore;
import com.agentone.skill.core.SkillCallLogRecorder;
import com.agentone.skill.core.SkillCategories;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.core.UrlSafetyUtil;
import com.agentone.skill.dto.SkillDTO;
import com.agentone.skill.dto.SkillImportDTO;
import com.agentone.skill.entity.AgentSkillBindingDO;
import com.agentone.skill.entity.McpToolPublishDO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.entity.SkillPackageFileDO;
import com.agentone.skill.executor.McpSkillExecutor;
import com.agentone.skill.executor.UserSkillExecutors;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.McpServerMapper;
import com.agentone.skill.mapper.McpToolPublishMapper;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.mapper.SkillPackageFileMapper;
import com.agentone.skill.service.SkillService;
import com.agentone.skill.vo.InvokeSkillVO;
import com.agentone.skill.vo.SkillExportVO;
import com.agentone.skill.vo.SkillPackageFileVO;
import com.agentone.skill.vo.SkillVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 中心服务实现。
 *
 * 管理两类用户 Skill：
 * - api：HTTP API 封装（面向 IT/集成侧，config 含 url，创建/执行双重 SSRF 校验）
 * - prompt：内容型指令（面向全员创建，config 含 content，无出站请求）
 *
 * 共性：DB 落 skill 表（租户拦截器自动隔离）；每次写操作同步维护 SkillRegistry，
 * 保证 Agent 对话侧立即可用。
 *
 * Skill 中心 v2 扩展：导入技能包（文件夹/zip，SKILL.md 解析 + 自动判型）、
 * 启停 toggle、动作型技能两阶段确认执行（草稿→确认→执行 + 审计）。
 */
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String EXPORT_FORMAT = "agentone-skill";

    /** 技能包规模上限：防超大上传拖垮服务 */
    private static final int MAX_PACKAGE_FILES = 200;
    private static final long MAX_PACKAGE_FILE_SIZE = 10L * 1024 * 1024;

    /** 脚本扩展名（判型用：scripts/ 目录或以下扩展名 → 脚本包） */
    private static final Set<String> SCRIPT_EXTENSIONS =
            Set.of("py", "js", "ts", "sh", "rb", "go", "php");

    /** SKILL.md frontmatter：--- yaml ---（与 Claude Skills 一致） */
    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("\\A---\\r?\\n(.*?)\\r?\\n---\\r?\\n?", Pattern.DOTALL);

    /** 广场直接调用的审计来源标记（对话调用为 Agent ID，调试器为 debugger） */
    private static final String INVOKE_AGENT_ID = "plaza";

    /** /test 调用的审计来源标记（与调试器一致，便于按来源区分） */
    private static final String TEST_AGENT_ID = "debugger";

    private final SkillMapper skillMapper;
    private final AgentSkillBindingMapper bindingMapper;
    private final SkillRegistry skillRegistry;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SkillPackageFileMapper packageFileMapper;
    private final McpToolPublishMapper mcpToolPublishMapper;
    private final McpServerMapper mcpServerMapper;
    private final ConfirmTokenStore confirmTokenStore;
    private final SkillCallLogRecorder callLogRecorder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO create(SkillDTO dto) {
        return createInternal(dto, "custom");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO update(String skillId, SkillDTO dto) {
        SkillDO skill = requireUserSkill(skillId);
        String type = resolveType(dto.getType());
        if (!type.equals(skill.getType())) {
            throw new BusinessException(5007, "不允许变更 Skill 类型（" + skill.getType() + " → " + type + "）");
        }
        validatePayload(type, dto);

        skill.setName(dto.getName());
        skill.setDescription(dto.getDescription());
        skill.setCategory(resolveCategory(dto.getCategory()));
        skill.setConfig(dto.getConfig());
        // prompt 型无参数，schema 固定为空（防用户填写无效字段造成误解）
        if ("prompt".equals(type)) {
            skill.setInputSchema("{}");
            skill.setOutputSchema("{}");
        } else {
            skill.setInputSchema(defaultEmpty(dto.getInputSchema()));
            skill.setOutputSchema(defaultEmpty(dto.getOutputSchema()));
        }
        if (dto.getVersion() != null && !dto.getVersion().isBlank()) {
            skill.setVersion(dto.getVersion());
        }
        skillMapper.updateById(skill);

        // 刷新执行器：Registry 以 skillId 为 key，register 即覆盖
        skillRegistry.register(UserSkillExecutors.create(skill, webClient, objectMapper));
        return toVO(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String skillId) {
        requireUserSkill(skillId);

        Long bindingCount = bindingMapper.selectCount(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .eq(AgentSkillBindingDO::getSkillId, skillId));
        if (bindingCount > 0) {
            throw new BusinessException(5005, "该 Skill 仍被 Agent 绑定，请先解除绑定");
        }

        // 先删子表 skill_package_file（FK 引用 skill.id），再删主表，否则外键冲突
        packageFileMapper.delete(new LambdaQueryWrapper<SkillPackageFileDO>()
                .eq(SkillPackageFileDO::getSkillId, skillId));
        skillMapper.deleteById(skillId);
        skillRegistry.unregister(skillId);
    }

    @Override
    public SkillResult test(String skillId, Map<String, Object> params) {
        Map<String, Object> safeParams = params != null ? params : Map.of();
        SkillExecutor executor = resolveExecutor(skillId);
        // 动作型技能的 test 同样会真实执行并产生副作用（发邮件/改数据），
        // 必须走 invoke 的「草稿 → 确认令牌 → 执行」两阶段，否则 test 成为绕过确认的后门
        if (executor.getDescriptor().isActionType()) {
            throw new BusinessException(5017,
                    "动作型技能需先确认再执行，请使用「试一试」的两阶段确认流程（invoke）");
        }
        String traceId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        Context context = Context.of(RuntimeContext.getUserId(), RuntimeContext.getWorkspaceId());
        // 与 invoke 共用执行 + 审计链路：任何真实执行都必须落 skill_call_log
        return executeWithAudit(executor, skillId, safeParams, traceId, TEST_AGENT_ID, context);
    }

    @Override
    public SkillExportVO export(String skillId) {
        SkillDO skill = requireUserSkill(skillId);
        SkillExportVO vo = new SkillExportVO();
        vo.setFormat(EXPORT_FORMAT);
        vo.setFormatVersion(1);
        vo.setName(skill.getName());
        vo.setType(skill.getType());
        vo.setCategory(skill.getCategory());
        vo.setDescription(skill.getDescription());
        vo.setConfig(skill.getConfig());
        vo.setInputSchema(skill.getInputSchema());
        vo.setOutputSchema(skill.getOutputSchema());
        vo.setVersion(skill.getVersion());
        vo.setExportedAt(LocalDateTime.now().toString());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO importSkill(SkillImportDTO dto) {
        if (dto.getFormat() != null && !dto.getFormat().isBlank()
                && !EXPORT_FORMAT.equals(dto.getFormat())) {
            throw new BusinessException(5007, "不是 AgentOne Skill 导出文件（format=" + dto.getFormat() + "）");
        }
        String type = resolveType(dto.getType());

        // 同名重复拦截：防重复导入造成混淆（租户拦截器保证只查本空间）
        Long dup = skillMapper.selectCount(
                new LambdaQueryWrapper<SkillDO>()
                        .eq(SkillDO::getName, dto.getName())
                        .eq(SkillDO::getType, type));
        if (dup > 0) {
            throw new BusinessException(5013, "已存在同名 Skill，请先删除或改名后再导入");
        }

        SkillDTO createDto = new SkillDTO();
        createDto.setName(dto.getName());
        createDto.setType(type);
        createDto.setCategory(dto.getCategory());
        createDto.setDescription(dto.getDescription());
        createDto.setConfig(dto.getConfig());
        createDto.setInputSchema(dto.getInputSchema());
        createDto.setOutputSchema(dto.getOutputSchema());
        createDto.setVersion(dto.getVersion());
        return createInternal(createDto, "imported");
    }

    // ============================================================
    // Skill 中心 v2：导入技能包
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO importPackage(MultipartFile zip, List<MultipartFile> files, List<String> paths) {
        Map<String, byte[]> entries = readPackageEntries(zip, files, paths);
        entries = stripCommonRoot(entries);

        // SKILL.md 是技能包的必需入口（内容型与脚本包共用）
        String skillMdPath = entries.keySet().stream()
                .filter(p -> "SKILL.md".equalsIgnoreCase(p))
                .findFirst()
                .orElseThrow(() -> new BusinessException(5014,
                        "技能包缺少 SKILL.md：请把技能说明文件放在包根目录后重新导入"));
        String skillMd = new String(entries.get(skillMdPath), StandardCharsets.UTF_8);

        String name = frontmatterValue(skillMd, "name");
        if (name == null || name.isBlank()) {
            throw new BusinessException(5014, "SKILL.md 缺少 frontmatter name 字段，无法识别技能名称");
        }

        // 同名重复拦截（与 importSkill 一致）
        Long dup = skillMapper.selectCount(
                new LambdaQueryWrapper<SkillDO>()
                        .eq(SkillDO::getName, name)
                        .eq(SkillDO::getType, "prompt"));
        if (dup > 0) {
            throw new BusinessException(5013, "已存在同名 Skill，请先删除或改名后再导入");
        }

        // 技能正文 = SKILL.md 去掉 frontmatter 后的主体；作为内容型指令存储
        SkillDTO createDto = new SkillDTO();
        createDto.setName(name);
        createDto.setType("prompt");
        createDto.setCategory(frontmatterValue(skillMd, "category"));
        createDto.setDescription(frontmatterValue(skillMd, "description"));
        createDto.setConfig(writeJson(Map.of("content", stripFrontmatter(skillMd))));
        SkillVO vo = createInternal(createDto, "imported");

        // 落技能包文件（脚本本期仅存储与随包分发，不执行）
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            SkillPackageFileDO file = new SkillPackageFileDO();
            file.setSkillId(vo.getId());
            file.setPath(entry.getKey());
            file.setKind(classifyPackageFile(entry.getKey()));
            file.setSize((long) entry.getValue().length);
            file.setContent(isProbablyText(entry.getValue())
                    ? new String(entry.getValue(), StandardCharsets.UTF_8) : null);
            file.setCreatedAt(now);
            packageFileMapper.insert(file);
        }
        return vo;
    }

    @Override
    public List<SkillPackageFileVO> listPackageFiles(String skillId) {
        // 先校验父 skill 归属（租户拦截器过滤跨空间行），skill_package_file 本身无 workspace_id
        SkillDO skill = skillMapper.selectById(skillId);
        if (skill == null) {
            throw new BusinessException(5002, "Skill 不存在");
        }
        return packageFileMapper.selectList(
                        new LambdaQueryWrapper<SkillPackageFileDO>()
                                .eq(SkillPackageFileDO::getSkillId, skillId)
                                .orderByAsc(SkillPackageFileDO::getPath))
                .stream()
                .map(f -> {
                    SkillPackageFileVO vo = new SkillPackageFileVO();
                    vo.setPath(f.getPath());
                    vo.setKind(f.getKind());
                    vo.setSize(f.getSize());
                    vo.setContent(f.getContent());
                    return vo;
                })
                .toList();
    }

    // ============================================================
    // Skill 中心 v2：启停 toggle
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO setStatus(String skillId, boolean enabled) {
        // MCP 虚拟技能：启停 = 发布/撤回到广场（治理语义对齐）
        if (skillId != null && skillId.startsWith(McpSkillExecutor.SKILL_ID_PREFIX)) {
            return setMcpToolPublished(skillId, enabled);
        }

        SkillDO skill = skillMapper.selectById(skillId);
        if (skill == null) {
            // 内置技能由系统托管（全局共享，不属于任何工作空间），不提供启停
            SkillDescriptor virtual = skillRegistry.getExecutor(skillId)
                    .map(SkillExecutor::getDescriptor).orElse(null);
            if (virtual != null && "builtin".equals(virtual.getType())) {
                throw new BusinessException(5006, "内置技能由系统托管，不支持启停");
            }
            throw new BusinessException(5002, "Skill 不存在");
        }
        if (!UserSkillExecutors.isUserType(skill.getType())) {
            throw new BusinessException(5006, "内置 Skill 不可修改");
        }

        skill.setStatus(enabled ? "active" : "disabled");
        skillMapper.updateById(skill);
        // 同步 Registry：停用即卸载执行器（Agent 对话侧不再可调用），启用重新装配
        if (enabled) {
            skillRegistry.register(UserSkillExecutors.create(skill, webClient, objectMapper));
        } else {
            skillRegistry.unregister(skillId);
        }
        return toVO(skill);
    }

    private SkillVO setMcpToolPublished(String skillId, boolean published) {
        String serverId = McpSkillExecutor.serverIdOf(skillId);
        // selectById 自带租户过滤：查不到即不属于当前工作空间
        if (serverId == null || mcpServerMapper.selectById(serverId) == null) {
            throw new BusinessException(5009, "MCP Server 不存在");
        }
        String toolName = skillId.substring((McpSkillExecutor.SKILL_ID_PREFIX + serverId + "-").length());
        if (toolName.isBlank()) {
            throw new BusinessException(5010, "MCP 工具名不合法");
        }

        McpToolPublishDO row = mcpToolPublishMapper.selectOne(
                new LambdaQueryWrapper<McpToolPublishDO>()
                        .eq(McpToolPublishDO::getServerId, serverId)
                        .eq(McpToolPublishDO::getToolName, toolName));
        if (row == null) {
            row = new McpToolPublishDO();
            row.setWorkspaceId(RuntimeContext.getWorkspaceId());
            row.setServerId(serverId);
            row.setToolName(toolName);
            row.setPublished(published);
            row.setUpdatedAt(LocalDateTime.now());
            mcpToolPublishMapper.insert(row);
        } else {
            row.setPublished(published);
            row.setUpdatedAt(LocalDateTime.now());
            mcpToolPublishMapper.updateById(row);
        }

        // 组装返回：元信息取 Registry 描述符（可能离线，离线时仅回 ID/状态）
        SkillVO vo = new SkillVO();
        vo.setId(skillId);
        vo.setType("mcp");
        vo.setStatus("active");
        vo.setPublished(published);
        skillRegistry.getExecutor(skillId).map(SkillExecutor::getDescriptor).ifPresent(d -> {
            vo.setName(d.getName());
            vo.setDescription(d.getDescription());
            vo.setCategory(d.getCategory());
            vo.setVersion(d.getVersion());
            vo.setActionType(d.isActionType());
        });
        return vo;
    }

    // ============================================================
    // Skill 中心 v2：广场调用（动作型两阶段确认）
    // ============================================================

    @Override
    public InvokeSkillVO invoke(String skillId, Map<String, Object> params, String confirmToken) {
        Map<String, Object> safeParams = params != null ? params : Map.of();
        SkillExecutor executor = resolveExecutor(skillId);
        SkillDescriptor descriptor = executor.getDescriptor();

        // 阶段 1：动作型技能首次调用只回草稿 + 确认令牌，绝不执行
        if (descriptor.isActionType() && (confirmToken == null || confirmToken.isBlank())) {
            InvokeSkillVO vo = new InvokeSkillVO();
            vo.setSkillId(skillId);
            vo.setSkillName(descriptor.getName());
            vo.setConfirmRequired(true);
            vo.setConfirmToken(confirmTokenStore.issue(skillId, safeParams));
            vo.setDraftParams(safeParams);
            return vo;
        }
        // 阶段 2：令牌校验（绑定技能 + 参数摘要 + 5 分钟有效期，一次性消费）
        if (descriptor.isActionType()
                && !confirmTokenStore.consume(confirmToken, skillId, safeParams)) {
            throw new BusinessException(5015, "确认令牌无效或已过期，请重新生成草稿并确认");
        }

        String traceId = "invoke-" + UUID.randomUUID().toString().substring(0, 8);
        Context context = Context.of(RuntimeContext.getUserId(), RuntimeContext.getWorkspaceId());
        // 审计：广场/详情页直接调用也写 skill_call_log（agentId=plaza 标记来源）
        SkillResult result = executeWithAudit(executor, skillId, safeParams, traceId,
                INVOKE_AGENT_ID, context);

        InvokeSkillVO vo = new InvokeSkillVO();
        vo.setSkillId(skillId);
        vo.setSkillName(descriptor.getName());
        vo.setConfirmRequired(false);
        vo.setSuccess(result.isSuccess());
        vo.setData(result.getData());
        vo.setErrorMessage(result.getErrorMessage());
        vo.setDurationMs(result.getDurationMs());
        vo.setTraceId(traceId);
        return vo;
    }

    /**
     * 执行 + 审计（test / invoke 共用）：执行异常兜底为失败结果，
     * 无论成功失败都写 skill_call_log，agentId 标记来源。
     */
    private SkillResult executeWithAudit(SkillExecutor executor, String skillId,
                                         Map<String, Object> params, String traceId,
                                         String auditAgentId, Context context) {
        SkillInvocation invocation = SkillInvocation.builder()
                .skillId(skillId)
                .params(params)
                .traceId(traceId)
                .build();

        long start = System.currentTimeMillis();
        SkillResult result;
        try {
            result = executor.execute(invocation, context);
        } catch (Exception e) {
            result = SkillResult.failure("执行异常: " + e.getMessage(), System.currentTimeMillis() - start);
        }
        if (result.getDurationMs() == null) {
            result.setDurationMs(System.currentTimeMillis() - start);
        }

        callLogRecorder.record(context.getWorkspaceId(), auditAgentId, traceId,
                skillId, traceId, writeJson(params), writeJson(result.getData()),
                result.getDurationMs(), result.isSuccess(), result.getErrorMessage());
        return result;
    }

    /**
     * 解析执行器（test/invoke 共用）：优先 Registry；不在 Registry（如已停用）
     * 时从 DB 行临时构建。Registry 跨租户共享，描述符标注归属空间的 Skill 需校验越权。
     */
    private SkillExecutor resolveExecutor(String skillId) {
        SkillExecutor executor = skillRegistry.getExecutor(skillId).orElse(null);
        if (executor == null) {
            SkillDO skill = skillMapper.selectById(skillId);
            if (skill == null) {
                throw new BusinessException(5002, "Skill 不存在");
            }
            if (!UserSkillExecutors.isUserType(skill.getType())) {
                throw new BusinessException(5006, "该 Skill 不支持直接调用");
            }
            executor = UserSkillExecutors.create(skill, webClient, objectMapper);
        }
        SkillDescriptor descriptor = executor.getDescriptor();
        if (!descriptor.isEnabled()) {
            throw new BusinessException(5016, "技能已停用，无法执行（如需使用请先启用）");
        }
        if (descriptor.getWorkspaceId() != null
                && !descriptor.getWorkspaceId().equals(RuntimeContext.getWorkspaceId())) {
            throw new BusinessException(5004, "无权调用其他工作空间的 Skill");
        }
        return executor;
    }

    // ============================================================
    // 技能包解析辅助
    // ============================================================

    /** 读取包内文件：zip 或「多文件 + 相对路径」，统一为 path→bytes */
    private Map<String, byte[]> readPackageEntries(MultipartFile zip,
                                                   List<MultipartFile> files, List<String> paths) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        if (zip != null && !zip.isEmpty()) {
            parseZip(zip, entries);
        } else if (files != null && !files.isEmpty()) {
            if (paths == null || paths.size() != files.size()) {
                throw new BusinessException(5014, "文件与相对路径数量不一致，请重新选择文件夹");
            }
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file.isEmpty()) {
                    continue;
                }
                String path = normalizePackagePath(paths.get(i));
                putEntry(entries, path, file.getSize(), () -> file.getBytes());
            }
        } else {
            throw new BusinessException(5014, "请选择技能文件夹或上传 .zip 技能包");
        }
        if (entries.isEmpty()) {
            throw new BusinessException(5014, "技能包为空");
        }
        return entries;
    }

    /** zip 解压（zip slip 防护：条目路径规范化后仍在包内才接受） */
    private void parseZip(MultipartFile zip, Map<String, byte[]> entries) {
        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                // 跳过 macOS Finder 压缩时自动生成的资源 fork 目录
                if (isMacOsResourceFork(name)) {
                    continue;
                }
                String path = normalizePackagePath(name);
                // 边读边计数：不能先整条目读入内存再校验大小（zip bomb 可撑爆堆）
                byte[] content = readLimited(zis, MAX_PACKAGE_FILE_SIZE, path);
                putEntry(entries, path, content.length, () -> content);
                zis.closeEntry();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(5014, "zip 解析失败: " + e.getMessage());
        }
    }

    private void putEntry(Map<String, byte[]> entries, String path, long size,
                          BytesSupplier supplier) {
        if (entries.size() >= MAX_PACKAGE_FILES) {
            throw new BusinessException(5014, "技能包文件数超过上限（" + MAX_PACKAGE_FILES + " 个）");
        }
        if (size > MAX_PACKAGE_FILE_SIZE) {
            throw new BusinessException(5014, "文件超过 10MB 上限: " + path);
        }
        try {
            entries.put(path, supplier.get());
        } catch (Exception e) {
            throw new BusinessException(5014, "读取文件失败: " + path);
        }
    }

    @FunctionalInterface
    private interface BytesSupplier {
        byte[] get() throws Exception;
    }

    /**
     * 增量读取单个条目：累计字节数一旦超过上限立即中止并抛错（解压炸弹防护），
     * 不把整个条目读完再校验大小。
     */
    private byte[] readLimited(InputStream in, long limit, String path) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > limit) {
                throw new BusinessException(5014, "文件超过 10MB 上限: " + path);
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * 判断是否为 macOS Finder 压缩时自动生成的资源 fork 文件：
     * - 路径以 __MACOSX/ 开头，或
     * - 文件名以 ._ 开头（AppleDouble 格式）
     */
    private boolean isMacOsResourceFork(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        String normalized = rawPath.replace('\\', '/');
        if (normalized.startsWith("__MACOSX/") || normalized.contains("/__MACOSX/")) {
            return true;
        }
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return fileName.startsWith("._");
    }

    /**
     * 路径规范化 + zip slip 防护：
     * 反斜杠转正斜杠、去前导 ./ 与 /，任何含 .. 段或规范化后越界的路径一律拒绝。
     */
    private String normalizePackagePath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(5014, "技能包中存在空路径文件");
        }
        String path = raw.replace('\\', '/').strip();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isBlank() || path.length() > 500) {
            throw new BusinessException(5014, "技能包路径非法: " + raw);
        }
        for (String segment : path.split("/")) {
            if (segment.isBlank() || "..".equals(segment) || ".".equals(segment)) {
                throw new BusinessException(5014, "技能包路径包含非法段（zip slip 防护）: " + raw);
            }
        }
        return path;
    }

    /** zip 整包文件夹打包时所有条目共享同一根目录前缀 → 剥离，保证 SKILL.md 在根 */
    private Map<String, byte[]> stripCommonRoot(Map<String, byte[]> entries) {
        if (entries.size() < 2) {
            return entries;
        }
        String first = entries.keySet().iterator().next();
        int slash = first.indexOf('/');
        if (slash <= 0) {
            return entries;
        }
        String root = first.substring(0, slash + 1);
        boolean allShare = entries.keySet().stream().allMatch(p -> p.startsWith(root));
        if (!allShare) {
            return entries;
        }
        Map<String, byte[]> stripped = new LinkedHashMap<>();
        entries.forEach((path, content) -> stripped.put(path.substring(root.length()), content));
        stripped.keySet().removeIf(String::isBlank);
        return stripped.isEmpty() ? entries : stripped;
    }

    /** 文件判型：SKILL.md 与 md 文档为 doc；scripts/ 下或脚本扩展名为 script；其余为 resource */
    private String classifyPackageFile(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".md")) {
            return "doc";
        }
        if (lower.startsWith("scripts/")) {
            return "script";
        }
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && SCRIPT_EXTENSIONS.contains(lower.substring(dot + 1))) {
            return "script";
        }
        return "resource";
    }

    /** 粗略文本判断：含 NUL 字节视为二进制（content 不落库，只存元信息） */
    private boolean isProbablyText(byte[] bytes) {
        int probe = Math.min(bytes.length, 8192);
        for (int i = 0; i < probe; i++) {
            if (bytes[i] == 0) {
                return false;
            }
        }
        return true;
    }

    /** frontmatter 简单 key: value 解析（name/description/category 单行值） */
    private String frontmatterValue(String skillMd, String key) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(skillMd);
        if (!matcher.find()) {
            return null;
        }
        for (String line : matcher.group(1).split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).strip().equalsIgnoreCase(key)) {
                String value = line.substring(colon + 1).strip();
                // 去掉成对引号
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    /** 去掉 frontmatter 头，返回正文 */
    private String stripFrontmatter(String skillMd) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(skillMd);
        if (matcher.find()) {
            return skillMd.substring(matcher.end()).strip();
        }
        return skillMd.strip();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ============================================================
    // 既有创建链路
    // ============================================================

    private SkillVO createInternal(SkillDTO dto, String source) {
        String type = resolveType(dto.getType());
        validatePayload(type, dto);

        SkillDO skill = new SkillDO();
        skill.setWorkspaceId(RuntimeContext.getWorkspaceId());
        skill.setName(dto.getName());
        skill.setType(type);
        skill.setSource(source);
        skill.setDescription(dto.getDescription());
        skill.setCategory(resolveCategory(dto.getCategory()));
        // prompt 型无参数，schema 固定为空
        if ("prompt".equals(type)) {
            skill.setInputSchema("{}");
            skill.setOutputSchema("{}");
        } else {
            skill.setInputSchema(defaultEmpty(dto.getInputSchema()));
            skill.setOutputSchema(defaultEmpty(dto.getOutputSchema()));
        }
        skill.setConfig(dto.getConfig());
        skill.setVersion(dto.getVersion() != null && !dto.getVersion().isBlank()
                ? dto.getVersion() : "1.0.0");
        skill.setStatus("active");
        skill.setInstalledAt(LocalDateTime.now());
        skillMapper.insert(skill);

        // 注册执行器：创建成功后 Agent 立即可绑定、可对话调用
        skillRegistry.register(UserSkillExecutors.create(skill, webClient, objectMapper));
        return toVO(skill);
    }

    /** 业务分类解析：空白归入"其他"（受控词表由前端维护，后端不做枚举校验） */
    private String resolveCategory(String category) {
        return category != null && !category.isBlank() ? category : SkillCategories.DEFAULT;
    }

    /** 类型解析：缺省 api（向后兼容既有调用方）；仅接受 api / prompt */
    private String resolveType(String type) {
        if (type == null || type.isBlank()) {
            return "api";
        }
        if (!UserSkillExecutors.isUserType(type)) {
            throw new BusinessException(5007, "不支持的 Skill 类型: " + type);
        }
        return type;
    }

    /**
     * 按类型校验 config / schema：
     * - api：config 必须为含 url 的 JSON 且通过 SSRF 检查
     * - prompt：config 必须为含非空 content 的 JSON（无出站请求，无 SSRF 校验）
     */
    private void validatePayload(String type, SkillDTO dto) {
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(dto.getConfig(), MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException(5007, "Skill 配置不是合法 JSON");
        }

        if ("prompt".equals(type)) {
            Object content = config.get("content");
            if (content == null || content.toString().isBlank()) {
                throw new BusinessException(5007, "内容型 Skill 配置缺少 content");
            }
            return;
        }

        Object url = config.get("url");
        if (url == null || url.toString().isBlank()) {
            throw new BusinessException(5007, "Skill 配置缺少 url");
        }
        try {
            UrlSafetyUtil.validate(url.toString());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(5008, "Skill 地址不安全: " + e.getMessage());
        }
        validateSchemaJson(dto.getInputSchema(), "inputSchema");
        validateSchemaJson(dto.getOutputSchema(), "outputSchema");
    }

    private void validateSchemaJson(String json, String field) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException(5007, field + " 不是合法 JSON");
        }
    }

    /** 加载 skill 行并要求是用户创建型（api/prompt）；租户拦截器保证查不到其他工作空间的数据 */
    private SkillDO requireUserSkill(String skillId) {
        SkillDO skill = skillMapper.selectById(skillId);
        if (skill == null) {
            throw new BusinessException(5002, "Skill 不存在");
        }
        if (!UserSkillExecutors.isUserType(skill.getType())) {
            throw new BusinessException(5006, "内置 Skill 不可修改");
        }
        return skill;
    }

    private String defaultEmpty(String value) {
        return value != null && !value.isBlank() ? value : "{}";
    }

    private SkillVO toVO(SkillDO skill) {
        SkillVO vo = new SkillVO();
        BeanUtils.copyProperties(skill, vo);
        return vo;
    }
}
