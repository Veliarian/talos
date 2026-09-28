package com.talos.gis.editing.features.model;

import java.util.UUID;

/**
 * DTO for viewing and updating surface movement and concealment parameters.
 */
public record TacticalModifierData(
        UUID id,
        String category,
        String osmKey,
        String osmValue,
        String description,
        float speedModifierWheeled,
        float speedModifierTracked,
        Float visibilityMeters,
        float coverDefensePercent
) {}