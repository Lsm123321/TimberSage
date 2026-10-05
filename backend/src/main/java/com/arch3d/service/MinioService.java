package com.arch3d.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface MinioService {
    String uploadFile(MultipartFile file, String bucket, String objectName);

    String uploadModelFile(MultipartFile file, String objectName);

    String uploadThumbnail(MultipartFile file, String objectName);

    InputStream downloadFile(String bucket, String objectName);

    void deleteFile(String bucket, String objectName);

    String getFileUrl(String bucket, String objectName);

    String getModelFileUrl(String objectName);

    String getThumbnailUrl(String objectName);

    boolean bucketExists(String bucket);

    void createBucket(String bucket);

    void initializeBuckets();
}
