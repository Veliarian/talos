package com.talos.gis.editing.basemap.model;

import java.util.UUID;

public record MapLayerData(
        UUID id,
        String layerType,
        int minZoom,
        int maxZoom,
        boolean isDefault,
        String status
) {}
