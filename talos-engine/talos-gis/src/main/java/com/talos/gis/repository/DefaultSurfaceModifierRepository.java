package com.talos.gis.repository;

import com.talos.gis.entity.DefaultSurfaceModifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository providing access to baseline terrain modifier templates.
 */
@Repository
public interface DefaultSurfaceModifierRepository extends JpaRepository<DefaultSurfaceModifierEntity, UUID> {

    List<DefaultSurfaceModifierEntity> findAllByCategory(String category);
}