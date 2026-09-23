package com.talos.gis.repository;

import com.talos.gis.entity.MapEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository managing bounded operational theater map entities.
 */
@Repository
public interface MapRepository extends JpaRepository<MapEntity, UUID> {

    Optional<MapEntity> findByName(String name);

    boolean existsByName(String name);

    List<MapEntity> findAllByStatus(String status);
}