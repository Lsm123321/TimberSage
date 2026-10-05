package com.arch3d.service;

import com.arch3d.dto.PartBlurbResponse;
import com.arch3d.dto.PartLabelRequest;

import java.util.Map;

public interface ModelPartLabelService {

    /**
     * 根据 glTF 节点/网格技术名生成中文构件名（键为部件 id）。
     */
    Map<String, String> labelParts(Integer modelId, PartLabelRequest request);

    /**
     * 结合模型上下文与构件名称，生成简短中文介绍（含生成时间、来源归属与免责声明）。
     */
    PartBlurbResponse describePart(Integer modelId, String technicalName, String aiLabel);
}
