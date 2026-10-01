package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员克隆的自定义音色。
 * 样本上传智谱（purpose=voice-clone-input）→ 提交 /v4/voice/clone → 拿到 voice 落库，
 * 之后 TTS 请求的 voice 参数直接使用 voiceId。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "custom_voice")
public class CustomVoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 展示名（管理员填写，如"管理员声音"） */
    @Column(name = "voice_name", length = 64, nullable = false)
    private String voiceName;

    /** 智谱返回的音色 ID（TTS 的 voice 参数值） */
    @Column(name = "voice_id", length = 128, nullable = false)
    private String voiceId;

    /** 音色来源模型（glm-tts-clone），便于后续升级克隆模型 */
    @Column(name = "clone_model", length = 64)
    private String cloneModel;

    /** 复刻时使用的试听文本 */
    @Column(name = "preview_text", length = 255)
    private String previewText;

    /** enabled / disabled */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "enabled";
    }
}
