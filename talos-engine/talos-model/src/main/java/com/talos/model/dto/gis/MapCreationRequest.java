package com.talos.model.dto.gis;

import java.util.List;

/**
 * Request payload to initiate a new bounded theater map ingestion.
 */
public record MapCreationRequest(
        String name,
        String description,
        Double centerLat,
        Double centerLon,
        Double sizeKm,              // Theater width and height in km (e.g. 20.0)
        List<String> layerTypes,    // e.g. ["SATELLITE", "TOPOGRAPHIC", "TACTICAL"]
        Integer minZoom,            // Default: 12
        Integer maxZoom             // Default: 15 or 16
) {}