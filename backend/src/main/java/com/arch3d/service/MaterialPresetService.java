package com.arch3d.service;

import com.arch3d.dto.MaterialPresetCreateRequest;
import com.arch3d.dto.MaterialPresetResponse;
import com.arch3d.entity.UserMaterialPreset;

import java.util.List;

public interface MaterialPresetService {
    UserMaterialPreset createPreset(MaterialPresetCreateRequest request, Integer userId);
    List<MaterialPresetResponse> getPresetsByModel(Integer modelId, Integer userId);
    UserMaterialPreset getPresetById(Integer presetId, Integer userId);
    void deletePreset(Integer presetId, Integer userId);
    String getMaterialParams(Integer presetId, Integer userId);
}
