package com.arch3d.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PartLabelRequest {

    @NotEmpty
    @Size(max = 40, message = "单次最多提交40个部件")
    private List<@Valid PartItem> parts;

    @Data
    public static class PartItem {
        @NotBlank
        private String id;
        @NotBlank
        private String technicalName;
    }
}
