package com.arch3d.service.impl;

import com.arch3d.dto.DownloadUrlResponse;
import com.arch3d.entity.BuildingModel;
import com.arch3d.entity.DownloadLog;
import com.arch3d.entity.User;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.repository.DownloadLogRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.service.DownloadService;
import com.arch3d.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadServiceImpl implements DownloadService {

    private final BuildingModelRepository modelRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String DOWNLOAD_COUNT_PREFIX = "download:count:";
    private static final Duration DOWNLOAD_COUNT_EXPIRE = Duration.ofDays(7);
    private static final long DOWNLOAD_URL_EXPIRE = 300; // 5分钟

    @Override
    public DownloadUrlResponse getDownloadUrl(Integer modelId, Integer userId, String clientIp) {
        DownloadUrlResponse response = getModelFileUrl(modelId, userId, clientIp);
        recordDownload(modelId, userId, clientIp);
        return response;
    }

    @Override
    public DownloadUrlResponse getModelFileUrl(Integer modelId, Integer userId, String clientIp) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        boolean isAdmin = false;
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            isAdmin = user != null && "admin".equalsIgnoreCase(user.getRole());
        }

        // 没发布的模型，管理员和创建者之外的人不能下载
        if (model.getStatus() == null || model.getStatus() != 1) {
            boolean allowed = false;
            if (userId != null) {
                if (model.getCreatedBy() != null && model.getCreatedBy().getId().equals(userId)) {
                    allowed = true;
                } else {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null && "admin".equalsIgnoreCase(user.getRole())) {
                        allowed = true;
                    }
                }
            }
            if (!allowed) {
                throw new RuntimeException("模型未发布或正在审核中，无法下载");
            }
        }

        // 管理员后台审核/预览用，绕过下载限制
        if (!isAdmin) {
            checkDownloadPermission(model, userId);
        }

        String downloadUrl = minioService.getModelFileUrl(model.getModelFilePath());

        return DownloadUrlResponse.builder()
            .downloadUrl(downloadUrl)
            .fileName(getFileNameFromPath(model.getModelFilePath()))
            .expireTime(DOWNLOAD_URL_EXPIRE)
            .modelName(model.getModelName())
            .dynasty(model.getDynasty())
            .build();
    }

    @Override
    public String getPublicViewFileUrl(Integer modelId) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new IllegalArgumentException("模型不存在"));

        if (model.getStatus() == null || model.getStatus() != 1) {
            throw new IllegalStateException("模型未发布或不可预览");
        }

        int permission = model.getDownloadPermission();
        if (permission == 0) {
            throw new IllegalStateException("该模型不允许访问文件");
        }
        if (permission == 1) {
            throw new IllegalStateException("需要登录后查看模型");
        }

        return minioService.getModelFileUrl(model.getModelFilePath());
    }

    @Override
    @Async
    @Transactional
    public void recordDownload(Integer modelId, Integer userId, String clientIp) {
        try {
            // 统计三件套：下载日志、模型下载计数、用户维度计数（Redis，7 天过期）。
            // 异步执行，下载本身不用等这些落库
            DownloadLog downloadLog = DownloadLog.builder()
                .modelId(modelId)
                .userId(userId)
                .ipAddress(clientIp)
                .downloadTime(LocalDateTime.now())
                .build();
            downloadLogRepository.save(downloadLog);

            modelRepository.incrementDownloadCount(modelId);

            if (userId != null) {
                String downloadCountKey = DOWNLOAD_COUNT_PREFIX + userId + ":" + modelId;
                redisTemplate.opsForValue().increment(downloadCountKey);
                redisTemplate.expire(downloadCountKey, DOWNLOAD_COUNT_EXPIRE);
            }

            log.info("Download recorded: modelId={}, userId={}, ip={}", modelId, userId, clientIp);
        } catch (Exception e) {
            log.error("Failed to record download: modelId={}, userId={}", modelId, userId, e);
        }
    }

    @Override
    public int getUserDownloadCount(Integer userId, Integer modelId) {
        if (userId == null) {
            return 0;
        }

        String downloadCountKey = DOWNLOAD_COUNT_PREFIX + userId + ":" + modelId;
        Object count = redisTemplate.opsForValue().get(downloadCountKey);
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    private void checkDownloadPermission(BuildingModel model, Integer userId) {
        // downloadPermission：0 禁止下载 / 1 要登录 / 2 所有人
        int permission = model.getDownloadPermission();

        if (permission == 0) {
            throw new RuntimeException("该模型禁止下载");
        }

        if (permission == 1 && userId == null) {
            throw new RuntimeException("需要登录才能下载该模型");
        }
    }

    private String getFileNameFromPath(String filePath) {
        if (filePath == null) {
            return "model.glb";
        }
        int lastSlashIndex = filePath.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return filePath;
        }
        return filePath.substring(lastSlashIndex + 1);
    }
}
