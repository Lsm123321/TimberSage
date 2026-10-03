package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartBlurbResponse {

    /** AI 生成的正文 */
    private String blurb;

    /** ISO-8601 生成时间（含东八区偏移），便于前端展示「何时生成」 */
    private String generatedAt;

    /** 可追溯的来源归属（库内字段与引擎说明）；非学术论文引用列表 */
    private List<PartBlurbSourceItem> sources;

    /** 统一的参考提示 / 免责声明 */
    private String disclaimer;
}
