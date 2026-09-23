package com.talos.gis.dto;

import java.util.UUID;

/**
 * DTO for managing global doctrine surface modifier templates.
 */
public record DefaultModifierDto(
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