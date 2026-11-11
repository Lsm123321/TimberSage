package com.arch3d.service;

import com.arch3d.dto.PageDTO;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

public interface StatisticsService {
    Map<String, Object> getDashboardStatistics();
    List<Map<String, Object>> get7DayTrend();
    List<Map<String, Object>> getDownloadStatistics(LocalDate startDate, LocalDate endDate);

    /**
     * 下载明细分页查询。
     * 原来全量返回，7600 条就 1.66MB，前端一次性渲染会把浏览器卡死；
     * 改服务端分页后每页只传几十条。
     */
    PageDTO<Map<String, Object>> getDownloadLogs(LocalDate startDate, LocalDate endDate, int page, int pageSize);
}
