package com.arch3d.service;

import com.arch3d.dto.DownloadUrlResponse;

public interface DownloadService {
    DownloadUrlResponse getDownloadUrl(Integer modelId, Integer userId, String clientIp);

    /**
     * 已登录用户在线预览/管理端加载 GLB 用的文件地址，权限校验与 {@link #getDownloadUrl} 相同，但不写入下载统计。
     */
    DownloadUrlResponse getModelFileUrl(Integer modelId, Integer userId, String clientIp);

    /**
     * 匿名场景下的在线预览地址（仅已发布且「允许所有人下载」的模型）。
     */
    String getPublicViewFileUrl(Integer modelId);

    void recordDownload(Integer modelId, Integer userId, String clientIp);
    int getUserDownloadCount(Integer userId, Integer modelId);
}
