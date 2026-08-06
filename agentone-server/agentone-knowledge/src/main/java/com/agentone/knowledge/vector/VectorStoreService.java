package com.agentone.knowledge.vector;

import com.agentone.knowledge.entity.ModelDO;
import com.agentone.knowledge.entity.ModelProviderDO;
import com.agentone.knowledge.mapper.ModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量存储服务（Provider 模式）
 *
 * 使用知识库绑定的 Provider + Embedding 模型的 API 凭证，
 * 存储在维度隔离表（vector_store_{dim}）中，支持不同知识库使用不同维度的 Embedding 模型。
 *
 * 直接用 JDBC 操作维度隔离表，不依赖 Spring AI PgVectorStore 的自动建表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final JdbcTemplate jdbcTemplate;
    private final ModelMapper modelMapper;

    /** 已初始化的维度表缓存，避免重复检查/建表 */
    private final ConcurrentHashMap<Integer, Boolean> initializedTables = new ConcurrentHashMap<>();

    private static final String TABLE_PREFIX = "vector_store_";

    // ==================== 向量化存储与检索（Provider 模式，按维度隔离） ====================

    /**
     * 批量向量化并存储（使用指定 Provider + Embedding 模型）
     *
     * @param documents      待向量化的文档分块
     * @param provider       API 凭证（baseUrl + apiKey）
     * @param embeddingModel 知识库绑定的 Embedding 模型（决定 modelId 和 dimensions）
     */
    public void saveBatchWithProvider(List<Document> documents, ModelProviderDO provider, ModelDO embeddingModel) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        EmbeddingModel embedding = buildEmbeddingModel(provider, embeddingModel);

        // 维度未知时，先用第一条文本探测向量长度，并回写到 model 记录
        int dims = ensureDimensionsResolved(embeddingModel, embedding);
        ensureTableExists(dims);

        // 批量调用 Embedding API
        List<float[]> embeddings = embedding.embed(documents.stream()
                .map(Document::getText)
                .toList());

        // JDBC 批量写入维度隔离表
        String tableName = TABLE_PREFIX + dims;
        String sql = String.format(
                "INSERT INTO %s (id, embedding, metadata) VALUES (?::text, ?::text::vector, ?::jsonb)",
                tableName);

        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String vectorStr = floatsToString(embeddings.get(i));
            String metadataJson = doc.getMetadata() != null ? mapToJson(doc.getMetadata()) : "{}";
            batchArgs.add(new Object[]{doc.getId(), vectorStr, metadataJson});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);

        log.debug("Provider 批量向量保存成功: provider={}, model={}, dims={}, count={}",
                provider.getName(), embeddingModel.getModelId(), dims, documents.size());
    }

    /**
     * 删除向量（使用指定 Provider + Embedding 模型对应的维度表）
     */
    public void deleteWithProvider(String id, ModelProviderDO provider, ModelDO embeddingModel) {
        int dims = resolveDimensions(embeddingModel);
        // 维度未知说明从未存过数据，无需删除
        if (dims < 0) {
            return;
        }
        String tableName = TABLE_PREFIX + dims;

        // 表可能不存在（首次删除时），直接忽略
        try {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE id = ?", id);
        } catch (Exception e) {
            log.debug("删除向量时表不存在或出错，忽略: table={}, id={}, error={}", tableName, id, e.getMessage());
        }
    }

    /**
     * 余弦相似度检索（使用指定 Provider + Embedding 模型对应的维度表）
     *
     * @param knowledgeId 知识库 ID（必须传，用于隔离不同知识库的向量数据）
     */
    public List<VectorSearchResult> searchWithProvider(String query, int topK, ModelProviderDO provider, ModelDO embeddingModel, String knowledgeId) {
        int dims = resolveDimensions(embeddingModel);
        // 维度未知说明从未存过数据，直接返回空
        if (dims < 0) {
            return List.of();
        }
        String tableName = TABLE_PREFIX + dims;

        if (!tableExists(tableName)) {
            return List.of();
        }

        EmbeddingModel embedding = buildEmbeddingModel(provider, embeddingModel);
        float[] queryEmbedding = embedding.embed(query);
        String vectorStr = floatsToString(queryEmbedding);

        // 按 knowledgeId 隔离：只检索当前知识库的向量，防止跨库串数据
        String sql = String.format(
                "SELECT id, 1 - (embedding <=> ?::text::vector) AS score " +
                "FROM %s " +
                "WHERE metadata->>'knowledgeId' = ? " +
                "ORDER BY embedding <=> ?::text::vector " +
                "LIMIT ?",
                tableName);

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                VectorSearchResult.builder()
                        .chunkId(rs.getString("id"))
                        .score(rs.getDouble("score"))
                        .build(),
                vectorStr, knowledgeId, vectorStr, topK);
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 Provider 专属 EmbeddingModel（轻量级对象，复用 HTTP 连接池）
     * 使用知识库绑定的具体 embedding 模型，不再从数据库随意查询
     */
    private EmbeddingModel buildEmbeddingModel(ModelProviderDO provider, ModelDO embeddingModel) {
        // Spring AI 1.0.0 的 OpenAiApi 会自动拼接 /v1 前缀，
        // 用户配置的 baseUrl 若已含 /v1 后缀需先去掉，避免 /v1/v1 导致 404
        String baseUrl = provider.getBaseUrl();
        if (baseUrl != null && baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(provider.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModel.getModelId())
                .build();

        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options);
    }

    /**
     * 确保维度对应的向量表存在（懒创建，每个维度只建一次）
     */
    private void ensureTableExists(int dimensions) {
        if (initializedTables.containsKey(dimensions)) {
            return;
        }

        String tableName = TABLE_PREFIX + dimensions;

        // 检查表是否存在
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tableName);

        if (count == null || count == 0) {
            String createTableSql = String.format(
                    "CREATE TABLE %s (" +
                    "  id TEXT PRIMARY KEY," +
                    "  embedding vector(%d)," +
                    "  metadata JSONB DEFAULT '{}')",
                    tableName, dimensions);
            jdbcTemplate.execute(createTableSql);

            // 创建余弦距离索引
            String createIndexSql = String.format(
                    "CREATE INDEX idx_%s_embedding ON %s USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)",
                    dimensions, tableName);
            try {
                jdbcTemplate.execute(createIndexSql);
            } catch (Exception e) {
                // IVFFlat 索引需要至少 rows_per_list 条数据，数据不足时会失败，忽略即可
                log.debug("创建向量索引失败（数据量不足，稍后重试）: table={}, error={}", tableName, e.getMessage());
            }

            log.info("创建维度向量表: table={}, dimensions={}", tableName, dimensions);
        }

        initializedTables.put(dimensions, true);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    /**
     * 解析 embedding 模型维度。
     * 维度未知时（添加模型时用户不需要填写），用一次 API 调用探测向量长度并回写。
     * delete / search 场景如果维度仍未知，说明还没存过数据，返回 -1 让调用方直接跳过。
     */
    private int resolveDimensions(ModelDO embeddingModel) {
        return embeddingModel.getDimensions() != null && embeddingModel.getDimensions() > 0
                ? embeddingModel.getDimensions()
                : -1;
    }

    /**
     * 确保维度已知：已记录则直接返回，否则通过 Spring AI 内置机制解析并回写。
     *
     * Spring AI 的 EmbeddingModel.dimensions() 内部策略：
     * 1. 先查内置 embedding-model-dimensions.properties（覆盖 OpenAI / Cohere / Vertex 等常见模型）
     * 2. 查不到时，自动调一次 API 探测 float[].length
     *
     * 我们只需调用框架方法，拿到结果后持久化到 DB，后续调用直接读取。
     */
    private int ensureDimensionsResolved(ModelDO embeddingModel, EmbeddingModel embedding) {
        if (embeddingModel.getDimensions() != null && embeddingModel.getDimensions() > 0) {
            return embeddingModel.getDimensions();
        }

        // 走 Spring AI 内置机制：先查静态注册表，未知模型才走 API 探测
        int dims = embedding.dimensions();

        // 回写到 model 记录，后续调用直接读取，不再重复解析
        embeddingModel.setDimensions(dims);
        modelMapper.updateById(embeddingModel);

        log.info("解析 Embedding 模型维度: modelId={}, dims={}", embeddingModel.getModelId(), dims);
        return dims;
    }

    /** float[] 转 PgVector 字符串格式：[0.1,0.2,0.3] */
    private String floatsToString(float[] floats) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < floats.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(floats[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /** Map 转 JSON 字符串（避免引入额外 JSON 依赖） */
    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
