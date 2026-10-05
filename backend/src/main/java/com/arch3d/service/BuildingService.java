package com.arch3d.service;

import com.arch3d.dto.BuildingDTO;
import com.arch3d.dto.PageDTO;
import com.arch3d.entity.AncientBuilding;
import com.arch3d.entity.Favorite;

import java.util.List;

public interface BuildingService {
    PageDTO<BuildingDTO> getBuildings(int page, int pageSize, String keyword, String dynasty);
    BuildingDTO getBuildingById(Integer id);
    BuildingDTO createBuilding(BuildingDTO buildingDTO);
    BuildingDTO updateBuilding(Integer id, BuildingDTO buildingDTO);
    void deleteBuilding(Integer id);
    void incrementViewCount(Integer id);
    List<BuildingDTO> getPopularBuildings(int limit);
    PageDTO<Favorite> getUserFavorites(Integer userId, int page, int pageSize);
    void addFavorite(Integer userId, Integer buildingId);
    void removeFavorite(Integer userId, Integer buildingId);
    boolean isFavorite(Integer userId, Integer buildingId);
}
