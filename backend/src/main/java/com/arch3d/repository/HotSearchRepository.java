package com.arch3d.repository;

import com.arch3d.entity.HotSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HotSearchRepository extends JpaRepository<HotSearch, Integer> {
    HotSearch findByKeywordAndStatDate(String keyword, LocalDate statDate);
    List<HotSearch> findByStatDateOrderBySearchCountDesc(LocalDate statDate);
    List<HotSearch> findTop10ByOrderBySearchCountDesc();

    List<HotSearch> findTop10ByStatDateOrderBySearchCountDesc(LocalDate statDate);

    @Query("SELECT max(h.statDate) FROM HotSearch h")
    LocalDate findMaxStatDate();

    @Modifying
    @Query("UPDATE HotSearch h SET h.searchCount = h.searchCount + 1 WHERE h.id = :id")
    void incrementSearchCount(@Param("id") Integer id);
}
