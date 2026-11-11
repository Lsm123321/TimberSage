package com.arch3d.service.impl;

import com.arch3d.service.AvatarService;
import com.arch3d.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 头像上传：校验 → 等比缩放 → 转 JPEG 压缩 → 存 MinIO。
 * 头像最大只在前端显示到 180px 左右，原图动辄几 MB 直接存会拖慢列表页，
 * 所以统一压到 512px 以内再入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    private final MinioService minioService;

    /** 允许的图片类型。SVG 故意不放：它能内嵌脚本，当 img src 用有 XSS 风险 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif", "image/bmp"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final int MAX_EDGE = 512;
    private static final float JPEG_QUALITY = 0.88f;

    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的头像图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("头像图片不能超过 5MB");
        }

        String contentType = file.getContentType() == null
            ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、WebP、GIF、BMP 格式的图片");
        }

        byte[] processed = processImage(file);
        String objectName = UUID.randomUUID().toString().replace("-", "") + ".jpg";

        // MultipartFile 已被读出一次，这里包一层新的给 MinIO 用
        MultipartFile upload = new InMemoryMultipartFile(objectName, objectName, "image/jpeg", processed);
        minioService.uploadAvatar(upload, objectName);

        // 返回相对路径：dev 走 Vite 代理、生产走 Nginx 代理，都不受部署域名影响
        String url = "/minio/avatars/" + objectName;
        log.info("头像上传成功: {}", url);
        return url;
    }

    @Override
    public void deleteAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        String prefix = "/minio/avatars/";
        if (!avatarUrl.startsWith(prefix)) {
            // 用户填的是外链或旧数据，不属于我们管的对象，跳过
            return;
        }
        String objectName = avatarUrl.substring(prefix.length());
        if (objectName.isBlank() || objectName.contains("/")) {
            return;
        }
        try {
            minioService.deleteFile("avatars", objectName);
            log.info("旧头像已删除: {}", objectName);
        } catch (Exception e) {
            // 删旧图失败不该让「换头像」整体失败，留个日志就行
            log.warn("旧头像删除失败: {}", objectName, e);
        }
    }

    /**
     * 读图 → 等比缩到 512px 内 → 输出 JPEG。
     * GIF 会丢动画，但头像场景用不到动图，统一成 JPEG 更好控制体积。
     */
    private byte[] processImage(MultipartFile file) {
        BufferedImage source;
        try (InputStream in = file.getInputStream()) {
            source = ImageIO.read(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("图片读取失败，请换一张图片试试");
        }
        if (source == null) {
            throw new IllegalArgumentException("无法识别该图片格式，请换一张图片试试");
        }

        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(width, height));
        int targetW = Math.max(1, (int) Math.round(width * scale));
        int targetH = Math.max(1, (int) Math.round(height * scale));

        // 透明背景铺白，否则 PNG/GIF 转 JPEG 后透明区会变成黑块
        BufferedImage canvas = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, targetW, targetH);
            g.drawImage(source, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }

        return writeJpeg(canvas);
    }

    private byte[] writeJpeg(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前环境不支持 JPEG 编码");
        }
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("图片压缩失败", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** MinIO 只认 MultipartFile，处理后的字节包一层内存实现，免得再落临时文件 */
    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
