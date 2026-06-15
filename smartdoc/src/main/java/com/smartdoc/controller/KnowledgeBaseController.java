package com.smartdoc.controller;

import com.smartdoc.dto.Result;
import com.smartdoc.entity.KnowledgeBase;
import com.smartdoc.repository.KnowledgeBaseRepository;
import com.smartdoc.utils.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseRepository kbRepository;

    /**
     * 创建知识库
     */
    @PostMapping
    public Result<?> create(@RequestBody KbCreateRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setUserId(userId);
        return Result.success(kbRepository.save(kb));
    }

    /**
     * 获取当前用户的知识库列表
     */
    @GetMapping
    public Result<?> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<KnowledgeBase> list = kbRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return Result.success(list);
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        return kbRepository.findById(id)
                .map(Result::success)
                .orElse(Result.fail("知识库不存在"));
    }

    /**
     * 更新知识库信息
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody KbCreateRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        KnowledgeBase kb = kbRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) return Result.fail(403, "无权操作");
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        return Result.success(kbRepository.save(kb));
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        KnowledgeBase kb = kbRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) return Result.fail(403, "无权操作");
        kbRepository.deleteById(id);
        return Result.success("删除成功");
    }

    @Data
    static class KbCreateRequest {
        private String name;
        private String description;
    }
}
