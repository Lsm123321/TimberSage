package com.arch3d.service.impl;

import com.arch3d.dto.MaterialPresetCreateRequest;
import com.arch3d.dto.MaterialPresetResponse;
import com.arch3d.entity.BuildingModel;
import com.arch3d.entity.User;
import com.arch3d.entity.UserMaterialPreset;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.repository.UserMaterialPresetRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.service.MaterialPresetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialPresetServiceImpl implements MaterialPresetService {

    private final UserMaterialPresetRepository presetRepository;
    private final BuildingModelRepository modelRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserMaterialPreset createPreset(MaterialPresetCreateRequest request, Integer userId) {
        BuildingModel model = modelRepository.findById(request.getModelId())
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 检查是否已存在同名方案
        if (presetRepository.existsByUserIdAndModelIdAndPresetName(userId, request.getModelId(), request.getPresetName())) {
            throw new RuntimeException("该模型下已存在同名材质方案");
        }

        UserMaterialPreset preset = UserMaterialPreset.builder()
            .user(user)
            .model(model)
            .presetName(request.getPresetName())
            .materialParams(request.getMaterialParams())
            .build();

        return presetRepository.save(preset);
    }

    @Override
    public List<MaterialPresetResponse> getPresetsByModel(Integer modelId, Integer userId) {
        List<UserMaterialPreset> presets = presetRepository.findByUserIdAndModelId(userId, modelId);

        return presets.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    @Override
    public UserMaterialPreset getPresetById(Integer presetId, Integer userId) {
        return presetRepository.findByIdAndUserId(presetId, userId)
            .orElseThrow(() -> new RuntimeException("材质方案不存在或无权限访问"));
    }

    @Override
    @Transactional
    public void deletePreset(Integer presetId, Integer userId) {
        UserMaterialPreset preset = getPresetById(presetId, userId);
        presetRepository.delete(preset);
    }

    @Override
    public String getMaterialParams(Integer presetId, Integer userId) {
        UserMaterialPreset preset = getPresetById(presetId, userId);
        return preset.getMaterialParams();
    }

    private MaterialPresetResponse convertToResponse(UserMaterialPreset preset) {
        return MaterialPresetResponse.builder()
            .id(preset.getId())
            .presetName(preset.getPresetName())
            .modelId(preset.getModel().getId())
            .materialParams(preset.getMaterialParams())
            .userId(preset.getUser().getId())
            .createdAt(preset.getCreatedAt())
            .updatedAt(preset.getUpdatedAt())
            .build();
    }
}
