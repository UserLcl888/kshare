package com.interview.service;

import com.interview.common.BizException;
import com.interview.common.ErrorCode;
import com.interview.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 图片自动处理：
 * 解析 md 中的图片引用，把 base64 图片、外链图片、后端本地 /images/ 图片
 * 自动上传到 MinIO，并把 URL 重写为 MinIO 地址；已是 MinIO 的 URL 跳过。
 * MinIO 未配置（accessKey 为空）时原样返回，不影响站点其它功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarkdownImageService {

    private static final Pattern IMG_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)(\\s+\"[^\"]*\")?\\)");
    private static final Pattern BASE64_PATTERN =
            Pattern.compile("^data:image/(png|jpe?g|gif|webp);base64,(.+)$", Pattern.DOTALL);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> ALLOWED_DIR =
            Set.of("article", "user-upload", "image", "cover", "banner", "avatar");
    private static final Map<String, String> CONTENT_TYPE = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");
    /** 压缩图统一格式与浏览器强缓存头（对象名带 UUID，内容不会被改写） */
    private static final String WEBP_CONTENT_TYPE = "image/webp";
    private static final String CACHE_IMMUTABLE = "public, max-age=31536000, immutable";
    /** 轮播图沿用固定文件名（可能被人工替换），不用一年强缓存 */
    private static final String CACHE_BANNER = "public, max-age=604800";

    private final MinioProperties props;
    private final ImageProcessService imageProcessService;

    @Autowired(required = false)
    private MinioClient minioClient;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 处理整篇 md：把图片 URL 全部重写为 MinIO 地址。 */
    public String processImages(String md, String dir) {
        if (!StringUtils.hasText(md) || minioClient == null || !StringUtils.hasText(props.getPublicBaseUrl())) {
            return md;
        }
        Matcher m = IMG_PATTERN.matcher(md);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String alt = m.group(1);
            String url = m.group(2);
            String newUrl = processOne(url, dir);
            m.appendReplacement(sb, Matcher.quoteReplacement("![" + alt + "](" + newUrl + ")"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String processOne(String url, String dir) {
        if (isMinioUrl(url)) {
            return url;
        }
        try {
            byte[] data;
            String ext;
            if (url.startsWith("data:image/")) {
                Matcher bm = BASE64_PATTERN.matcher(url);
                if (!bm.matches()) {
                    return url;
                }
                ext = normalizeExt(bm.group(1));
                data = Base64.getDecoder().decode(bm.group(2));
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                byte[] downloaded = download(url);
                if (downloaded == null) {
                    return url;
                }
                ext = extFromUrl(url);
                data = downloaded;
            } else if (url.startsWith("/images/")) {
                String name = url.substring("/images/".length());
                ClassPathResource res = new ClassPathResource("static/images/" + name);
                if (!res.exists()) {
                    return url;
                }
                ext = extFromUrl(name);
                data = res.getInputStream().readAllBytes();
            } else {
                // 相对路径等单文件上传无法解析,保留原样
                return url;
            }
            if (data.length == 0 || data.length > props.getImageMaxSize()) {
                return url;
            }
            return storeImage(data, ext, dir);
        } catch (Exception e) {
            log.warn("图片自动上传失败,保留原链接 url={}", url, e);
            return url;
        }
    }

    /**
     * 封面类图片的存储结果：大图（详情页）+ 缩略图（列表页）。
     * 两者地址都基于同一张原图派生，原图一并保留在对象存储里。
     */
    public record StoredCover(String url, String thumbUrl) {
    }

    /**
     * 单张图片上传（正文插图、头像、轮播图等复用）。未配置 MinIO 时抛业务异常。
     *
     * <p>原图按原名保存一份，同时按用途压出一张加宽 WebP 并返回它的地址：
     * 正文插图取 1600、头像取 256、轮播图取 1920。压缩不可用（gif / 编码失败）时回退原图地址。
     */
    public String storeImage(byte[] data, String ext, String dir) {
        String normDir = validate(data, ext, dir);
        String object = objectKey(normDir, normalizeExt(ext));
        try {
            // 原图留底：方便日后重新导出更大尺寸
            putObject(object, data, CONTENT_TYPE.getOrDefault(normalizeExt(ext), "application/octet-stream"));
            byte[] compressed = compress(data, normalizeExt(ext), widthFor(normDir));
            if (compressed == null) {
                return publicUrl(object);
            }
            String compressedKey = derivedKey(object, widthFor(normDir));
            putObject(compressedKey, compressed, WEBP_CONTENT_TYPE);
            return publicUrl(compressedKey);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "图片上传失败：" + e.getMessage());
        }
    }

    /**
     * 封面上传（文章封面 / 学习分类封面）：原图留底 + 大图（宽 1600）+ 缩略图（宽 800）。
     * 列表页用缩略图、详情页用大图，避免列表页一次性拉几十 MB 的原图。
     */
    public StoredCover storeCover(byte[] data, String ext) {
        String normDir = validate(data, ext, "cover");
        String normExt = normalizeExt(ext);
        String object = objectKey(normDir, normExt);
        try {
            putObject(object, data, CONTENT_TYPE.getOrDefault(normExt, "application/octet-stream"));

            byte[] large = compress(data, normExt, ImageProcessService.WIDTH_LARGE);
            if (large == null) {
                return new StoredCover(publicUrl(object), publicUrl(object));
            }
            String largeKey = derivedKey(object, ImageProcessService.WIDTH_LARGE);
            putObject(largeKey, large, WEBP_CONTENT_TYPE);

            byte[] thumb = compress(data, normExt, ImageProcessService.WIDTH_THUMB);
            if (thumb == null) {
                return new StoredCover(publicUrl(largeKey), publicUrl(largeKey));
            }
            String thumbKey = derivedKey(object, ImageProcessService.WIDTH_THUMB);
            putObject(thumbKey, thumb, WEBP_CONTENT_TYPE);
            return new StoredCover(publicUrl(largeKey), publicUrl(thumbKey));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "图片上传失败：" + e.getMessage());
        }
    }

    /** 校验上传参数，返回归一化后的目录名。 */
    private String validate(byte[] data, String ext, String dir) {
        if (minioClient == null || !StringUtils.hasText(props.getPublicBaseUrl())) {
            throw new BizException(ErrorCode.SERVER_ERROR, "图片存储未配置，请联系管理员");
        }
        String normExt = normalizeExt(ext);
        if (!ALLOWED_EXT.contains(normExt)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 png/jpg/gif/webp 图片");
        }
        if (data == null || data.length == 0 || data.length > props.getImageMaxSize()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能为空且不能超过 "
                    + (props.getImageMaxSize() / 1024 / 1024) + "MB");
        }
        String normDir = StringUtils.hasText(dir) ? dir.trim() : "image";
        return ALLOWED_DIR.contains(normDir) ? normDir : "image";
    }

    /** 压缩为 WebP；gif（可能带动画）或编码失败时返回 null。 */
    private byte[] compress(byte[] data, String normExt, int width) {
        if (!imageProcessService.compressible(normExt)) {
            return null;
        }
        return imageProcessService.toWebp(data, width);
    }

    /** 各用途的目标宽度。 */
    private int widthFor(String dir) {
        if ("avatar".equals(dir)) {
            return ImageProcessService.WIDTH_AVATAR;
        }
        if ("banner".equals(dir)) {
            return ImageProcessService.WIDTH_BANNER;
        }
        return ImageProcessService.WIDTH_LARGE;
    }

    /** 上传对象：图片统一带长缓存头，让浏览器和 CDN 能直接命中缓存。 */
    private void putObject(String object, byte[] data, String contentType) throws Exception {
        Map<String, String> headers = Map.of(
                "Content-Type", contentType,
                "Cache-Control", object.startsWith("banner/") ? CACHE_BANNER : CACHE_IMMUTABLE);
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(props.getBucket())
                .object(object)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType(contentType)
                .headers(headers)
                .build());
    }

    private String publicUrl(String object) {
        return props.getPublicBaseUrl() + "/" + props.getBucket() + "/" + object;
    }

    /** 当前是否已启用 MinIO 存储。 */
    public boolean enabled() {
        return minioClient != null && StringUtils.hasText(props.getPublicBaseUrl());
    }

    private boolean isMinioUrl(String url) {
        String prefix = props.getPublicBaseUrl() + "/" + props.getBucket() + "/";
        return url.startsWith(prefix);
    }

    /**
     * 删除某个 MinIO 对象（按完整 URL）。非 MinIO URL 或删除失败均忽略，供替换封面/头像等清理用。
     * 一张原图会派生出多个尺寸的 WebP，这里按基名一并清掉，避免留下孤儿对象。
     */
    public void removeObjectByUrl(String url) {
        if (!StringUtils.hasText(url) || !isMinioUrl(url) || minioClient == null) {
            return;
        }
        String prefix = props.getPublicBaseUrl() + "/" + props.getBucket() + "/";
        String object = url.substring(prefix.length());
        String base = baseKeyOf(object);
        List<String> keys = new ArrayList<>(List.of(
                object,
                base + ".w" + ImageProcessService.WIDTH_LARGE + ".webp",
                base + ".w" + ImageProcessService.WIDTH_THUMB + ".webp",
                base + ".w" + ImageProcessService.WIDTH_BANNER + ".webp",
                base + ".w" + ImageProcessService.WIDTH_AVATAR + ".webp"));
        for (String ext : List.of("png", "jpg", "jpeg", "webp", "gif")) {
            keys.add(base + "." + ext);
        }
        for (String key : keys) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(props.getBucket())
                        .object(key)
                        .build());
            } catch (Exception e) {
                log.warn("删除 MinIO 对象失败 key={}", key, e);
            }
        }
        log.info("已移除 MinIO 图片及其派生图: {}", base);
    }

    /** 派生对象名：cover/2026/08/31/xxx.png → cover/2026/08/31/xxx.w1600.webp */
    private String derivedKey(String object, int width) {
        return baseKeyOf(object) + ".w" + width + ".webp";
    }

    /** 去掉派生后缀与扩展名，得到对象基名（dir/yyyy/MM/dd/uuid）。 */
    private String baseKeyOf(String object) {
        return object.replaceAll("\\.w\\d+\\.webp$", "").replaceAll("\\.[A-Za-z0-9]+$", "");
    }

    private byte[] download(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible; InterviewBot/1.0)")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200 || resp.body() == null) {
                return null;
            }
            if (resp.body().length > props.getImageMaxSize()) {
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            log.debug("外链图片下载失败 url={}", url, e);
            return null;
        }
    }

    private String objectKey(String dir, String ext) {
        return dir + "/" + LocalDate.now().format(DAY) + "/" + UUID.randomUUID() + "." + ext;
    }

    private String extFromUrl(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            return normalizeExt(path.substring(dot + 1));
        }
        return "png";
    }

    private String normalizeExt(String ext) {
        if (ext == null) {
            return "png";
        }
        String e = ext.toLowerCase();
        if ("jpeg".equals(e)) {
            return "jpg";
        }
        return e;
    }
}
