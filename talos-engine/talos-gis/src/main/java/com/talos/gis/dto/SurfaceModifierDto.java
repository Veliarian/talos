package com.talos.gis.dto;

import java.util.UUID;

/**
 * DTO for viewing and updating surface movement and concealment parameters.
 */
public record SurfaceModifierDto(
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