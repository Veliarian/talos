package com.talos.gis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity representing a distinct geographic vector feature (road, building, forest, fortification)
 * belonging to a specific bounded operational map.
 */
@Getter
@Setter
@NoArgsConstructor
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
    private String category; // 'ROAD', 'VEGETATION', 'BUILDING', 'WATER', 'SOIL', 'FORTIFICATION'

    @Column(name = "type_key", nullable = false)
    private String typeKey;

    @Column(name = "type_value", nullable = false)
    private String typeValue;

    @Column(name = "geometry_type", nullable = false)
    private String geometryType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String geojson;

    @Column(nullable = false)
    private String status = "OPERATIONAL"; // 'OPERATIONAL', 'DESTROYED', 'MINED', 'CHECKPOINT'

    @Column(name = "speed_modifier_override_wheeled")
    private Float speedModifierOverrideWheeled;

    @Column(name = "speed_modifier_override_tracked")
    private Float speedModifierOverrideTracked;

    @Column(name = "visibility_override")
    private Float visibilityOverride;

    @Column(name = "cover_defense_override")
    private Float coverDefenseOverride;

    @Column(name = "is_custom_modified", nullable = false)
    private boolean isCustomModified = false;

    @Column(name = "custom_notes")
    private String customNotes;

    @Column(name = "height_meters")
    private Float heightMeters;

    @Column(name = "width_meters")
    private Float widthMeters;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapFeatureEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}