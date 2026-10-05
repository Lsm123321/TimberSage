package com.arch3d.service.impl;

import com.arch3d.dto.BuildingDTO;
import com.arch3d.dto.PageDTO;
import com.arch3d.entity.AncientBuilding;
import com.arch3d.entity.Favorite;
import com.arch3d.entity.User;
import com.arch3d.repository.AncientBuildingRepository;
import com.arch3d.repository.FavoriteRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.service.BuildingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BuildingServiceImpl implements BuildingService {

    private final AncientBuildingRepository buildingRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;

    @Override
    public PageDTO<BuildingDTO> getBuildings(int page, int pageSize, String keyword, String dynasty) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createTime").descending());
        Page<AncientBuilding> buildingPage;

        if (keyword != null && !keyword.isEmpty()) {
            buildingPage = buildingRepository.searchByKeyword(keyword, pageable);
        } else if (dynasty != null && !dynasty.isEmpty()) {
            buildingPage = buildingRepository.findByDynasty(dynasty, pageable);
        } else {
            buildingPage = buildingRepository.findAll(pageable);
        }

        return PageDTO.<BuildingDTO>builder()
            .total(buildingPage.getTotalElements())
            .page(page)
            .pageSize(pageSize)
            .records(buildingPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()))
            .build();
    }

    @Override
    public BuildingDTO getBuildingById(Integer id) {
        AncientBuilding building = buildingRepository.findById(id.longValue())
            .orElseThrow(() -> new RuntimeException("建筑不存在: " + id));
        return convertToDTO(building);
    }

    @Override
    @Transactional
    public BuildingDTO createBuilding(BuildingDTO buildingDTO) {
        AncientBuilding building = convertToEntity(buildingDTO);
        AncientBuilding saved = buildingRepository.save(building);
        return convertToDTO(saved);
    }

    @Override
    @Transactional
    public BuildingDTO updateBuilding(Integer id, BuildingDTO buildingDTO) {
        AncientBuilding building = buildingRepository.findById(id.longValue())
            .orElseThrow(() -> new RuntimeException("建筑不存在: " + id));

        if (buildingDTO.getName() != null) {
            building.setName(buildingDTO.getName());
        }
        if (buildingDTO.getNameEn() != null) {
            building.setNameEn(buildingDTO.getNameEn());
        }
        if (buildingDTO.getDescription() != null) {
            building.setDescription(buildingDTO.getDescription());
        }
        if (buildingDTO.getDynasty() != null) {
            building.setDynasty(buildingDTO.getDynasty());
        }
        if (buildingDTO.getLocation() != null) {
            building.setLocation(buildingDTO.getLocation());
        }
        if (buildingDTO.getCoverImage() != null) {
            building.setCoverImage(buildingDTO.getCoverImage());
        }
        if (buildingDTO.getModelUrl() != null) {
            building.setModelUrl(buildingDTO.getModelUrl());
        }
        if (buildingDTO.getModelType() != null) {
            building.setModelType(buildingDTO.getModelType());
        }
        if (buildingDTO.getSpecifications() != null) {
            building.setSpecifications(buildingDTO.getSpecifications());
        }
        if (buildingDTO.getHistoricalSignificance() != null) {
            building.setHistoricalSignificance(buildingDTO.getHistoricalSignificance());
        }

        AncientBuilding updated = buildingRepository.save(building);
        return convertToDTO(updated);
    }

    @Override
    @Transactional
    public void deleteBuilding(Integer id) {
        if (!buildingRepository.existsById(id.longValue())) {
            throw new RuntimeException("建筑不存在: " + id);
        }
        buildingRepository.deleteById(id.longValue());
    }

    @Override
    @Transactional
    public void incrementViewCount(Integer id) {
        buildingRepository.incrementViewCount(id.longValue());
    }

    @Override
    public List<BuildingDTO> getPopularBuildings(int limit) {
        return buildingRepository.findTop10ByOrderByViewCountDesc().stream()
            .limit(limit)
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    @Override
    public PageDTO<Favorite> getUserFavorites(Integer userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<Favorite> favoritePage = favoriteRepository.findByUserIdOrderByCreateTimeDesc(userId.longValue(), pageable);

        return PageDTO.<Favorite>builder()
            .total(favoritePage.getTotalElements())
            .page(page)
            .pageSize(pageSize)
            .records(favoritePage.getContent())
            .build();
    }

    @Override
    @Transactional
    public void addFavorite(Integer userId, Integer buildingId) {
        if (favoriteRepository.existsByUserIdAndBuildingId(userId.longValue(), buildingId.longValue())) {
            throw new RuntimeException("已经收藏过此建筑");
        }

        Favorite favorite = Favorite.builder()
            .userId(userId.longValue())
            .buildingId(buildingId.longValue())
            .build();
        favoriteRepository.save(favorite);
        buildingRepository.incrementFavoriteCount(buildingId.longValue());
    }

    @Override
    @Transactional
    public void removeFavorite(Integer userId, Integer buildingId) {
        Favorite favorite = favoriteRepository.findByUserIdAndBuildingId(userId.longValue(), buildingId.longValue())
            .orElseThrow(() -> new RuntimeException("收藏记录不存在"));
        favoriteRepository.delete(favorite);
        buildingRepository.decrementFavoriteCount(buildingId.longValue());
    }

    @Override
    public boolean isFavorite(Integer userId, Integer buildingId) {
        return favoriteRepository.existsByUserIdAndBuildingId(userId.longValue(), buildingId.longValue());
    }

    private BuildingDTO convertToDTO(AncientBuilding building) {
        return BuildingDTO.builder()
            .id(building.getId())
            .name(building.getName())
            .nameEn(building.getNameEn())
            .description(building.getDescription())
            .dynasty(building.getDynasty())
            .location(building.getLocation())
            .coverImage(building.getCoverImage())
            .modelUrl(building.getModelUrl())
            .modelType(building.getModelType())
            .specifications(building.getSpecifications())
            .historicalSignificance(building.getHistoricalSignificance())
            .viewCount(building.getViewCount())
            .favoriteCount(building.getFavoriteCount())
            .createTime(building.getCreateTime())
            .updateTime(building.getUpdateTime())
            .build();
    }

    private AncientBuilding convertToEntity(BuildingDTO dto) {
        return AncientBuilding.builder()
            .name(dto.getName())
            .nameEn(dto.getNameEn())
            .description(dto.getDescription())
            .dynasty(dto.getDynasty())
            .location(dto.getLocation())
            .coverImage(dto.getCoverImage())
            .modelUrl(dto.getModelUrl())
            .modelType(dto.getModelType())
            .specifications(dto.getSpecifications())
            .historicalSignificance(dto.getHistoricalSignificance())
            .viewCount(dto.getViewCount() != null ? dto.getViewCount() : 0)
            .favoriteCount(dto.getFavoriteCount() != null ? dto.getFavoriteCount() : 0)
            .build();
    }
}
