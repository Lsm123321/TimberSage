package com.arch3d.service.impl;

import com.arch3d.dto.PageDTO;
import com.arch3d.entity.*;
import com.arch3d.repository.*;
import com.arch3d.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    /**
     * 下载明细分页查询。
     *
     * 两个关键点：
     * 1) 走仓储的 JOIN FETCH 查询，避免逐行懒加载 user/model/category 造成 N+1；
     * 2) 在数据库层面分页，只取当前页几十条，不再把全表塞进内存和网络。
     * 页码从 1 开始（跟前端一致），转成 JPA 的 0 基索引时要减 1。
     */
    @Override
    public PageDTO<Map<String, Object>> getDownloadLogs(LocalDate startDate, LocalDate endDate, int page, int pageSize) {
        // 兜底：页码/页大小非法时给安全默认值，避免 PageRequest 抛异常
        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize);

        Page<DownloadLog> logPage;
        if (startDate != null && endDate != null) {
            LocalDateTime startTime = startDate.atStartOfDay();
            // 结束日期要包含当天整天，所以用「次日零点」做开区间上界
            LocalDateTime endTime = endDate.plusDays(1).atStartOfDay();
            logPage = downloadLogRepository.findByDateRangeWithDetails(startTime, endTime, pageable);
        } else {
            logPage = downloadLogRepository.findAllWithDetails(pageable);
        }

        List<Map<String, Object>> records = logPage.getContent().stream()
                .map(this::toDownloadLogRow)
                .collect(Collectors.toList());

        return PageDTO.<Map<String, Object>>builder()
                .total(logPage.getTotalElements())
                .page(safePage)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    /** 单条下载日志转前端需要的字段结构 */
    private Map<String, Object> toDownloadLogRow(DownloadLog log) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", log.getId());
        row.put("userId", log.getUserId());
        row.put("username", log.getUser() != null ? log.getUser().getUsername() : "匿名");
        row.put("modelId", log.getModelId());
        boolean modelExists = log.getModel() != null;
        row.put("modelExists", modelExists);
        row.put("modelName", modelExists ? log.getModel().getModelName() : null);
        row.put("dynasty", modelExists ? log.getModel().getDynasty() : null);
        row.put("categoryName", (modelExists && log.getModel().getCategory() != null)
                ? log.getModel().getCategory().getCategoryName() : null);
        row.put("ipAddress", log.getIpAddress());
        row.put("downloadTime", log.getDownloadTime());
        return row;
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
