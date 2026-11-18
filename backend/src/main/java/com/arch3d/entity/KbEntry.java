package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * AI 问答知识库条目。
 *
 * 每条记录是「某个模型 + 某个知识类型」下的一条标准问答。
 * 用户提问时先查这张表，命中就直接返回，不再调用大模型；
 * 未命中才走 LLM，并（可选）落成候选等人工审核。
 *
 * 为什么 normalizedQuestion 要单独存一列：
 * 匹配前要先把问题压成统一形式（去标点、同义替换、剥模型名），
 * 每次查询现算太浪费，入库时算好存下来，查询时直接比对。
 */
@Entity
@Table(name = "kb_entry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BuildingModel model;

    /** 分类冗余字段，方便按分类批量维护（模型换了分类时以模型的为准） */
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    /** 归一化后的问题，匹配用 */
    @Column(name = "normalized_question", nullable = false, length = 512)
    private String normalizedQuestion;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** LLM_GENERATED / MANUAL / USER_RATED / FALLBACK，用来区分答案的可信来源 */
    @Column(name = "answer_source", nullable = false, length = 32)
    private String answerSource;

    /** structure / material / history / culture / aesthetic / compare */
    @Column(name = "question_type", length = 32)
    private String questionType;

    /** 0=候选 1=已通过 2=已驳回 3=已归档，只有 1 参与检索 */
    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount;

    @Column(name = "helpful_count", nullable = false)
    private Integer helpfulCount;

    /** 累计节省的 token 估算值，用来在管理端展示收益 */
    @Column(name = "tokens_saved", nullable = false)
    private Integer tokensSaved;

    @Column(name = "reviewed_by")
    private Integer reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    /** 编辑时 +1，和 Redis 缓存 key 关联，用来精确失效 */
    @Column(name = "knowledge_version", nullable = false)
    private Integer knowledgeVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = 0;
        }
        if (hitCount == null) {
            hitCount = 0;
        }
        if (helpfulCount == null) {
            helpfulCount = 0;
        }
        if (tokensSaved == null) {
            tokensSaved = 0;
        }
        if (knowledgeVersion == null) {
            knowledgeVersion = 1;
        }
        if (answerSource == null) {
            answerSource = "MANUAL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
