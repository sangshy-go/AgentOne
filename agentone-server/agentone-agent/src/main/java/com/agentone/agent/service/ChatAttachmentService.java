package com.agentone.agent.service;

import com.agentone.agent.entity.ChatAttachmentDO;
import com.agentone.agent.vo.AttachmentVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 对话附件服务
 */
public interface ChatAttachmentService {

    /**
     * 上传附件（校验+解析+入库）
     */
    AttachmentVO upload(MultipartFile file);

    /**
     * 按 ids 批量加载附件（归属校验：缺 4010 / 越权 4011）
     */
    List<ChatAttachmentDO> listByIds(List<String> ids);

    /**
     * 附件元信息（归属校验）
     */
    AttachmentVO getMeta(String id);

    /**
     * 流式返回附件字节（归属校验）
     */
    Resource streamData(String id);
}
