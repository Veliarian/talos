package com.talos.gis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

/**
 * Baseline reference template entity for default terrain surface modifiers.
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
    private String category;

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
        if (!(o instanceof DefaultSurfaceModifierEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}