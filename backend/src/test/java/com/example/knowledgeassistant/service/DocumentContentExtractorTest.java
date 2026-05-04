package com.example.knowledgeassistant.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentContentExtractorTest {

    private final DocumentContentExtractor extractor = new DocumentContentExtractor();

    @Test
    void extractsMarkdownText() {
        DocumentParserResult result = extractor.extract("policy.md", "# 标题\n\n退款规则在此。".getBytes(StandardCharsets.UTF_8));

        assertThat(result.documentType()).isEqualTo("markdown");
        assertThat(result.text()).contains("退款规则");
    }

    @Test
    void extractsPdfText() throws IOException {
        DocumentParserResult result = extractor.extract("guide.pdf", createPdf("pgvector extension is required before vector search"));

        assertThat(result.documentType()).isEqualTo("pdf");
        assertThat(result.text()).contains("pgvector");
    }

    @Test
    void extractsDocxText() throws IOException {
        DocumentParserResult result = extractor.extract("faq.docx", createDocx("Oracle 和 Elasticsearch 走定制化接入评估流程"));

        assertThat(result.documentType()).isEqualTo("docx");
        assertThat(result.text()).contains("定制化接入评估流程");
    }

    @Test
    void rejectsUnsupportedFileType() {
        assertThatThrownBy(() -> extractor.extract("sheet.xlsx", new byte[]{1, 2, 3}))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("仅支持 .txt、.md、.pdf、.docx");
    }

    @Test
    void reportsPdfParseFailure() {
        assertThatThrownBy(() -> extractor.extract("broken.pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("无法解析 PDF 文档");
    }

    private byte[] createPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(60, 700);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createDocx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }
}
