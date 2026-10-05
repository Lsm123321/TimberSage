package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.BuildingDTO;
import com.arch3d.dto.PageDTO;
import com.arch3d.entity.Favorite;
import com.arch3d.security.UserPrincipal;
import com.arch3d.service.BuildingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
@Tag(name = "古建筑管理", description = "古建筑CRUD接口")
@SecurityRequirement(name = "Bearer Authentication")
public class BuildingController {

    private final BuildingService buildingService;

    @GetMapping
    @Operation(summary = "获取建筑列表", description = "分页获取古建筑列表，支持关键词和朝代筛选")
    public ResponseEntity<ApiResponse<PageDTO<BuildingDTO>>> getBuildings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dynasty) {
        PageDTO<BuildingDTO> buildings = buildingService.getBuildings(page, pageSize, keyword, dynasty);
        return ResponseEntity.ok(ApiResponse.success(buildings));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取建筑详情", description = "根据ID获取古建筑详细信息")
    public ResponseEntity<ApiResponse<BuildingDTO>> getBuildingById(@PathVariable Integer id) {
        buildingService.incrementViewCount(id);
        BuildingDTO building = buildingService.getBuildingById(id);
        return ResponseEntity.ok(ApiResponse.success(building));
    }

    @GetMapping("/popular")
    @Operation(summary = "获取热门建筑", description = "获取浏览量最高的建筑列表")
    public ResponseEntity<ApiResponse<List<BuildingDTO>>> getPopularBuildings(
            @RequestParam(defaultValue = "10") int limit) {
        List<BuildingDTO> buildings = buildingService.getPopularBuildings(limit);
        return ResponseEntity.ok(ApiResponse.success(buildings));
    }

    @PostMapping
    @Operation(summary = "创建建筑", description = "创建新的古建筑信息")
    public ResponseEntity<ApiResponse<BuildingDTO>> createBuilding(@RequestBody BuildingDTO buildingDTO) {
        BuildingDTO created = buildingService.createBuilding(buildingDTO);
        return ResponseEntity.ok(ApiResponse.success("创建成功", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新建筑", description = "更新古建筑信息")
    public ResponseEntity<ApiResponse<BuildingDTO>> updateBuilding(
            @PathVariable Integer id,
            @RequestBody BuildingDTO buildingDTO) {
        BuildingDTO updated = buildingService.updateBuilding(id, buildingDTO);
        return ResponseEntity.ok(ApiResponse.success("更新成功", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除建筑", description = "删除古建筑信息")
    public ResponseEntity<ApiResponse<Void>> deleteBuilding(@PathVariable Integer id) {
        buildingService.deleteBuilding(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    @GetMapping("/favorites")
    @Operation(summary = "获取用户收藏", description = "获取当前用户的收藏列表")
    public ResponseEntity<ApiResponse<PageDTO<Favorite>>> getUserFavorites(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageDTO<Favorite> favorites = buildingService.getUserFavorites(userPrincipal.getId(), page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(favorites));
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "添加收藏", description = "收藏古建筑")
    public ResponseEntity<ApiResponse<Void>> addFavorite(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Integer id) {
        try {
            buildingService.addFavorite(userPrincipal.getId(), id);
            return ResponseEntity.ok(ApiResponse.success("收藏成功", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "取消收藏", description = "取消收藏古建筑")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Integer id) {
        try {
            buildingService.removeFavorite(userPrincipal.getId(), id);
            return ResponseEntity.ok(ApiResponse.success("取消收藏成功", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}/favorite")
    @Operation(summary = "检查收藏状态", description = "检查当前用户是否收藏了该建筑")
    public ResponseEntity<ApiResponse<Boolean>> isFavorite(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Integer id) {
        boolean favorite = buildingService.isFavorite(userPrincipal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(favorite));
    }
}
