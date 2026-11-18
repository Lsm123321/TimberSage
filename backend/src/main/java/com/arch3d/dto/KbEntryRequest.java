package com.arch3d.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增/编辑知识条目的请求体。
 * status 允许为空：为空时新增默认落成候选（0），由管理员审核后才上线。
 */
@Data
public class KbEntryRequest {

    @NotNull(message = "必须指定关联模型")
    private Integer modelId;

    @NotBlank(message = "问题不能为空")
    private String question;

    @NotBlank(message = "答案不能为空")
    private String answer;

    /** structure / material / history / culture / aesthetic / compare */
    private String questionType;

    /** 0候选 1通过 2驳回 3归档；不传则新增为0、编辑保持原值 */
    private Integer status;

    private String answerSource;

    private String rejectReason;
}
