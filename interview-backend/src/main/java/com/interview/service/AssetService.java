package com.interview.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.common.BizException;
import com.interview.common.ErrorCode;
import com.interview.common.PageResult;
import com.interview.entity.Asset;
import com.interview.enums.AdminLogAction;
import com.interview.mapper.AssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 附件管理：把 HTML 页面 / 文档原样传到 MinIO，并把访问链接记录下来，
 * 管理员复制这个链接贴进 Markdown 正文即可（正文仍是普通的 MD 渲染，
 * 外链由前端统一按新标签页打开，后端不做任何特殊处理）。
 *
 * <p>上传时唯一需要「处理」的是 HTTP 头：Content-Type 必须设对，
 * 否则 HTML 会变成下载而不是渲染；Office/zip 这类浏览器不能内嵌的文件
 * 设成 attachment，点下载时不会跳走页面、中文文件名也能还原。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetService {

    /** HTML 页面单文件上限 */
    private static final long HTML_MAX_SIZE = 5 * 1024 * 1024L;
    /** 文档单文件上限（需与 spring.servlet.multipart.max-file-size、Nginx client_max_body_size 一致） */
    private static final long FILE_MAX_SIZE = 50 * 1024 * 1024L;
    private static final Set<String> HTML_EXT = Set.of("html", "htm");
    /**
     * 文档白名单。刻意不含 svg（能带脚本的 XML，直接打开等于开 XSS 口子）、
     * html（走 HTML 页面那类）、docm/xlsm 等带宏格式。
     */
    private static final Set<String> FILE_EXT =
            Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip");
    /** 浏览器能原生内嵌渲染、不需要强制下载的类型 */
    private static final Set<String> INLINE_EXT = Set.of("pdf", "txt", "csv");
    private static final Map<String, String> FILE_CONTENT_TYPE = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("zip", "application/zip"));
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final AssetMapper assetMapper;
    private final MarkdownImageService markdownImageService;
    private final AdminLogService adminLogService;

    /** 后台列表：按上传时间倒序，可按来源类型和文件名筛选。 */
    public PageResult<Asset> adminList(String keyword, String kind, long page, long size) {
        LambdaQueryWrapper<Asset> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(kind)) {
            qw.eq(Asset::getKind, kind.trim());
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(Asset::getFileName, keyword.trim());
        }
        qw.orderByDesc(Asset::getId);
        Page<Asset> result = assetMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    /** 上传附件并落记录，返回带链接的完整信息供后台直接展示/复制。 */
    @Transactional
    public Asset upload(MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
        boolean isHtml = "html".equalsIgnoreCase(kind);
        String original = originalName(file);
        String ext = extOf(original);

        if (isHtml) {
            if (!HTML_EXT.contains(ext)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "HTML 页面仅支持 .html/.htm 文件");
            }
            if (file.getSize() > HTML_MAX_SIZE) {
                throw new BizException(ErrorCode.PARAM_ERROR, "HTML 文件不能超过 5MB");
            }
        } else {
            if (!FILE_EXT.contains(ext)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 pdf/word/excel/ppt/txt/csv/zip 格式");
            }
            if (file.getSize() > FILE_MAX_SIZE) {
                throw new BizException(ErrorCode.PARAM_ERROR, "文件不能超过 50MB");
            }
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "文件读取失败，请重试");
        }
        if (isHtml && looksBinary(data)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件内容不是文本，请确认是 HTML 文件");
        }

        String dir = isHtml ? "html" : "file";
        String objectName = dir + "/" + LocalDate.now().format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String contentType = isHtml
                ? "text/html; charset=utf-8"
                : FILE_CONTENT_TYPE.getOrDefault(ext, "application/octet-stream");
        String disposition = isHtml || INLINE_EXT.contains(ext) ? null : attachmentDisposition(original);

        String url = markdownImageService.storeAsset(data, objectName, contentType, disposition);

        Asset asset = new Asset();
        asset.setKind(isHtml ? "html" : "file");
        asset.setObjectName(objectName);
        asset.setUrl(url);
        asset.setFileName(original);
        asset.setFileSize(file.getSize());
        asset.setCreatedBy(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        assetMapper.insert(asset);
        adminLogService.write(AdminLogAction.ASSET_UPLOAD, asset.getId(), "上传附件：" + original);
        return assetMapper.selectById(asset.getId());
    }

    /** 删除附件：先删对象存储里的文件，再删记录。 */
    @Transactional
    public void delete(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在或已被删除");
        }
        markdownImageService.removeAssetByUrl(asset.getUrl());
        assetMapper.deleteById(id);
        adminLogService.write(AdminLogAction.ASSET_DELETE, id, "删除附件：" + asset.getFileName());
    }

    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        // 去掉客户端可能带上的路径，只保留文件名本身
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    /** 粗略识别二进制：文本文件里不应该出现 NUL 字节。 */
    private static boolean looksBinary(byte[] data) {
        int limit = Math.min(data.length, 4096);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** 非内嵌类型一律带 attachment，中文文件名通过 RFC 5987 的 filename* 还原。 */
    private static String attachmentDisposition(String fileName) {
        String ascii = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
