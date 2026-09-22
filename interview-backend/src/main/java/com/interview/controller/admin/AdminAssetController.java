package com.interview.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.interview.common.PageResult;
import com.interview.common.Result;
import com.interview.entity.Asset;
import com.interview.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台附件管理：上传 HTML 页面 / 文档，拿到链接后复制到 Markdown 正文里使用。
 */
@RestController
@RequestMapping("/api/admin/assets")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminAssetController {

    private final AssetService assetService;

    @GetMapping
    public Result<PageResult<Asset>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.ok(assetService.adminList(keyword, kind, page, size));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Asset> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "kind", defaultValue = "file") String kind) {
        return Result.ok(assetService.upload(file, kind));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        assetService.delete(id);
        return Result.ok();
    }
}
