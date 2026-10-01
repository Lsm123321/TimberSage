package com.arch3d.config;

import com.arch3d.entity.BuildingModel;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RejectedModelCleanupTask {

    private final BuildingModelRepository modelRepository;
    private final MinioService minioService;

    @Value("${minio.bucket.models:models}")
    private String modelsBucket;

    @Value("${minio.bucket.thumbnails:thumbnails}")
    private String thumbnailsBucket;

    /** 被驳回的模型文件保留几天，默认 7 天 */
    @Value("${app.cleanup.rejected-retention-days:7}")
    private int rejectedRetentionDays;

    /**
     * 每天凌晨 3 点半清一遍：超期被驳回的模型把 MinIO 文件删掉，数据库路径置空
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupRejectedModels() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(0, rejectedRetentionDays));

        List<BuildingModel> expiredRejected = modelRepository.findByStatusAndUpdatedAtBefore(2, cutoff);
        if (expiredRejected.isEmpty()) {
            return;
        }

        int deleted = 0;
        for (BuildingModel model : expiredRejected) {
            deleted += cleanupOne(model) ? 1 : 0;
        }

        if (deleted > 0) {
            log.info("Rejected model cleanup finished. deletedObjectsForModels={}/{}", deleted, expiredRejected.size());
        }
    }

    private boolean cleanupOne(BuildingModel model) {
        boolean changed = false;
        try {
            String modelObject = model.getModelFilePath();
            if (modelObject != null && !modelObject.isBlank()) {
                // modelFilePath 存的是 objectName（不是 URL）
                minioService.deleteFile(modelsBucket, modelObject);
                model.setModelFilePath(null);
                changed = true;
            }

            String thumbObject = model.getThumbnailPath();
            if (thumbObject != null && !thumbObject.isBlank() && !isHttpUrl(thumbObject)) {
                minioService.deleteFile(thumbnailsBucket, thumbObject);
                model.setThumbnailPath(null);
                changed = true;
            }
        } catch (Exception e) {
            // 一条删失败别拖垮整个任务，记个日志继续下一条
            log.warn("Failed to cleanup rejected model files. modelId={}", model.getId(), e);
            return false;
        }

        if (changed) {
            modelRepository.save(model);
        }
        return changed;
    }

    private boolean isHttpUrl(String value) {
        String v = value.toLowerCase();
        return v.startsWith("http://") || v.startsWith("https://");
    }
}

