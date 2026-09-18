package com.talos.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity representing a bounded operational theater map.
 */
@Data
@Entity
@Table(name = "maps")
public class MapEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_lat", nullable = false)
    private double minLat;

    @Column(name = "max_lat", nullable = false)
    private double maxLat;

    @Column(name = "min_lon", nullable = false)
    private double minLon;

    @Column(name = "max_lon", nullable = false)
    private double maxLon;

    @Column(name = "center_lat", nullable = false)
    private double centerLat;

    @Column(name = "center_lon", nullable = false)
    private double centerLon;

    @Column(name = "size_km", nullable = false)
    private double sizeKm;

    @Column(name = "dem_file_path")
    private String demFilePath;

    @Column(nullable = false)
    private String status; // 'CREATED', 'DOWNLOADING', 'READY', 'FAILED'

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "map", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MapLayerEntity> layers = new ArrayList<>();

    @OneToMany(mappedBy = "map", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MapSurfaceModifierEntity> surfaceModifiers = new ArrayList<>();
}