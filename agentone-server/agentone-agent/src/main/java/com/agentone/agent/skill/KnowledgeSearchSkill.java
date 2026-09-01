package com.agentone.agent.skill;

import com.agentone.common.context.Context;
import com.agentone.knowledge.service.KnowledgeService;
import com.agentone.knowledge.vo.AgentKnowledgeBindingVO;
import com.agentone.knowledge.vo.SearchResultVO;
import com.agentone.skill.core.SkillCategories;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索 Skill（第三个 builtin）。
 * 与"默认 RAG 注入"的区别：由 LLM 在 ReAct 循环中自主决定何时检索、用什么 query 检索，
 * 检索范围仍限定为当前 Agent 绑定的知识库（按绑定阈值过滤）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchSkill implements SkillExecutor {

    private static final String SKILL_ID = "builtin-knowledge-search";
    private static final int DEFAULT_TOP_K = 5;
    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 20;

    private final KnowledgeService knowledgeService;

    @Override
    public SkillResult execute(SkillInvocation invocation, Context context) {
        long startTime = System.currentTimeMillis();

        Map<String, Object> params = invocation.getParams();
        Object queryObj = params != null ? params.get("query") : null;
        String query = queryObj != null ? queryObj.toString() : null;
        if (query == null || query.isBlank()) {
            return SkillResult.failure("query 不能为空", System.currentTimeMillis() - startTime);
        }
        if (context.getAgentId() == null || context.getAgentId().isBlank()) {
            return SkillResult.failure("缺少 Agent 上下文，无法确定检索范围",
                    System.currentTimeMillis() - startTime);
        }
        int topK = DEFAULT_TOP_K;
        Object topKObj = params.get("top_k");
        if (topKObj instanceof Number) {
            topK = ((Number) topKObj).intValue();
        }
        // 限制 top_k 范围，防止 LLM 传入过大值导致检索放大 / 上下文膨胀（Bug4）
        if (topK < MIN_TOP_K) {
            topK = MIN_TOP_K;
        } else if (topK > MAX_TOP_K) {
            topK = MAX_TOP_K;
        }

        try {
            List<AgentKnowledgeBindingVO> bindings = knowledgeService.listBindings(context.getAgentId());
            if (bindings.isEmpty()) {
                Map<String, Object> data = new HashMap<>();
                data.put("results", List.of());
                data.put("count", 0);
                data.put("message", "当前 Agent 未绑定任何知识库");
                return SkillResult.success(data, System.currentTimeMillis() - startTime);
            }

            List<SearchResultVO> merged = new ArrayList<>();
            for (AgentKnowledgeBindingVO binding : bindings) {
                // 阈值过滤下沉到知识服务（传 null 则不过滤），与 /search HTTP 入口同口径
                List<SearchResultVO> results = knowledgeService.search(
                        binding.getKnowledgeId(), query, topK, binding.getSimilarityThreshold());
                merged.addAll(results);
            }
            merged.sort((a, b) -> Double.compare(
                    b.getScore() != null ? b.getScore() : 0,
                    a.getScore() != null ? a.getScore() : 0));
            List<SearchResultVO> top = merged.size() > topK ? merged.subList(0, topK) : merged;

            List<Map<String, Object>> items = new ArrayList<>();
            for (SearchResultVO r : top) {
                Map<String, Object> item = new HashMap<>();
                item.put("documentName", r.getDocumentName());
                item.put("content", r.getContent());
                item.put("score", r.getScore());
                items.add(item);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("results", items);
            data.put("count", items.size());
            return SkillResult.success(data, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("知识库检索 Skill 执行失败: agentId={}, error={}", context.getAgentId(), e.getMessage());
            return SkillResult.failure("知识库检索失败: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public SkillDescriptor getDescriptor() {
        return SkillDescriptor.builder()
                .id(SKILL_ID)
                .name("知识库检索")
                .description("在当前 Agent 绑定的知识库中检索与 query 最相关的文本片段，用于回答需要私有知识的问题")
                .type("builtin")
                .version("1.0.0")
                .source("agentone")
                .category(SkillCategories.IT)
                .enabled(true)
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "检索语句"),
                                "top_k", Map.of("type", "integer", "description", "返回条数，默认 5")
                        ),
                        "required", List.of("query")
                ))
                .build();
    }
}
