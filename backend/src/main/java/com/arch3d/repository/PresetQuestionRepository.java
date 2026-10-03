package com.arch3d.repository;

import com.arch3d.entity.PresetQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PresetQuestionRepository extends JpaRepository<PresetQuestion, Integer> {
    List<PresetQuestion> findByCategoryIdAndEnabled(Integer categoryId, Integer enabled);
    List<PresetQuestion> findByModelIdAndEnabled(Integer modelId, Integer enabled);
    List<PresetQuestion> findByEnabledOrderBySortOrderAsc(Integer enabled);
    List<PresetQuestion> findByModelIdAndEnabledTrue(Integer modelId);
    List<PresetQuestion> findByCategoryIdAndEnabledTrue(Integer categoryId);
    List<PresetQuestion> findByEnabledTrue();
    Page<PresetQuestion> findByEnabled(Integer enabled, Pageable pageable);

    @Modifying
    @Query("UPDATE PresetQuestion p SET p.clickCount = p.clickCount + 1 WHERE p.id = :id")
    void incrementClickCount(@Param("id") Integer id);

    void deleteByModelId(Integer modelId);
}
