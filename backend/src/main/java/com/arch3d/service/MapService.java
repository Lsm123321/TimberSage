package com.arch3d.service;

import com.arch3d.entity.BuildingModel;

import java.util.List;
import java.util.Map;

public interface MapService {
    Map<String, Object> getMapOverview(String keyword);

    List<BuildingModel> getRegionModels(String regionCode);

    Map<String, Object> generateModelInsight(Integer modelId, String modelName, String dynasty, String location, String description);
}

