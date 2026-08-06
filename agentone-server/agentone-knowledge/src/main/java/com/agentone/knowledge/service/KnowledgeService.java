package com.agentone.knowledge.service;

import com.agentone.knowledge.dto.BindKnowledgeDTO;
import com.agentone.knowledge.dto.KnowledgeBaseDTO;
import com.agentone.knowledge.vo.AgentKnowledgeBindingVO;
import com.agentone.knowledge.vo.DocumentVO;
import com.agentone.knowledge.vo.KnowledgeBaseVO;
import com.agentone.knowledge.vo.SearchResultVO;
import org.springframework.web.multipart.MultipartFile;
import com.agentone.common.result.PageResult;

import java.util.List;

/**
 * 知识库服务
 */
public interface KnowledgeService {

    // ==================== 知识库管理 ====================

    /**
     * 创建知识库
     */
    KnowledgeBaseVO createKnowledgeBase(KnowledgeBaseDTO dto);

    /**
     * 获取知识库列表
     */
    PageResult<KnowledgeBaseVO> listKnowledgeBases(Integer page, Integer size);

    /**
     * 获取知识库详情
     */
    KnowledgeBaseVO getKnowledgeBase(String id);

    /**
     * 更新知识库
     */
    KnowledgeBaseVO updateKnowledgeBase(String id, KnowledgeBaseDTO dto);

    /**
     * 删除知识库
     */
    void deleteKnowledgeBase(String id);

    // ==================== 文档管理 ====================

    /**
     * 上传文档
     */
    DocumentVO uploadDocument(String knowledgeId, MultipartFile file);

    /**
     * 获取文档列表
     */
    PageResult<DocumentVO> listDocuments(String knowledgeId, Integer page, Integer size);

    /**
     * 删除文档
     */
    void deleteDocument(String documentId);

    /**
     * 重试失败的文档处理
     */
    DocumentVO retryDocument(String documentId);

    // ==================== 检索 ====================

    /**
     * 检索知识库
     */
    List<SearchResultVO> search(String knowledgeId, String query, int topK);

    // ==================== Agent 绑定 ====================

    /**
     * 绑定知识库到 Agent
     */
    AgentKnowledgeBindingVO bindKnowledge(BindKnowledgeDTO dto);

    /**
     * 解绑知识库
     */
    void unbindKnowledge(String bindingId);

    /**
     * 更新绑定的检索参数（topK / similarityThreshold）
     */
    AgentKnowledgeBindingVO updateBinding(String bindingId, BindKnowledgeDTO dto);

    /**
     * 获取 Agent 绑定的知识库列表
     */
    List<AgentKnowledgeBindingVO> listBindings(String agentId);

    /**
     * 删除 Agent 的全部知识库绑定（Agent 被物理删除时级联调用）
     */
    void removeAllBindings(String agentId);

    /**
     * 获取知识库被哪些 Agent 绑定（用于删除知识库前提示依赖方）
     */
    List<AgentKnowledgeBindingVO> listBindingsByKnowledge(String knowledgeId);
}
