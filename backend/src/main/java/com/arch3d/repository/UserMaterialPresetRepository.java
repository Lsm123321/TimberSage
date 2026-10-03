package com.arch3d.repository;

import com.arch3d.entity.UserMaterialPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserMaterialPresetRepository extends JpaRepository<UserMaterialPreset, Integer> {
    List<UserMaterialPreset> findByUserId(Integer userId);
    List<UserMaterialPreset> findByModelId(Integer modelId);
    List<UserMaterialPreset> findByUserIdAndModelId(Integer userId, Integer modelId);
    Optional<UserMaterialPreset> findByUserIdAndModelIdAndPresetName(Integer userId, Integer modelId, String presetName);
    boolean existsByUserIdAndModelIdAndPresetName(Integer userId, Integer modelId, String presetName);
    Optional<UserMaterialPreset> findByIdAndUserId(Integer id, Integer userId);
    void deleteByModelId(Integer modelId);
    void deleteByUserId(Integer userId);
}
