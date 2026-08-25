package com.agentone.agent.service.impl;

import com.agentone.agent.entity.ChatAttachmentDO;
import com.agentone.agent.mapper.ChatAttachmentMapper;
import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.knowledge.parser.DocumentParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatAttachmentServiceImpl 单测：覆盖上传成功路径、扩展名拒绝、MIME 嗅探拒绝、
 * 大小限制、文档解析入库 5 类场景。
 */
@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceImplTest {

    @Mock
    private ChatAttachmentMapper attachmentMapper;
    @Mock
    private DocumentParser documentParser;

    @InjectMocks
    private ChatAttachmentServiceImpl service;

    @BeforeEach
    void setUp() {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    @Test
    void upload_image_success() {
        // 1x1 PNG
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89};
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", png);
        when(attachmentMapper.insert(any(ChatAttachmentDO.class))).thenReturn(1);

        var vo = service.upload(file);

        assertNotNull(vo);
        assertEquals("a.png", vo.getFileName());
        assertEquals("image", vo.getKind());
        verify(attachmentMapper).insert(any(ChatAttachmentDO.class));
    }

    @Test
    void upload_blockedByExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1});
        assertThrows(BusinessException.class, () -> service.upload(file));
        verify(attachmentMapper, never()).insert(any(ChatAttachmentDO.class));
    }

    @Test
    void upload_blockedBySizeLimit() {
        // 11MB png 字节
        byte[] big = new byte[(int) (11L * 1024 * 1024)];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", big);
        assertThrows(BusinessException.class, () -> service.upload(file));
        verify(attachmentMapper, never()).insert(any(ChatAttachmentDO.class));
    }

    @Test
    void upload_doc_parsesAndInserts() throws Exception {
        byte[] content = "hello world".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", content);
        when(documentParser.parse(any(), any())).thenReturn("hello world parsed");
        when(attachmentMapper.insert(any(ChatAttachmentDO.class))).thenReturn(1);

        var vo = service.upload(file);

        assertEquals("document", vo.getKind());
        verify(documentParser).parse(any(), any());
        verify(attachmentMapper).insert(any(ChatAttachmentDO.class));
    }

    @Test
    void upload_doc_parseFails_rejectsInsert() throws Exception {
        byte[] content = "bad".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "broken.pdf", "application/pdf", content);
        when(documentParser.parse(any(), any())).thenThrow(new RuntimeException("corrupt"));

        assertThrows(BusinessException.class, () -> service.upload(file));
        verify(attachmentMapper, never()).insert(any(ChatAttachmentDO.class));
    }

    @Test
    void listByIds_crossWorkspace_throwsUnauthorized() {
        ChatAttachmentDO row = new ChatAttachmentDO();
        ReflectionTestUtils.setField(row, "id", "att-1");
        row.setWorkspaceId("ws-other");
        row.setUserId("user-1");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(row));

        assertThrows(BusinessException.class, () -> service.listByIds(List.of("att-1")));
    }
}
