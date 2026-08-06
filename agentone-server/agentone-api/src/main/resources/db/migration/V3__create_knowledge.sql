-- ============================================================
-- V3: 知识库 + 文档 + 文档分块（含向量）
-- ============================================================

-- 知识库表
CREATE TABLE knowledge_base (
    id              VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    VARCHAR(36)  NOT NULL REFERENCES workspace(id),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    doc_count       INT          NOT NULL DEFAULT 0,
    chunk_count     INT          NOT NULL DEFAULT 0,
    embedding_model VARCHAR(50)  NOT NULL DEFAULT 'text-embedding-3-small',
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_workspace ON knowledge_base(workspace_id);

-- 文档表
CREATE TABLE document (
    id           VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    knowledge_id VARCHAR(36)  NOT NULL REFERENCES knowledge_base(id),
    name         VARCHAR(200) NOT NULL,
    type         VARCHAR(20)  NOT NULL,  -- pdf / docx / md / txt / html / csv
    size         BIGINT       NOT NULL DEFAULT 0,
    chunk_count  INT          NOT NULL DEFAULT 0,
    status       VARCHAR(20)  NOT NULL DEFAULT 'pending',  -- pending / processing / ready / error
    file_path    VARCHAR(500),
    error_msg    TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_knowledge ON document(knowledge_id);

-- 文档分块表（含 PgVector 向量列）
CREATE TABLE document_chunk (
    id           VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id  VARCHAR(36)  NOT NULL REFERENCES document(id),
    content      TEXT         NOT NULL,
    chunk_index  INT          NOT NULL DEFAULT 0,
    metadata     JSONB        NOT NULL DEFAULT '{}',
    embedding    vector(1536)
);

CREATE INDEX idx_chunk_document ON document_chunk(document_id);

-- PgVector 向量索引（IVFFlat，余弦相似度）
-- 注意：需要至少 100 条数据后才能创建有效索引，这里先建索引定义
CREATE INDEX idx_chunk_embedding ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Agent-Knowledge 绑定表
CREATE TABLE agent_knowledge_binding (
    id                    VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id              VARCHAR(36)  NOT NULL REFERENCES agent(id),
    knowledge_id          VARCHAR(36)  NOT NULL REFERENCES knowledge_base(id),
    top_k                 INT          NOT NULL DEFAULT 5,
    similarity_threshold  FLOAT        NOT NULL DEFAULT 0.7,
    created_at            TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_kb_agent      ON agent_knowledge_binding(agent_id);
CREATE INDEX idx_agent_kb_knowledge  ON agent_knowledge_binding(knowledge_id);
