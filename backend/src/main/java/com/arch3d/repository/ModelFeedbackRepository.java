package com.arch3d.repository;

import com.arch3d.entity.ModelFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelFeedbackRepository extends JpaRepository<ModelFeedback, Integer> {
    Page<ModelFeedback> findByModelId(Integer modelId, Pageable pageable);
    Page<ModelFeedback> findByUserId(Integer userId, Pageable pageable);
    Page<ModelFeedback> findByStatus(Integer status, Pageable pageable);
    long countByStatus(Integer status);
    void deleteByModelId(Integer modelId);
    void deleteByUserId(Integer userId);

    /**
     * 公开展示用：只取「已采纳」的纠错。
     * 待审核(0) 和已驳回(2) 一律不进前端列表，避免未核实内容被当成官方结论。
     */
    Page<ModelFeedback> findByModelIdAndStatus(Integer modelId, Integer status, Pageable pageable);

    /** 我的反馈：本人能看到自己的全部状态；管理员审核后他人才能看到 */
    Page<ModelFeedback> findByUserIdAndModelId(Integer userId, Integer modelId, Pageable pageable);
}
