package com.talos.gis.repository;

import com.talos.model.entity.MapSurfaceModifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MapSurfaceModifierRepository extends JpaRepository<MapSurfaceModifierEntity, UUID> {
}