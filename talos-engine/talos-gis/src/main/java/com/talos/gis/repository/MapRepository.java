package com.talos.gis.repository;

import com.talos.model.entity.MapEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MapRepository extends JpaRepository<MapEntity, UUID> {
}