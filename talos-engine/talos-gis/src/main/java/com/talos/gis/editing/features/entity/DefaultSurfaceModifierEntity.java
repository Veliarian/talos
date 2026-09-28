package com.talos.gis.editing.features.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

/**
 * Global doctrine template entity with visual styling, layer priority rank,
 * and category-specific tactical properties stored as JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "default_surface_modifiers")
public class DefaultSurfaceModifierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String category; // 'ROAD', 'BRIDGE', 'RIVER', 'OPEN_WATER', 'VEGETATION', 'BUILDING', 'SOIL'

    @Column(name = "osm_key", nullable = false)
    private String osmKey;

    @Column(name = "osm_value", nullable = false)
    private String osmValue;

    private String description;

    @Column(name = "movement_priority", nullable = false)
    private int movementPriority = 50;

    @Column(name = "color_2d")
    private String color2d = "#f59e0b";

    @Column(name = "texture_3d")
    private String texture3d = "default";

    @Column(name = "speed_modifier_wheeled", nullable = false)
    private float speedModifierWheeled = 1.0f;

    @Column(name = "speed_modifier_tracked", nullable = false)
    private float speedModifierTracked = 1.0f;

    @Column(name = "visibility_meters")
    private Float visibilityMeters;

    @Column(name = "cover_defense_percent", nullable = false)
    private float coverDefensePercent = 0.0f;

    @Column(name = "properties", columnDefinition = "TEXT")
    private String properties; // Category-specific JSON payload

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultSurfaceModifierEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}