package com.arch3d.service;

import com.arch3d.entity.BuildingModel;

import java.util.List;
import java.util.Map;

public interface MapService {
    Map<String, Object> getMapOverview(String keyword);

    /**
     * 按范围检索地图点位。
     * scope：model=模型名 / dynasty=朝代 / location=地点 / all=三者任一
     */
    Map<String, Object> getMapOverview(String keyword, String scope);

    List<BuildingModel> getRegionModels(String regionCode);

    Map<String, Object> generateModelInsight(Integer modelId, String modelName, String dynasty, String location, String description);
}

