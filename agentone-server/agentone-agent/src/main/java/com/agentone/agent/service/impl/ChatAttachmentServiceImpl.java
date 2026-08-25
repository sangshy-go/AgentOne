package com.agentone.agent.service.impl;

import com.agentone.agent.entity.ChatAttachmentDO;
import com.agentone.agent.mapper.ChatAttachmentMapper;
import com.agentone.agent.service.ChatAttachmentService;
import com.agentone.agent.vo.AttachmentVO;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.knowledge.parser.DocumentParser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 对话附件服务实现
 *
 * 安全校验链路：扩展名白名单 → Tika MIME 嗅探 → 大小上限 → 文档解析（失败拒绝入库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Set<String> DOC_EXTS = Set.of("pdf", "docx", "txt", "md", "csv");

    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_DOC_SIZE = 20L * 1024 * 1024;

    private static final Set<String> BLOCKED_MIME = Set.of(
            "text/html", "application/xhtml+xml", "image/svg+xml");

    private static final int PARSED_TEXT_CAP = 200_000;

    private static final Tika TIKA = new Tika();

    private final ChatAttachmentMapper attachmentMapper;
    private final DocumentParser documentParser;

    @Override
    public AttachmentVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(4012, "请选择要上传的文件");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException(4012, "文件名无效");
        }

        String ext = getFileExt(originalName);
        boolean isImage = IMAGE_EXTS.contains(ext);
        boolean isDoc = DOC_EXTS.contains(ext);
        if (!isImage && !isDoc) {
            throw new BusinessException(4012, "不支持的文件类型（仅支持图片 png/jpg/jpeg/webp/gif 与文档 pdf/docx/txt/md/csv）");
        }

        // 大小上限
        long size = file.getSize();
        long limit = isImage ? MAX_IMAGE_SIZE : MAX_DOC_SIZE;
        if (size > limit) {
            throw new BusinessException(4013, "文件超过大小限制（图片 " + (MAX_IMAGE_SIZE / 1024 / 1024)
                    + "MB，文档 " + (MAX_DOC_SIZE / 1024 / 1024) + "MB）");
        }

        // MIME 嗅探（与知识库一致：防扩展名伪装）
        verifyContentType(file, originalName);

        // 文档即时解析（失败拒绝入库，4014）
        String parsedText = null;
        if (isDoc) {
            try (InputStream in = file.getInputStream()) {
                String text = documentParser.parse(in, ext);
                if (text != null && !text.isBlank()) {
                    parsedText = text.length() > PARSED_TEXT_CAP ? text.substring(0, PARSED_TEXT_CAP) : text;
                }
            } catch (Exception e) {
                log.warn("附件文档解析失败: name={}, error={}", originalName, e.getMessage());
                throw new BusinessException(4014, "文档解析失败：" + e.getMessage());
            }
        }

        // 入库
        byte[] data;
        try (InputStream in = file.getInputStream()) {
            data = in.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(4014, "读取文件失败");
        }

        ChatAttachmentDO row = new ChatAttachmentDO();
        row.setWorkspaceId(RuntimeContext.getWorkspaceId());
        row.setUserId(RuntimeContext.getUserId());
        row.setFileName(originalName);
        row.setMimeType(resolveMimeType(file.getContentType(), ext));
        row.setFileSize(size);
        row.setKind(isImage ? "image" : "document");
        row.setData(data);
        row.setParsedText(parsedText);
        row.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(row);

        return toVO(row);
    }

    @Override
    public List<ChatAttachmentDO> listByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 去重保序
        LinkedHashSet<String> unique = new LinkedHashSet<>(ids);
        List<ChatAttachmentDO> rows = attachmentMapper.selectList(
                new LambdaQueryWrapper<ChatAttachmentDO>()
                        .in(ChatAttachmentDO::getId, unique));
        if (rows.size() != unique.size()) {
            Set<String> found = new LinkedHashSet<>();
            rows.forEach(r -> found.add(r.getId()));
            List<String> missing = unique.stream().filter(i -> !found.contains(i)).toList();
            throw new BusinessException(4010, "附件不存在：" + String.join(",", missing));
        }
        String ws = RuntimeContext.getWorkspaceId();
        String uid = RuntimeContext.getUserId();
        List<String> bad = new ArrayList<>();
        for (ChatAttachmentDO r : rows) {
            if (!ws.equals(r.getWorkspaceId()) || !uid.equals(r.getUserId())) {
                bad.add(r.getId());
            }
        }
        if (!bad.isEmpty()) {
            throw new BusinessException(4011, "无权访问附件：" + String.join(",", bad));
        }
        return rows;
    }

    @Override
    public AttachmentVO getMeta(String id) {
        ChatAttachmentDO row = requireOwner(id);
        return toVO(row);
    }

    @Override
    public Resource streamData(String id) {
        ChatAttachmentDO row = requireOwner(id);
        return new InputStreamResource(new ByteArrayInputStream(row.getData()));
    }

    // ==================== 内部方法 ====================

    private ChatAttachmentDO requireOwner(String id) {
        ChatAttachmentDO row = attachmentMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(4010, "附件不存在");
        }
        String ws = RuntimeContext.getWorkspaceId();
        String uid = RuntimeContext.getUserId();
        if (!ws.equals(row.getWorkspaceId()) || !uid.equals(row.getUserId())) {
            throw new BusinessException(4011, "无权访问附件");
        }
        return row;
    }

    private static String getFileExt(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase();
    }

    private static String resolveMimeType(String fromFile, String ext) {
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.split(";")[0].trim().toLowerCase();
        }
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            case "md" -> "text/markdown";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    private static void verifyContentType(MultipartFile file, String originalName) {
        try (InputStream in = file.getInputStream()) {
            String detected = TIKA.detect(in, originalName);
            if (detected != null) {
                String mime = detected.split(";")[0].trim().toLowerCase();
                if (BLOCKED_MIME.contains(mime)) {
                    throw new BusinessException(4012,
                            "文件内容类型不被允许（检测为 " + mime + "），出于安全考虑已拒绝上传");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("内容类型嗅探失败，跳过: name={}, error={}", originalName, e.getMessage());
        }
    }

    private static AttachmentVO toVO(ChatAttachmentDO row) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(row.getId());
        vo.setFileName(row.getFileName());
        vo.setMimeType(row.getMimeType());
        vo.setFileSize(row.getFileSize());
        vo.setKind(row.getKind());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }
}
