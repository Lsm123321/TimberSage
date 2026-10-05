package com.arch3d.service.impl;

import com.arch3d.entity.*;
import com.arch3d.repository.*;
import com.arch3d.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final BuildingModelRepository modelRepository;
    private final AiChatHistoryRepository chatHistoryRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final UserRepository userRepository;
    private final ContributionApplyRepository contributionApplyRepository;
    private final RoleUpgradeApplyRepository roleUpgradeApplyRepository;
    private final ModelFeedbackRepository modelFeedbackRepository;
    private final SiteFeedbackRepository siteFeedbackRepository;
    private final UserModelLikeRepository userModelLikeRepository;
    private final UserModelFavoriteRepository userModelFavoriteRepository;

    @Override
    public Map<String, Object> getDashboardStatistics() {
        // 仪表盘一堆卡片数字，逐项 count 一遍塞进 map
        Map<String, Object> statistics = new HashMap<>();

        long totalModels = modelRepository.count();
        statistics.put("totalModels", totalModels);

        long pendingModels = modelRepository.countByStatus(0);
        statistics.put("pendingModels", pendingModels);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayChats = chatHistoryRepository.countByCreatedAtAfter(todayStart);
        statistics.put("todayChats", todayChats);

        long totalUsers = userRepository.count();
        statistics.put("totalUsers", totalUsers);

        // 贡献者/角色申请两个表合起来算待审批
        long pendingApplications = contributionApplyRepository.countByStatus(0)
            + roleUpgradeApplyRepository.countByStatus(0);
        statistics.put("pendingApplications", pendingApplications);

        long todayDownloads = downloadLogRepository.countByDownloadTimeAfter(todayStart);
        statistics.put("todayDownloads", todayDownloads);
        statistics.put("totalDownloads", downloadLogRepository.count());

        // 待处理反馈 = 模型纠错 + 站点建议
        long pendingFeedback = modelFeedbackRepository.countByStatus(0)
            + siteFeedbackRepository.countByStatus(0);
        statistics.put("pendingFeedback", pendingFeedback);

        // 点赞收藏按关系表行数算
        statistics.put("totalLikes", userModelLikeRepository.count());
        statistics.put("totalFavorites", userModelFavoriteRepository.count());

        return statistics;
    }

    @Override
    public List<Map<String, Object>> get7DayTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());

            long modelsCount = modelRepository.countByCreatedAtBetween(startOfDay, endOfDay);
            dayData.put("models", modelsCount);

            long chatsCount = chatHistoryRepository.countByCreatedAtBetween(startOfDay, endOfDay);
            dayData.put("chats", chatsCount);

            long downloadsCount = downloadLogRepository.countByDownloadTimeBetween(startOfDay, endOfDay);
            dayData.put("downloads", downloadsCount);

            trend.add(dayData);
        }

        return trend;
    }

    @Override
    public List<Map<String, Object>> getDownloadStatistics(LocalDate startDate, LocalDate endDate) {
        List<DownloadLog> logs = getLogsByDateRange(startDate, endDate);
        Map<Integer, Long> downloadCountByModelId = logs.stream()
                .collect(Collectors.groupingBy(DownloadLog::getModelId, Collectors.counting()));

        // 先按下载次数排好序再查模型，避免懒加载字段缺失时"先取前 10 再过滤"最后剩不了几条
        return downloadCountByModelId.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> modelRepository.findById(entry.getKey()).map(model -> {
                    Map<String, Object> modelData = new HashMap<>();
                    modelData.put("modelId", model.getId());
                    modelData.put("modelName", model.getModelName());
                    modelData.put("downloadCount", entry.getValue());
                    modelData.put("dynasty", model.getDynasty());
                    modelData.put("categoryName",
                            model.getCategory() != null ? model.getCategory().getCategoryName() : null);
                    return modelData;
                }).orElse(null))
                .filter(Objects::nonNull)
                .limit(10)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getDownloadLogs(LocalDate startDate, LocalDate endDate) {
        return getLogsByDateRange(startDate, endDate).stream().map(log -> {
            Map<String, Object> modelData = new HashMap<>();
            modelData.put("id", log.getId());
            modelData.put("userId", log.getUserId());
            modelData.put("username", log.getUser() != null ? log.getUser().getUsername() : "匿名");
            modelData.put("modelId", log.getModelId());
            boolean modelExists = log.getModel() != null;
            modelData.put("modelExists", modelExists);
            modelData.put("modelName", modelExists ? log.getModel().getModelName() : null);
            modelData.put("dynasty", modelExists ? log.getModel().getDynasty() : null);
            modelData.put("categoryName", (modelExists && log.getModel().getCategory() != null)
                    ? log.getModel().getCategory().getCategoryName() : null);
            modelData.put("ipAddress", log.getIpAddress());
            modelData.put("downloadTime", log.getDownloadTime());
            return modelData;
        }).collect(Collectors.toList());
    }

    private List<DownloadLog> getLogsByDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            LocalDateTime startTime = startDate.atStartOfDay();
            LocalDateTime endTime = endDate.plusDays(1).atStartOfDay();
            return downloadLogRepository.findByDownloadTimeBetweenOrderByDownloadTimeDesc(startTime, endTime);
        }
        return downloadLogRepository.findAllByOrderByDownloadTimeDesc();
    }
}
