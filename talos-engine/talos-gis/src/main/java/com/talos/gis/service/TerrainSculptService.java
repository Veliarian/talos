package com.talos.gis.service;

import com.talos.gis.dto.TerrainSculptRequest;
import com.talos.gis.entity.MapEntity;
import com.talos.gis.repository.MapRepository;
import com.talos.gis.util.DemRaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Service handling interactive elevation raster sculpting (DIG, RAISE, FLATTEN)
 * directly operating on 32-bit Float GeoTIFF matrices.
 */
@Service
public class TerrainSculptService {

    private static final Logger log = LoggerFactory.getLogger(TerrainSculptService.class);
    private static final double METERS_PER_DEGREE = 111132.95;

    private final MapRepository mapRepository;
    private final GisStorageService storageService;

    public TerrainSculptService(MapRepository mapRepository, GisStorageService storageService) {
        this.mapRepository = mapRepository;
        this.storageService = storageService;
    }

    /**
     * Applies a radial brush sculpt operation onto the map's elevation raster.
     *
     * @param mapId   operational map UUID
     * @param request sculpt parameters (center coordinates, radius, delta, operation)
     * @return true altitude in meters at the center point after sculpting
     */
    public double sculptTerrain(UUID mapId, TerrainSculptRequest request) {
        MapEntity map = mapRepository.findById(mapId)
                .orElseThrow(() -> new IllegalArgumentException("Map not found: " + mapId));

        Path demPath = storageService.resolvePath(String.format("maps/%s/terrain.tif", mapId));
        File demFile = demPath.toFile();

        if (!demFile.exists()) {
            throw(new IllegalStateException("Elevation DEM raster file does not exist on disk: " + demPath));
        }

        try {
            DemRaster dem = DemRaster.readFromFile(demFile);

            int width = dem.getWidth();
            int height = dem.getHeight();

            double minLat = map.getMinLat();
            double maxLat = map.getMaxLat();
            double minLon = map.getMinLon();
            double maxLon = map.getMaxLon();

            // Calculate pixel coordinate of sculpt center safely clamped inside bounds
            int centerPxX = (int) Math.round((request.centerLon() - minLon) / (maxLon - minLon) * (width - 1));
            int centerPxY = (int) Math.round((maxLat - request.centerLat()) / (maxLat - minLat) * (height - 1));

            centerPxX = Math.clamp(centerPxX, 0, width - 1);
            centerPxY = Math.clamp(centerPxY, 0, height - 1);

            double mppY = (maxLat - minLat) * METERS_PER_DEGREE / height;
            double mppX = (maxLon - minLon) * METERS_PER_DEGREE * Math.cos(Math.toRadians(request.centerLat())) / width;

            double radius = request.radiusMeters();
            int radiusPxX = (int) Math.ceil(radius / mppX);
            int radiusPxY = (int) Math.ceil(radius / mppY);

            int startX = Math.max(0, centerPxX - radiusPxX);
            int endX = Math.min(width - 1, centerPxX + radiusPxX);
            int startY = Math.max(0, centerPxY - radiusPxY);
            int endY = Math.min(height - 1, centerPxY + radiusPxY);

            float centerCurrentAlt = dem.getElevation(centerPxX, centerPxY);
            double delta = request.deltaMeters();
            String operation = request.operation().toUpperCase();

            for (int y = startY; y <= endY; y++) {
                for (int x = startX; x <= endX; x++) {
                    double distMeters = Math.hypot((x - centerPxX) * mppX, (y - centerPxY) * mppY);

                    if (distMeters <= radius) {
                        // Smooth cosine falloff factor: 1.0 at center, tapering to 0.0 at edge
                        double falloff = 0.5 * (1.0 + Math.cos(Math.PI * distMeters / radius));
                        float currentAlt = dem.getElevation(x, y);

                        float newAlt = switch (operation) {
                            case "DIG" -> (float) (currentAlt - (delta * falloff));
                            case "RAISE" -> (float) (currentAlt + (delta * falloff));
                            case "FLATTEN" -> (float) (currentAlt * (1.0 - falloff) + centerCurrentAlt * falloff);
                            default -> currentAlt;
                        };

                        dem.setElevation(x, y, newAlt);
                    }
                }
            }

            // Write modified matrix directly back to disk
            dem.writeToFile(demFile);

            float finalCenterAlt = dem.getElevation(centerPxX, centerPxY);
            log.info("[TERRAIN SCULPT] Map {}: Applied {} (radius: {}m, delta: {}m). New altitude at center: {} m",
                    mapId, operation, radius, delta, finalCenterAlt);

            return finalCenterAlt;

        } catch (Exception e) {
            log.error("[TERRAIN SCULPT] Failed to sculpt terrain for map {}", mapId, e);
            throw new RuntimeException("Terrain sculpt execution failed: " + e.getMessage(), e);
        }
    }
}