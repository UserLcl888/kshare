package com.interview.controller;

import com.interview.common.PageResult;
import com.interview.common.Result;
import com.interview.dto.Requests;
import com.interview.dto.VOs;
import com.interview.service.AuthService;
import com.interview.service.MarkdownService;
import com.interview.service.UserUploadService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user/uploads")
@RequiredArgsConstructor
public class UserUploadController {

    private final UserUploadService userUploadService;
    private final AuthService authService;
    private final MarkdownService markdownService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<VOs.UserUploadListItemVO> create(
            @RequestParam("title") String title,
            @RequestParam(value = "categoryName", required = false) String categoryName,
            @RequestParam(value = "groupName", required = false) String groupName,
            @RequestPart("file") MultipartFile file) {
        Requests.UserUploadSaveDTO dto = new Requests.UserUploadSaveDTO();
        dto.setTitle(title);
        dto.setCategoryName(categoryName);
        dto.setGroupName(groupName);
        return Result.ok(userUploadService.create(authService.currentUser().getId(), dto, file));
    }

    @GetMapping
    public Result<PageResult<VOs.UserUploadListItemVO>> myList(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.ok(userUploadService.myList(authService.currentUser().getId(), page, size));
    }

    /** 今日投稿额度：{limit, used, remaining}，供前端做精细提示。 */
    @GetMapping("/quota")
    public Result<java.util.Map<String, Integer>> quota() {
        return Result.ok(userUploadService.dailyQuota(authService.currentUser().getId()));
    }

    /**
     * 投稿前的 Markdown 预览：与正文同一套解析 + 消毒规则，保证预览与发布后效果一致。
     * 只渲染不落库（图片不搬运，仍是原文里的地址）。
     */
    @PostMapping("/preview")
    public Result<VOs.MarkdownPreviewVO> preview(@Valid @RequestBody Requests.MarkdownPreviewDTO dto) {
        return Result.ok(markdownService.preview(dto.getContentMd()));
    }

    @GetMapping("/{id}")
    public Result<VOs.UserUploadDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(userUploadService.myDetail(authService.currentUser().getId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        userUploadService.deleteByUser(authService.currentUser().getId(), id);
        return Result.ok();
    }
}
