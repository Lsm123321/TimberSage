package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 构件介绍浮窗中的「参考来源」一行说明（可追溯至库内字段或生成引擎，不含虚构外链）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartBlurbSourceItem {

    /** 简短类别，如「模型条目」「构件节点」 */
    private String label;

    /** 具体说明（脱敏摘录或字段说明） */
    private String detail;
}
