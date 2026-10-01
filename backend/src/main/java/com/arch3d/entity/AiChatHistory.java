package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_chat_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BuildingModel model;

    @Column(name = "user_question", nullable = false, columnDefinition = "TEXT")
    private String userQuestion;

    @Column(name = "ai_answer", nullable = false, columnDefinition = "TEXT")
    private String aiAnswer;

    @Column(name = "ai_model", length = 64)
    private String aiModel;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "helpful")
    private Boolean helpful;

    @Column(name = "chat_note", columnDefinition = "TEXT")
    private String chatNote;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
