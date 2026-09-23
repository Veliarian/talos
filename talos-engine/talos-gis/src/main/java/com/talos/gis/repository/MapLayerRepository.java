package com.talos.gis.repository;

import com.talos.gis.entity.MapLayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository managing raster and vector layer records for maps.
 */
@Repository
public interface MapLayerRepository extends JpaRepository<MapLayerEntity, UUID> {

    List<MapLayerEntity> findAllByMapId(UUID mapId);

    Optional<MapLayerEntity> findByMapIdAndLayerType(UUID mapId, String layerType);

    void deleteAllByMapId(UUID mapId);
}