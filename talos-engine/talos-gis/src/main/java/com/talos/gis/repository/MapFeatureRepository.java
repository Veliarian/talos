package com.talos.gis.repository;

import com.talos.model.entity.MapFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for map vector features.
 */
@Repository
public interface MapFeatureRepository extends JpaRepository<MapFeatureEntity, UUID> {
    List<MapFeatureEntity> findByMapId(UUID mapId);
    List<MapFeatureEntity> findByMapIdAndCategory(UUID mapId, String category);
}