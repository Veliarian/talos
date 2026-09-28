package com.talos.gis.editing.features.model;

/**
 * Payload to create or update a doctrine template with styling and category properties JSON.
 */
public record SurfaceTemplateRequest(
        String category,
        String osmKey,
        String osmValue,
        String description,
        Integer movementPriority,
        String color2d,
        String texture3d,
        float speedModifierWheeled,
        float speedModifierTracked,
        Float visibilityMeters,
        float coverDefensePercent,
        String propertiesJson // Serialized category properties (RoadProps, RiverProps, etc.)
) {
    public SurfaceTemplateRequest {
        if (movementPriority == null) movementPriority = 50;
        if (color2d == null || color2d.isBlank()) color2d = "#f59e0b";
        if (texture3d == null || texture3d.isBlank()) texture3d = "default";
    }
}