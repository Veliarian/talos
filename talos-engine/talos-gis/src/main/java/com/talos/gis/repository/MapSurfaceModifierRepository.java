package com.talos.gis.repository;

import com.talos.gis.entity.MapSurfaceModifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for tactical surface modifiers.
 */
@Repository
public interface MapSurfaceModifierRepository extends JpaRepository<MapSurfaceModifierEntity, UUID> {

    List<MapSurfaceModifierEntity> findAllByMapId(UUID mapId);

    void deleteAllByMapId(UUID mapId);
}