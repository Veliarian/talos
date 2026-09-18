package com.talos.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA Entity representing a distinct geographic vector feature (road, building, forest)
 * belonging to a specific bounded operational map.
 */
@Data
@Entity
@Table(name = "map_features")
public class MapFeatureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    private MapEntity map;

    @Column(name = "osm_id")
    private Long osmId;

    private String name;

    @Column(nullable = false)
    private String category; // 'ROAD', 'VEGETATION', 'BUILDING', 'WATER', 'SOIL'

    @Column(name = "type_key", nullable = false)
    private String typeKey;

    @Column(name = "type_value", nullable = false)
    private String typeValue;

    @Column(name = "geometry_type", nullable = false)
    private String geometryType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String geojson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}