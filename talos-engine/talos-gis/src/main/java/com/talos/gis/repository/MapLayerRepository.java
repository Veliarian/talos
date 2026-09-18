package com.talos.gis.repository;

import com.talos.model.entity.MapLayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MapLayerRepository extends JpaRepository<MapLayerEntity, UUID> {
    Optional<MapLayerEntity> findByMapIdAndLayerType(UUID mapId, String layerType);
}