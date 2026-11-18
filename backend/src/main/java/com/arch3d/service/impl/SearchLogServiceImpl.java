package com.arch3d.service.impl;

import com.arch3d.entity.HotSearch;
import com.arch3d.entity.SearchLog;
import com.arch3d.entity.User;
import com.arch3d.repository.HotSearchRepository;
import com.arch3d.repository.SearchLogRepository;
import com.arch3d.service.SearchLogService;
import com.arch3d.util.SearchKeywordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            // 扫描器会往搜索框灌 ${jndi:ldap://...} 之类的载荷；
            // 不合法就只跳过日志，不影响用户正常搜索。
            String safeKeyword = SearchKeywordUtils.normalize(keyword);
            if (safeKeyword == null) {
                return;
            }

            SearchLog searchLog = new SearchLog();
            if (userId != null) {
                User user = new User();
                user.setId(userId);
                searchLog.setUser(user);
            }
            searchLog.setKeyword(safeKeyword);
            if (categoryFilter != null) {
                searchLog.setCategoryFilter(categoryFilter.toString());
            }
            searchLog.setDynastyFilter(dynastyFilter);
            searchLog.setResultCount(resultCount);
            searchLogRepository.save(searchLog);
            log.debug("Search log recorded: keyword={}, userId={}", safeKeyword, userId);
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
        // 数据库里可能残留历史脏数据，读取时再过滤一次，保证首页永远不展示载荷
        return hotSearches.stream()
            .filter(item -> SearchKeywordUtils.isValid(item.getKeyword()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void aggregateHotSearches() {
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime yesterday = today.minusDays(1).atStartOfDay();
            LocalDateTime now = LocalDateTime.now();

            // 拉最近 24 小时的搜索记录，先清洗再按关键词分组计数
            List<SearchLog> recentSearches = searchLogRepository.findByCreatedAtBetween(yesterday, now);
            Map<String, Long> keywordCounts = recentSearches.stream()
                .map(SearchLog::getKeyword)
                .map(SearchKeywordUtils::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));

            // 只保留前 20 个，避免每 10 分钟把整张表刷一遍
            List<Map.Entry<String, Long>> topKeywords = keywordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(20)
                .collect(Collectors.toList());

            // 同一统计日先清空再写入：旧逻辑每次聚合都直接 insert，
            // 同一关键词会被重复写很多行，首页就会出现一屏一模一样的词。
            hotSearchRepository.deleteByStatDate(today);
            for (Map.Entry<String, Long> entry : topKeywords) {
                HotSearch hotSearch = HotSearch.builder()
                    .keyword(entry.getKey())
                    .searchCount(entry.getValue().intValue())
                    .statDate(today)
                    .build();
                hotSearchRepository.save(hotSearch);
            }

            // 顺手把 Redis 里的热搜榜也刷了
            updateHotSearchCache();

            log.info("Hot searches aggregated: {} keywords ({} raw records)", topKeywords.size(), recentSearches.size());
        } catch (Exception e) {
            log.error("Failed to aggregate hot searches", e);
        }
    }

    private void updateHotSearchCache() {
        try {
            // 只取最新统计日的数据，避免把历史日期的旧词混进来
            LocalDate latestStatDate = hotSearchRepository.findMaxStatDate();
            List<HotSearch> hotSearches = latestStatDate == null
                ? List.of()
                : hotSearchRepository.findTop10ByStatDateOrderBySearchCountDesc(latestStatDate);

            redisTemplate.delete(HOT_SEARCH_KEY);
            for (HotSearch hotSearch : hotSearches) {
                if (!SearchKeywordUtils.isValid(hotSearch.getKeyword())) {
                    continue;
                }
                redisTemplate.opsForList().rightPush(HOT_SEARCH_KEY, hotSearch.getKeyword());
            }
        } catch (Exception e) {
            log.error("Failed to update hot search cache", e);
        }
    }
}
