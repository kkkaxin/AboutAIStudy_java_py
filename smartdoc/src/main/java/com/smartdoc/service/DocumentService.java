package com.smartdoc.service;

import com.smartdoc.entity.Document;
import com.smartdoc.entity.KnowledgeBase;
import com.smartdoc.repository.DocumentRepository;
import com.smartdoc.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository kbRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    /**
     * 上传并处理文档（同步版本）
     */
    public Document uploadDocument(MultipartFile file, Long kbId, Long userId) throws Exception {
        // 1. 校验或创建知识库
        KnowledgeBase kb = kbRepository.findById(kbId).orElse(null);
        if (kb == null) {
            // 如果知识库不存在，自动创建
            kb = new KnowledgeBase();
            kb.setId(kbId);
            kb.setUserId(userId);
            kb.setName("默认知识库");
            kb.setDescription("系统默认知识库");
            kb.setDocCount(0);
            kbRepository.save(kb);
            log.info("自动创建知识库: id={}, name=默认知识库", kbId);
        } else if (!kb.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此知识库");
        }

        // 2. 保存文件到磁盘
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);
        String savedFileName = UUID.randomUUID() + "." + fileType;
        
        // 确保上传根目录存在
        Path uploadRootPath = Paths.get(uploadDir);
        if (!Files.exists(uploadRootPath)) {
            Files.createDirectories(uploadRootPath);
            log.info("创建上传目录: {}", uploadRootPath.toAbsolutePath());
        }
        
        Path savedPath = Paths.get(uploadDir, String.valueOf(kbId), savedFileName);
        Files.createDirectories(savedPath.getParent());
        file.transferTo(savedPath.toFile());

        // 3. 创建数据库记录
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setFileName(originalFilename);
        doc.setFilePath(savedPath.toString());
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setStatus("PROCESSING");
        documentRepository.save(doc);

        // 4. 同步处理文档（解析 + Embedding + 存入向量库）
        processDocumentSync(doc);

        return doc;
    }

    /**
     * 异步：文档解析 → 分块 → Embedding → 存入 VectorStore
     */
    @Async
    public void processDocumentAsync(Document doc) {
        processDocumentSync(doc);
    }

    /**
     * 同步处理文档
     */
    public void processDocumentSync(Document doc) {
        try {
            log.info("开始处理文档：{}", doc.getFileName());

            // 读取文档内容
            List<org.springframework.ai.document.Document> documents = readDocument(doc);

            // 文本分块（Spring AI 1.0.0 GA 使用 Builder 模式）
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(chunkSize)
                    .withMinChunkSizeChars(chunkOverlap)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(10000)
                    .withKeepSeparator(true)
                    .build();
            List<org.springframework.ai.document.Document> chunks = splitter.apply(documents);

            // 为每个 chunk 添加元数据（便于后续检索时溯源）
            for (org.springframework.ai.document.Document chunk : chunks) {
                chunk.getMetadata().put("doc_id", String.valueOf(doc.getId()));
                chunk.getMetadata().put("kb_id", String.valueOf(doc.getKbId()));
                chunk.getMetadata().put("file_name", doc.getFileName());
            }

            // 存入向量库（Spring AI 自动调用 Embedding 模型生成向量）
            vectorStore.add(chunks);

            // 更新状态
            doc.setChunkCount(chunks.size());
            doc.setStatus("DONE");
            documentRepository.save(doc);

            // 更新知识库文档计数
            kbRepository.incrementDocCount(doc.getKbId());

            log.info("文档处理完成：{}，共 {} 个文本块", doc.getFileName(), chunks.size());
        } catch (Exception e) {
            log.error("文档处理失败：{}", doc.getFileName(), e);
            doc.setStatus("FAILED");
            documentRepository.save(doc);
            throw new RuntimeException("文档处理失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据文件类型选择对应的文档读取器
     */
    private List<org.springframework.ai.document.Document> readDocument(Document doc) {
        FileSystemResource resource = new FileSystemResource(new File(doc.getFilePath()));
        DocumentReader reader;

        if ("pdf".equalsIgnoreCase(doc.getFileType())) {
            // PDF 专用读取器，保留文本格式
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                            .withNumberOfBottomTextLinesToDelete(3)
                            .withNumberOfTopPagesToSkipBeforeDelete(1)
                            .build())
                    .withPagesPerDocument(1)
                    .build();
            reader = new PagePdfDocumentReader(resource, config);
        } else {
            // TXT、MD、DOCX 等通用读取器
            reader = new TikaDocumentReader(resource);
        }

        return reader.read();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "txt";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    public List<Document> listByKbId(Long kbId) {
        return documentRepository.findByKbIdOrderByCreatedAtDesc(kbId);
    }

    public void deleteDocument(Long docId, Long userId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        // TODO: 从向量库中删除对应 chunk
        documentRepository.deleteById(docId);
        kbRepository.decrementDocCount(doc.getKbId());
    }
}
