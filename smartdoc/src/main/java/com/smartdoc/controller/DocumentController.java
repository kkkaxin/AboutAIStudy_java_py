package com.smartdoc.controller;

import com.smartdoc.dto.Result;
import com.smartdoc.entity.Document;
import com.smartdoc.service.DocumentService;
import com.smartdoc.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档到指定知识库
     * 支持 PDF、TXT、MD 格式
     */
    @PostMapping("/upload")
    public Result<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kbId", required = false, defaultValue = "1") Long kbId) {
        try {
            Long userId = getCurrentUserIdOrDefault();
            Document doc = documentService.uploadDocument(file, kbId, userId);
            return Result.success(doc);
        } catch (Exception e) {
            return Result.fail("上传失败：" + e.getMessage());
        }
    }

    /**
     * 获取知识库下的所有文档
     */
    @GetMapping
    public Result<?> listDocuments(@RequestParam(value = "kbId", required = false, defaultValue = "1") Long kbId) {
        List<Document> docs = documentService.listByKbId(kbId);
        return Result.success(docs);
    }

    /**
     * 删除文档（同时从向量库移除）
     */
    @DeleteMapping("/{docId}")
    public Result<?> deleteDocument(@PathVariable Long docId) {
        Long userId = getCurrentUserIdOrDefault();
        documentService.deleteDocument(docId, userId);
        return Result.success("删除成功");
    }

    /**
     * 获取当前用户ID，如果未登录则返回默认ID
     */
    private Long getCurrentUserIdOrDefault() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return 1L; // 默认用户ID
        }
    }
}
