package com.talos.gis.editing.features.model;

import java.util.UUID;

public record SurfaceTemplateResponse(
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