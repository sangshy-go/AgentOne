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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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

    /** 已建立 IVFFlat 索引的维度表缓存（索引只在数据量足够后才创建） */
    private final ConcurrentHashMap<Integer, Boolean> indexReadyTables = new ConcurrentHashMap<>();

    /** 每个维度表一把锁，避免并发首次建表/建索引出现竞态 */
    private final ConcurrentHashMap<Integer, Object> tableLocks = new ConcurrentHashMap<>();

    private static final String TABLE_PREFIX = "vector_store_";

    /** 低于该数据量时不创建 IVFFlat 索引（pgvector 要求足够数据，否则 CREATE INDEX 失败） */
    private static final int INDEX_MIN_ROWS = 1000;

    /**
     * Embedding HTTP 调用超时与重试（P3）。
     *
     * 此前 Embedding 调用既无超时也无重试：远端抖动/瞬时 5xx 会让整篇文档直接失败进 error，
     * 且无超时时请求可能长时间挂住，占满异步处理线程池。
     * 现在：连接 10s / 读取 60s（Embedding 批量请求较慢，读超时给足），失败最多重试 2 次并线性退避。
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    /** 总尝试次数 = 1 次首发 + 2 次重试 */
    private static final int EMBEDDING_MAX_ATTEMPTS = 3;
    private static final long EMBEDDING_RETRY_BACKOFF_MS = 500L;

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

        // 批量调用 Embedding API（带重试，抵御远端瞬时抖动）
        List<String> texts = documents.stream().map(Document::getText).toList();
        List<float[]> embeddings = callWithRetry(() -> embedding.embed(texts),
                "batch embed(size=" + texts.size() + ")");

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

        // 写入后按需补充 IVFFlat 索引（空表/小表 CREATE INDEX 会失败，故延迟到数据量足够时）
        ensureIndexExists(dims);

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
        float[] queryEmbedding = callWithRetry(() -> embedding.embed(query), "query embed");
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
     * Embedding 调用重试：失败最多重试 2 次，线性退避（500ms / 1000ms）。
     * 用于抵御远端瞬时抖动导致整篇文档处理失败；重试耗尽后抛出最后一次异常，交由上层回滚/置 error。
     */
    private <T> T callWithRetry(Supplier<T> call, String action) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= EMBEDDING_MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt == EMBEDDING_MAX_ATTEMPTS) {
                    break;
                }
                long backoff = EMBEDDING_RETRY_BACKOFF_MS * attempt;
                log.warn("Embedding 调用失败，{}ms 后重试（第 {}/{} 次尝试）: action={}, error={}",
                        backoff, attempt, EMBEDDING_MAX_ATTEMPTS, action, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        log.error("Embedding 调用重试 {} 次后仍失败: action={}, error={}",
                EMBEDDING_MAX_ATTEMPTS - 1, action, lastError.getMessage());
        throw lastError;
    }

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

        // 显式设置连接/读取超时，避免远端无响应时请求长时间挂住并占满异步处理线程
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(provider.getApiKey())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModel.getModelId())
                .build();

        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options);
    }

    /**
     * 确保维度对应的向量表存在（懒创建，每个维度只建一次）。
     * 使用 per-dims 锁避免并发首次建表的竞态；建表失败若是 "表已存在" 则视为成功。
     */
    private void ensureTableExists(int dimensions) {
        if (initializedTables.containsKey(dimensions)) {
            return;
        }

        Object lock = tableLocks.computeIfAbsent(dimensions, k -> new Object());
        synchronized (lock) {
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
                try {
                    jdbcTemplate.execute(createTableSql);
                    log.info("创建维度向量表: table={}, dimensions={}", tableName, dimensions);
                } catch (Exception e) {
                    // 并发场景下另一线程可能已建好表（PSQLException duplicate_table，SQLState=42P07），视为成功
                    if (isDuplicateTable(e)) {
                        log.debug("向量表已存在（并发建表），忽略: table={}", tableName);
                    } else {
                        throw e;
                    }
                }
            }

            initializedTables.put(dimensions, true);
        }
    }

    /**
     * 确保维度表的 IVFFlat 索引已建立。
     * 仅在数据量超过阈值（INDEX_MIN_ROWS）后才创建，避免空表 CREATE INDEX 失败被吞掉导致
     * 所有向量表都退化为全表扫描。索引建立成功才标记 indexReady；否则留待后续写入重试。
     */
    private void ensureIndexExists(int dimensions) {
        if (indexReadyTables.containsKey(dimensions)) {
            return;
        }

        Object lock = tableLocks.computeIfAbsent(dimensions, k -> new Object());
        synchronized (lock) {
            if (indexReadyTables.containsKey(dimensions)) {
                return;
            }

            String tableName = TABLE_PREFIX + dimensions;
            if (!tableExists(tableName)) {
                return;
            }

            Integer rowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName, Integer.class);
            if (rowCount == null || rowCount <= INDEX_MIN_ROWS) {
                // 数据量不足，本次不建索引，下次写入积累够数据后再试
                return;
            }

            // 索引可能已存在（如本实例重启后），先探测避免重复建索引报错
            if (indexExists(dimensions)) {
                indexReadyTables.put(dimensions, true);
                return;
            }

            String createIndexSql = String.format(
                    "CREATE INDEX idx_%s_embedding ON %s USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)",
                    dimensions, tableName);
            try {
                jdbcTemplate.execute(createIndexSql);
                indexReadyTables.put(dimensions, true);
                log.info("创建维度向量索引: table={}, dimensions={}, rows={}", tableName, dimensions, rowCount);
            } catch (Exception e) {
                if (isDuplicateIndex(e)) {
                    indexReadyTables.put(dimensions, true);
                } else {
                    // 索引未建成功：不标记 indexReady，留待后续写入按数据量重试，避免静默退化
                    log.warn("创建向量索引失败（留待后续重试）: table={}, rows={}, error={}",
                            tableName, rowCount, e.getMessage());
                }
            }
        }
    }

    /**
     * 检查 IVFFlat 索引是否已存在
     */
    private boolean indexExists(int dimensions) {
        String tableName = TABLE_PREFIX + dimensions;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?",
                Integer.class, "idx_" + dimensions + "_embedding");
        return count != null && count > 0;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    /**
     * 探测 embedding 模型的真实向量维度（调用一次 API），供 createModel 在入库前校验维度。
     * 不在此处持久化，由调用方写入 model 记录。
     */
    public int probeEmbeddingDimensions(ModelProviderDO provider, ModelDO embeddingModel) {
        EmbeddingModel embedding = buildEmbeddingModel(provider, embeddingModel);
        return embedding.dimensions();
    }

    /** 判断异常是否为 "表已存在"（PostgreSQL SQLState=42P07, duplicate_table） */
    private boolean isDuplicateTable(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException se && "42P07".equals(se.getSQLState())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /** 判断异常是否为 "索引已存在"（PostgreSQL SQLState=42P07, duplicate_table） */
    private boolean isDuplicateIndex(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException se && "42P07".equals(se.getSQLState())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
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
