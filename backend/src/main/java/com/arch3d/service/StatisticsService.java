package com.arch3d.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

public interface StatisticsService {
    Map<String, Object> getDashboardStatistics();
    List<Map<String, Object>> get7DayTrend();
    List<Map<String, Object>> getDownloadStatistics(LocalDate startDate, LocalDate endDate);
    List<Map<String, Object>> getDownloadLogs(LocalDate startDate, LocalDate endDate);
}
