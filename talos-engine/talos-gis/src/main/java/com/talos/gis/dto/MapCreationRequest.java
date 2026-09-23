package com.talos.gis.dto;

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
        Integer maxZoom             // Default: 15
) {
    // Compact constructor ensuring robust default values against null payloads
    public MapCreationRequest {
        if (sizeKm == null || sizeKm <= 0.0) {
            sizeKm = 10.0;
        }
        if (minZoom == null) {
            minZoom = 12;
        }
        if (maxZoom == null) {
            maxZoom = 15;
        }
        if (layerTypes == null || layerTypes.isEmpty()) {
            layerTypes = List.of("SATELLITE");
        }
    }
}