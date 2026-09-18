package com.talos.gis.repository;

import com.talos.model.entity.DefaultSurfaceModifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DefaultSurfaceModifierRepository extends JpaRepository<DefaultSurfaceModifierEntity, UUID> {
}