package com.arch3d.repository;

import com.arch3d.entity.BuildingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuildingCategoryRepository extends JpaRepository<BuildingCategory, Integer> {
    BuildingCategory findByCategoryCode(String categoryCode);
    List<BuildingCategory> findAllByOrderBySortOrderAsc();
}
