package com.talos.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

/**
 * Map-specific movement and concealment modifier for tactical surfaces and roads.
 */
@Data
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
}