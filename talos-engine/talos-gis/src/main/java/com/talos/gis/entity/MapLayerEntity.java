package com.talos.gis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity representing a locally stored basemap layer (e.g. Satellite, Topographic).
 */
@Getter
@Setter
@NoArgsConstructor
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapLayerEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}