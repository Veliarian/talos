package com.talos.gis.editing.features.model;

import java.util.UUID;

public record TacticalModifierData(
        UUID id,
        String category,
        String osmKey,
        String osmValue,
        String description,
        int movementPriority,
        String color2d,
        String texture3d,
        float speedModifierWheeled,
        float speedModifierTracked,
        Float visibilityMeters,
        float coverDefensePercent,
        String propertiesJson
) {}