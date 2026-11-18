package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库来源信息，挂在 ChatStreamMeta 上回传给前端。
 * 只放展示需要的字段，不泄露答案内容（答案已经通过 message 事件流式给出）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbSource {
    private Integer entryId;
    private String modelName;
    /** structure / material / history / culture / aesthetic / compare */
    private String questionType;
    /** 这条知识被命中过多少次，用来体现可信度 */
    private Integer hitCount;
    private String reviewedAt;
    /** 命中层级：L1 缓存 / L2 精确 / L3 意图，便于排查 */
    private String matchLayer;
}
