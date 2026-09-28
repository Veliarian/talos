package com.talos.gis.editing.features.model;

import java.util.UUID;

/**
 * DTO for managing global doctrine surface modifier templates.
 */
public record SurfaceTemplateRequest(
        String category,
        String osmKey,
        String osmValue,
        String description,
        float speedModifierWheeled,
        float speedModifierTracked,
        Float visibilityMeters,
        float coverDefensePercent
) {}