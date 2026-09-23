package com.talos.gis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity representing a bounded operational theater map.
 */
@Getter
@Setter
@NoArgsConstructor
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

    public void addLayer(MapLayerEntity layer) {
        layers.add(layer);
        layer.setMap(this);
    }

    public void removeLayer(MapLayerEntity layer) {
        layers.remove(layer);
        layer.setMap(null);
    }

    public void addSurfaceModifier(MapSurfaceModifierEntity modifier) {
        surfaceModifiers.add(modifier);
        modifier.setMap(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}