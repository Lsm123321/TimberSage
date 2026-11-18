package com.arch3d.repository;

import com.arch3d.entity.KbEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Repository
public interface KbEntryRepository extends JpaRepository<KbEntry, Integer> {

    /**
     * 管理端列表：所有筛选条件都允许为空，为空就不参与过滤。
     * 注意这里是查全部状态，不像 preset_question 那样只查 enabled=1，
     * 否则禁用/驳回的条目在后台就永远看不见、也改不回来了。
     */
    @Query("""
            SELECT k FROM KbEntry k
            LEFT JOIN k.model m
            WHERE (:modelId IS NULL OR m.id = :modelId)
              AND (:categoryId IS NULL OR k.categoryId = :categoryId)
              AND (:questionType IS NULL OR k.questionType = :questionType)
              AND (:status IS NULL OR k.status = :status)
              AND (:keyword IS NULL OR k.question LIKE %:keyword% OR k.answer LIKE %:keyword%)
            """)
    Page<KbEntry> search(@Param("modelId") Integer modelId,
                         @Param("categoryId") Integer categoryId,
                         @Param("questionType") String questionType,
                         @Param("status") Integer status,
                         @Param("keyword") String keyword,
                         Pageable pageable);

    /** 检索用：取某模型下已通过的全部知识，一次load进内存做匹配 */
    List<KbEntry> findByModelIdAndStatus(Integer modelId, Integer status);

    /** 精确命中：同一模型下归一化问题完全相同 */
    List<KbEntry> findByModelIdAndNormalizedQuestionAndStatus(Integer modelId,
                                                              String normalizedQuestion,
                                                              Integer status);

    /** 同类型知识，用于意图匹配 */
    List<KbEntry> findByModelIdAndQuestionTypeAndStatus(Integer modelId,
                                                         String questionType,
                                                         Integer status);

    long countByStatus(Integer status);

    long countByModelIdAndStatus(Integer modelId, Integer status);

    @Modifying
    @Transactional
    @Query("UPDATE KbEntry k SET k.hitCount = k.hitCount + 1, k.tokensSaved = k.tokensSaved + :tokens WHERE k.id = :id")
    void incrementHit(@Param("id") Integer id, @Param("tokens") Integer tokens);

    @Modifying
    @Transactional
    @Query("UPDATE KbEntry k SET k.helpfulCount = k.helpfulCount + 1 WHERE k.id = :id")
    void incrementHelpful(@Param("id") Integer id);

    /** 模型改名/换朝代时，相关答案可能过时，整批失效 */
    @Modifying
    @Transactional
    void deleteByModelId(Integer modelId);

    /** 命中总数，图谱统计用。空表时返回 0，不要用 SUM 直接取（会得到 null） */
    @Query("SELECT COALESCE(SUM(k.hitCount), 0) FROM KbEntry k")
    long sumHitCount();

    /** 按知识类型统计条数，返回 question_type + cnt 两列 */
    @Query("""
            SELECT k.questionType AS question_type, COUNT(k) AS cnt
            FROM KbEntry k
            WHERE k.status = 1
            GROUP BY k.questionType
            """)
    List<Map<String, Object>> countGroupByType();
}
