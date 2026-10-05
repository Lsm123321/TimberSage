package com.arch3d.service.impl;

import com.arch3d.entity.HotSearch;
import com.arch3d.entity.SearchLog;
import com.arch3d.entity.User;
import com.arch3d.repository.HotSearchRepository;
import com.arch3d.repository.SearchLogRepository;
import com.arch3d.service.SearchLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogServiceImpl implements SearchLogService {

    private final SearchLogRepository searchLogRepository;
    private final HotSearchRepository hotSearchRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HOT_SEARCH_KEY = "hot:search";
    private static final int HOT_SEARCH_LIMIT = 10;

    @Override
    @Async
    @Transactional
    public void recordSearchLog(String keyword, Integer categoryFilter, String dynastyFilter, Integer userId, Integer resultCount) {
        try {
            SearchLog searchLog = new SearchLog();
            if (userId != null) {
                User user = new User();
                user.setId(userId);
                searchLog.setUser(user);
            }
            searchLog.setKeyword(keyword);
            if (categoryFilter != null) {
                searchLog.setCategoryFilter(categoryFilter.toString());
            }
            searchLog.setDynastyFilter(dynastyFilter);
            searchLog.setResultCount(resultCount);
            searchLogRepository.save(searchLog);
            log.info("Search log recorded: keyword={}, userId={}", keyword, userId);
        } catch (Exception e) {
            log.error("Failed to record search log", e);
        }
    }

    @Override
    public List<HotSearch> getHotSearches(LocalDate date, int limit) {
        if (date == null) {
            date = LocalDate.now();
        }
        List<HotSearch> hotSearches = hotSearchRepository.findByStatDateOrderBySearchCountDesc(date);
        return hotSearches.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void aggregateHotSearches() {
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime yesterday = today.minusDays(1).atStartOfDay();
            LocalDateTime now = LocalDateTime.now();

            // 拉最近 24 小时的搜索记录，按关键词分组计数
            List<SearchLog> recentSearches = searchLogRepository.findByCreatedAtBetween(yesterday, now);
            Map<String, Long> keywordCounts = recentSearches.stream()
                .collect(Collectors.groupingBy(SearchLog::getKeyword, Collectors.counting()));

            // 每个关键词写一条当日热点记录
            for (Map.Entry<String, Long> entry : keywordCounts.entrySet()) {
                HotSearch hotSearch = HotSearch.builder()
                    .keyword(entry.getKey())
                    .searchCount(entry.getValue().intValue())
                    .statDate(today)
                    .build();
                hotSearchRepository.save(hotSearch);
            }

            // 顺手把 Redis 里的热搜榜也刷了
            updateHotSearchCache();

            log.info("Hot searches aggregated: {} keywords", keywordCounts.size());
        } catch (Exception e) {
            log.error("Failed to aggregate hot searches", e);
        }
    }

    private void updateHotSearchCache() {
        try {
            List<HotSearch> hotSearches = hotSearchRepository.findTop10ByOrderBySearchCountDesc();
            redisTemplate.delete(HOT_SEARCH_KEY);
            for (int i = 0; i < hotSearches.size(); i++) {
                HotSearch hotSearch = hotSearches.get(i);
                redisTemplate.opsForList().rightPush(HOT_SEARCH_KEY, hotSearch.getKeyword());
            }
        } catch (Exception e) {
            log.error("Failed to update hot search cache", e);
        }
    }
}
