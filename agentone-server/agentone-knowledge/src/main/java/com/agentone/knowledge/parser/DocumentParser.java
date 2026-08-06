package com.agentone.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

/**
 * 文档解析器（基于 Apache Tika）
 * 支持格式：pdf / docx / md / txt / html / csv
 */
@Slf4j
@Component
public class DocumentParser {

    private final AutoDetectParser parser = new AutoDetectParser();

    /**
     * 跳过所有嵌入资源（图片、缩略图、OLE 对象等）。
     *
     * 背景：部分 docx 的 docProps/thumbnail.wmf 预览图内嵌 GBK 编码的正文文本，
     * Tika 默认会递归解析这些嵌入资源，并把图里的 GBK 字节按 ISO-8859-1 误读成
     * 乱码（如 "Òª½â¾ö"）混入正文，导致分块和检索结果出现乱码。
     * 知识库只需索引文档正文，嵌入图片/对象的文本一律忽略。
     */
    private static final EmbeddedDocumentExtractor SKIP_EMBEDDED = new EmbeddedDocumentExtractor() {
        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return false;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler,
                                  Metadata metadata, boolean outputHtml) {
            // 不解析嵌入资源
        }
    };

    /**
     * 解析文档为纯文本
     *
     * @param inputStream 文档输入流
     * @param fileType    文件类型（pdf/docx/md/txt/html/csv）
     * @return 纯文本内容
     */
    public String parse(InputStream inputStream, String fileType) {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);
            context.set(EmbeddedDocumentExtractor.class, SKIP_EMBEDDED);

            parser.parse(inputStream, handler, metadata, context);
            String text = handler.toString();

            if (text == null || text.isBlank()) {
                log.warn("文档解析结果为空, fileType={}", fileType);
                return "";
            }
            // 清理多余空白
            return text.trim().replaceAll("\\r\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        } catch (Exception e) {
            log.error("文档解析失败: fileType={}, error={}", fileType, e.getMessage());
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }
}
