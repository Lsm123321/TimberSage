package com.arch3d.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartBlurbRequest {

    @NotBlank
    private String technicalName;

    /** AI 中文构件名，可为空 */
    private String aiLabel;
}
