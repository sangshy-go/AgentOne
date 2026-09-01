package com.agentone.agent.controller;

import com.agentone.agent.service.ChatAttachmentService;
import com.agentone.agent.vo.AttachmentVO;
import com.agentone.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对话附件接口
 */
@RestController
@RequestMapping("/api/chat/attachments")
@RequiredArgsConstructor
public class ChatAttachmentController {

    private final ChatAttachmentService attachmentService;

    @PostMapping
    public Result<AttachmentVO> upload(MultipartFile file) {
        return Result.ok(attachmentService.upload(file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> stream(@PathVariable String id) {
        AttachmentVO meta = attachmentService.getMeta(id);
        Resource body = attachmentService.streamData(id);
        MediaType mt = parseMediaType(meta.getMimeType());
        return ResponseEntity.ok()
                .contentType(mt)
                .header("Content-Disposition", "inline; filename=\"" + sanitize(meta.getFileName()) + "\"")
                .body(body);
    }

    private static MediaType parseMediaType(String mime) {
        if (mime == null) return MediaType.APPLICATION_OCTET_STREAM;
        String[] parts = mime.split("/", 2);
        if (parts.length == 2) {
            try { return new MediaType(parts[0], parts[1]); } catch (Exception ignore) { /* fallthrough */ }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /** 去引号与换行，防 Content-Disposition 注入 */
    private static String sanitize(String fileName) {
        return fileName == null ? "" : fileName.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
