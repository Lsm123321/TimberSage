package com.arch3d.config;

import com.arch3d.service.SearchLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledTasks {

    private final SearchLogService searchLogService;

    @Scheduled(cron = "0 */10 * * * *") // 每10分钟执行一次
    public void aggregateHotSearches() {
        log.info("Starting hot search aggregation task");
        try {
            searchLogService.aggregateHotSearches();
            log.info("Hot search aggregation completed");
        } catch (Exception e) {
            log.error("Error aggregating hot searches", e);
        }
    }
}
