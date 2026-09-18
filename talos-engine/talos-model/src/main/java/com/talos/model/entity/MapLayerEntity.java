package com.talos.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA Entity representing a locally stored basemap layer (e.g. Satellite, Topographic).
 */
@Data
@Entity
@Table(name = "map_layers")
public class MapLayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    private MapEntity map;

    @Column(name = "layer_type", nullable = false)
    private String layerType; // 'SATELLITE', 'TOPOGRAPHIC', 'TACTICAL'

    @Column(name = "storage_format", nullable = false)
    private String storageFormat = "XYZ_DIR";

    @Column(name = "local_path", nullable = false)
    private String localPath;

    @Column(name = "min_zoom", nullable = false)
    private int minZoom;

    @Column(name = "max_zoom", nullable = false)
    private int maxZoom;

    @Column(nullable = false)
    private String status;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}