package com.arch3d.service.impl;

import com.arch3d.service.MinioService;
import io.minio.*;
import io.minio.http.Method;

import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.models:models}")
    private String modelsBucket;

    @Value("${minio.bucket.thumbnails:thumbnails}")
    private String thumbnailsBucket;

    @Value("${minio.url:http://localhost:9000}")
    private String minioUrl;

    /**
     * 对外可访问的 MinIO 地址（用于给浏览器返回可用的预签名 URL）。
     * 例如本机 Docker Compose：http://localhost:19100
     */
    @Value("${spring.minio.public-endpoint:}")
    private String publicEndpoint;

    @Override
    @PostConstruct
    public void initializeBuckets() {
        try {
            // 先探一下 MinIO 通不通，不通就跳过初始化，别把整个应用卡死在启动阶段
            minioClient.listBuckets();
            log.info("MinIO连接正常，开始初始化存储桶");
            
            if (!bucketExists(modelsBucket)) {
                createBucket(modelsBucket);
                log.info("存储桶 {} 创建成功", modelsBucket);
            } else {
                log.debug("存储桶 {} 已存在", modelsBucket);
            }
            ensureBucketPublicRead(modelsBucket);
            
            if (!bucketExists(thumbnailsBucket)) {
                createBucket(thumbnailsBucket);
                log.info("存储桶 {} 创建成功", thumbnailsBucket);
            } else {
                log.debug("存储桶 {} 已存在", thumbnailsBucket);
            }
            ensureBucketPublicRead(thumbnailsBucket);
            
            log.info("MinIO存储桶初始化完成");
        } catch (Exception e) {
            log.warn("MinIO服务不可用，跳过存储桶初始化。如果需要文件上传功能，请启动MinIO服务。错误信息: {}", e.getMessage());
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String bucket, String objectName) {
        try {
            InputStream inputStream = file.getInputStream();
            String contentType = file.getContentType();

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build()
            );

            log.info("File uploaded successfully: {}/{}", bucket, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("Failed to upload file: {}/{}", bucket, objectName, e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    @Override
    public String uploadModelFile(MultipartFile file, String objectName) {
        return uploadFile(file, modelsBucket, objectName);
    }

    @Override
    public String uploadThumbnail(MultipartFile file, String objectName) {
        return uploadFile(file, thumbnailsBucket, objectName);
    }

    @Override
    public InputStream downloadFile(String bucket, String objectName) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to download file: {}/{}", bucket, objectName, e);
            throw new RuntimeException("Failed to download file", e);
        }
    }

    @Override
    public void deleteFile(String bucket, String objectName) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build()
            );
            log.info("File deleted successfully: {}/{}", bucket, objectName);
        } catch (Exception e) {
            log.error("Failed to delete file: {}/{}", bucket, objectName, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    @Override
    public String getFileUrl(String bucket, String objectName) {
        // 桶是公开读的，直接拼 URL。不走预签名是因为改写 host 会导致签名对不上，访问直接 403
        return buildPublicObjectUrl(bucket, objectName);
    }

    private String rewriteToPublicEndpoint(String presignedUrl) {
        if (presignedUrl == null || presignedUrl.isBlank()) {
            return presignedUrl;
        }
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            return presignedUrl;
        }
        try {
            URI src = URI.create(presignedUrl);
            URI pub = URI.create(publicEndpoint);

            // 只换 scheme/host/port，路径和签名参数原样保留
            URI rewritten = new URI(
                pub.getScheme() != null ? pub.getScheme() : src.getScheme(),
                null,
                pub.getHost(),
                pub.getPort(),
                src.getPath(),
                src.getQuery(),
                src.getFragment()
            );
            return rewritten.toString();
        } catch (Exception e) {
            // 改写失败就退回原 URL，别把请求直接搞挂
            log.warn("Failed to rewrite presigned url to public endpoint. publicEndpoint={}, url={}", publicEndpoint, presignedUrl, e);
            return presignedUrl;
        }
    }

    @Override
    public String getModelFileUrl(String objectName) {
        return getFileUrl(modelsBucket, objectName);
    }

    @Override
    public String getThumbnailUrl(String objectName) {
        return getFileUrl(thumbnailsBucket, objectName);
    }

    @Override
    public boolean bucketExists(String bucket) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.error("Failed to check bucket exists: {}", bucket, e);
            return false;
        }
    }

    @Override
    public void createBucket(String bucket) {
        try {
            minioClient.makeBucket(
                MakeBucketArgs.builder().bucket(bucket).build()
            );
            log.info("Bucket created successfully: {}", bucket);
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", bucket, e);
            throw new RuntimeException("Failed to create bucket", e);
        }
    }

    private void ensureBucketPublicRead(String bucket) {
        try {
            // 桶策略设成公开只读，浏览器不加签名就能直接访问文件
            String policy = "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": {\"AWS\": [\"*\"]},\n" +
                "      \"Action\": [\"s3:GetObject\"],\n" +
                "      \"Resource\": [\"arn:aws:s3:::" + bucket + "/*\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

            minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(policy)
                    .build()
            );
            log.info("Bucket {} policy set to public-read", bucket);
        } catch (Exception e) {
            log.warn("Failed to set public-read policy for bucket {}: {}", bucket, e.getMessage());
        }
    }

    // 对象名转 URL 时除了空格（+ 号在 URL 里会被当成空格意外转义）
    private String buildPublicObjectUrl(String bucket, String objectName) {
        String base = (publicEndpoint != null && !publicEndpoint.isBlank()) ? publicEndpoint : minioUrl;
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedObject = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        String encodedObject = URLEncoder.encode(normalizedObject, StandardCharsets.UTF_8).replace("+", "%20");
        return normalizedBase + "/" + bucket + "/" + encodedObject;
    }

    public String generateUniqueFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }
}
