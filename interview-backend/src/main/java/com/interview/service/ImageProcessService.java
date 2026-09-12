package com.interview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 图片压缩：把上传的原图缩放后编码为 WebP。
 *
 * <p>站点图片分两类使用场景，尺寸参数按「页面上实际显示多大」反推：
 * <ul>
 *   <li>列表/卡片缩略图：卡片封面实际约 196px 宽，取 {@link #WIDTH_THUMB}=800，高清屏下也够清晰；</li>
 *   <li>详情页大图 / 正文插图：正文栏最宽约 920px，取 {@link #WIDTH_LARGE}=1600。</li>
 * </ul>
 * 质量统一 0.85：肉眼与原图基本无差别，但截图类内容的文字不会发虚。
 * 只缩不放——原图比目标宽度小就保持原尺寸，避免放大糊掉。
 */
@Slf4j
@Service
public class ImageProcessService {

    /** 详情页封面 / 正文插图的最大宽度 */
    public static final int WIDTH_LARGE = 1600;
    /** 列表卡片封面的最大宽度 */
    public static final int WIDTH_THUMB = 800;
    /** 首页轮播图最大宽度（整屏宽图） */
    public static final int WIDTH_BANNER = 1920;
    /** 头像最大宽度 */
    public static final int WIDTH_AVATAR = 256;

    private static final float QUALITY = 0.85f;
    private static final String WEBP_MIME = "image/webp";

    /** gif 可能是动图，转 WebP 会丢动画，因此不压缩，保持原图。 */
    public boolean compressible(String ext) {
        if (ext == null) {
            return false;
        }
        String e = ext.toLowerCase();
        return "png".equals(e) || "jpg".equals(e) || "jpeg".equals(e)
                || "webp".equals(e) || "bmp".equals(e);
    }

    /**
     * 缩放并编码为 WebP。
     *
     * @return WebP 字节；读取失败、无 WebP 编码器或编码异常时返回 null，由调用方回退原图
     */
    public byte[] toWebp(byte[] source, int maxWidth) {
        if (source == null || source.length == 0) {
            return null;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(source));
            if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
                return null;
            }
            BufferedImage scaled = scaleToWidth(src, Math.min(maxWidth, src.getWidth()));
            return writeWebp(scaled);
        } catch (Exception e) {
            log.warn("图片转 WebP 失败，保留原图：{}", e.getMessage());
            return null;
        }
    }

    /** 按目标宽度等比缩放；分步折半下采样，比一步缩到底更锐利。 */
    private BufferedImage scaleToWidth(BufferedImage src, int targetWidth) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        BufferedImage current = toDrawable(src);
        if (targetWidth >= srcW) {
            return current;
        }
        int targetHeight = Math.max(1, (int) Math.round(srcH * (targetWidth / (double) srcW)));
        int w = srcW;
        int h = srcH;
        while (w > targetWidth * 2 && h > targetHeight * 2) {
            w = Math.max(targetWidth, w / 2);
            h = Math.max(targetHeight, h / 2);
            current = scaleOnce(current, w, h);
        }
        return scaleOnce(current, targetWidth, targetHeight);
    }

    private BufferedImage scaleOnce(BufferedImage src, int width, int height) {
        BufferedImage dst = new BufferedImage(width, height, imageType(src));
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return dst;
    }

    /** 统一成 Graphics2D 可高效绘制的类型，避免索引色/自定义 ColorModel 出错。 */
    private BufferedImage toDrawable(BufferedImage src) {
        int type = imageType(src);
        if (src.getType() == type) {
            return src;
        }
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), type);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private int imageType(BufferedImage src) {
        return src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    private byte[] writeWebp(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(WEBP_MIME);
        if (!writers.hasNext()) {
            log.warn("当前环境没有 WebP 编码器，图片将保持原始格式");
            return null;
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            try {
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    // WebP 的 ImageWriteParam 要求先指定压缩类型，才能设置质量
                    String[] types = param.getCompressionTypes();
                    if (types != null && types.length > 0) {
                        param.setCompressionType(types[0]);
                        param.setCompressionQuality(QUALITY);
                    }
                }
            } catch (Exception ignored) {
                // 该编码器不支持显式质量参数时用默认值
            }
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
