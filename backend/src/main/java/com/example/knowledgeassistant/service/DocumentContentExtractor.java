package com.example.knowledgeassistant.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentContentExtractor {

    private final List<DocumentParser> parsers = List.of(
            new PlainTextDocumentParser(),
            new MarkdownDocumentParser(),
            new PdfDocumentParser(),
            new DocxDocumentParser()
    );

    public DocumentParserResult extract(String fileName, byte[] bytes) {
        if (!StringUtils.hasText(fileName)) {
            throw new DocumentParseException("文件名不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new DocumentParseException("上传文件为空，无法建立索引");
        }

        return parsers.stream()
                .filter(parser -> parser.supports(fileName))
                .findFirst()
                .map(parser -> new DocumentParserResult(parser.documentType(), parser.extractText(fileName, bytes)))
                .orElseThrow(() -> new DocumentParseException("仅支持 .txt、.md、.pdf、.docx 文档"));
    }

    private static String ensureHasText(String label, String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            throw new DocumentParseException(label + "未提取到可用文本");
        }
        return normalized;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "")
                .replace("\uFEFF", "")
                .trim();
    }

    private abstract static class BaseDocumentParser implements DocumentParser {

        protected boolean hasExtension(String fileName, String extension) {
            return fileName.toLowerCase(Locale.ROOT).endsWith(extension);
        }
    }

    private static class PlainTextDocumentParser extends BaseDocumentParser {

        @Override
        public boolean supports(String fileName) {
            return hasExtension(fileName, ".txt");
        }

        @Override
        public String documentType() {
            return "text";
        }

        @Override
        public String extractText(String fileName, byte[] bytes) {
            return ensureHasText("TXT 文档", new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static class MarkdownDocumentParser extends BaseDocumentParser {

        @Override
        public boolean supports(String fileName) {
            return hasExtension(fileName, ".md");
        }

        @Override
        public String documentType() {
            return "markdown";
        }

        @Override
        public String extractText(String fileName, byte[] bytes) {
            return ensureHasText("Markdown 文档", new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static class PdfDocumentParser extends BaseDocumentParser {

        @Override
        public boolean supports(String fileName) {
            return hasExtension(fileName, ".pdf");
        }

        @Override
        public String documentType() {
            return "pdf";
        }

        @Override
        public String extractText(String fileName, byte[] bytes) {
            try (PDDocument document = Loader.loadPDF(bytes)) {
                String text = new PDFTextStripper().getText(document);
                return ensureHasText("PDF 文档", text);
            } catch (IOException ex) {
                throw new DocumentParseException("无法解析 PDF 文档: " + rootMessage(ex), ex);
            }
        }
    }

    private static class DocxDocumentParser extends BaseDocumentParser {

        @Override
        public boolean supports(String fileName) {
            return hasExtension(fileName, ".docx");
        }

        @Override
        public String documentType() {
            return "docx";
        }

        @Override
        public String extractText(String fileName, byte[] bytes) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return ensureHasText("DOCX 文档", extractor.getText());
            } catch (IOException ex) {
                throw new DocumentParseException("无法解析 DOCX 文档: " + rootMessage(ex), ex);
            }
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
