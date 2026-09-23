package com.talos.gis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

/**
 * Map-specific movement and concealment modifier for tactical surfaces and roads.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "map_surface_modifiers")
public class MapSurfaceModifierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    private MapEntity map;

    @Column(nullable = false)
    private String category; // 'ROAD', 'VEGETATION', 'WATER', 'SOIL'

    @Column(name = "osm_key", nullable = false)
    private String osmKey;

    @Column(name = "osm_value", nullable = false)
    private String osmValue;

    private String description;

    @Column(name = "speed_modifier_wheeled", nullable = false)
    private float speedModifierWheeled;

    @Column(name = "speed_modifier_tracked", nullable = false)
    private float speedModifierTracked;

    @Column(name = "visibility_meters")
    private Float visibilityMeters;

    @Column(name = "cover_defense_percent", nullable = false)
    private float coverDefensePercent;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapSurfaceModifierEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}