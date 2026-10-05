package com.arch3d.service;

import com.arch3d.entity.SearchLog;
import com.arch3d.entity.HotSearch;
import java.time.LocalDate;
import java.util.List;

public interface SearchLogService {
    void recordSearchLog(String keyword, Integer categoryFilter, String dynastyFilter, Integer userId, Integer resultCount);
    List<HotSearch> getHotSearches(LocalDate date, int limit);
    void aggregateHotSearches();
}
