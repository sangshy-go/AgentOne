package com.agentone.skill.service;

import com.agentone.skill.core.SkillResult;
import com.agentone.skill.dto.SkillDTO;
import com.agentone.skill.dto.SkillImportDTO;
import com.agentone.skill.vo.InvokeSkillVO;
import com.agentone.skill.vo.SkillExportVO;
import com.agentone.skill.vo.SkillPackageFileVO;
import com.agentone.skill.vo.SkillVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Skill 中心服务：用户创建型 Skill（api / prompt）的生命周期管理。
 * builtin / mcp 为虚拟挂载，不落库、不可修改，不在本服务范围内。
 */
public interface SkillService {

    /**
     * 创建用户 Skill（api=HTTP 封装 / prompt=内容型指令），
     * 并注册到 SkillRegistry（立即可被 Agent 绑定使用）
     */
    SkillVO create(SkillDTO dto);

    /**
     * 更新用户 Skill，并刷新 Registry 中的执行器
     */
    SkillVO update(String skillId, SkillDTO dto);

    /**
     * 删除用户 Skill（存在 Agent 绑定时拒绝），并从 Registry 注销
     */
    void delete(String skillId);

    /**
     * 测试调用：直接执行 Skill 并返回真实结果（调试器 / 创建向导用）
     */
    SkillResult test(String skillId, Map<String, Object> params);

    /**
     * 导出用户 Skill 为 JSON 定义（发布/分享的最小形态）
     */
    SkillExportVO export(String skillId);

    /**
     * 导入 Skill 定义：与创建同校验链路，source 标记 imported；同名重复拒绝（5013）
     */
    SkillVO importSkill(SkillImportDTO dto);

    /**
     * 导入技能包（Skill 中心 v2）：整个文件夹（多文件 + 相对路径）或 .zip 包。
     * 解析 SKILL.md frontmatter 带出名称/描述，自动识别内容型 vs 脚本包，
     * 落 skill + skill_package_file；zip 解压做路径穿越（zip slip）防护。
     *
     * @param zip   zip 包（与 files/paths 二选一）
     * @param files 多文件上传（文件夹选择场景）
     * @param paths 与 files 一一对应的包内相对路径（前端 webkitRelativePath 传来）
     */
    SkillVO importPackage(MultipartFile zip, List<MultipartFile> files, List<String> paths);

    /**
     * 技能包文件树（详情抽屉展示 SKILL.md + scripts + resources；脚本仅存储不执行）
     */
    List<SkillPackageFileVO> listPackageFiles(String skillId);

    /**
     * 启用/停用技能（Skill 中心 v2 我的技能 toggle）：
     * 用户 Skill 切换 status 并同步 Registry；MCP 工具切换发布状态。
     * 内置技能由系统托管，拒绝操作（5006）。
     */
    SkillVO setStatus(String skillId, boolean enabled);

    /**
     * 广场调用（试一试 / 动作型确认执行）。
     * 动作型技能两阶段：无 confirmToken 返回草稿 + 令牌；带有效令牌才真实执行并写审计。
     * 非动作型直接执行并写审计。
     */
    InvokeSkillVO invoke(String skillId, Map<String, Object> params, String confirmToken);
}
