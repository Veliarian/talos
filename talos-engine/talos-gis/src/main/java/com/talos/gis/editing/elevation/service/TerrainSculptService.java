package com.talos.gis.editing.elevation.service;

import com.talos.gis.core.service.GisStorageService;
import com.talos.gis.editing.elevation.model.LinearSculptRequest;
import com.talos.gis.editing.elevation.model.RadialSculptRequest;
import com.talos.gis.core.entity.MapEntity;
import com.talos.gis.core.repository.MapRepository;
import com.talos.gis.editing.elevation.util.DemRaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing radial brush and linear vector elevation sculpting (trenches, berms, ditches),
 * operating directly on 32-bit Float GeoTIFF rasters with integrated undo/redo history.
 */
@Service
public class TerrainSculptService {

    private static final Logger log = LoggerFactory.getLogger(TerrainSculptService.class);
    private static final double METERS_PER_DEGREE = 111132.95;

    private final MapRepository mapRepository;
    private final GisStorageService storageService;
    private final TerrainHistoryService historyService;

    public TerrainSculptService(MapRepository mapRepository,
                                GisStorageService storageService,
                                TerrainHistoryService historyService) {
        this.mapRepository = mapRepository;
        this.storageService = storageService;
        this.historyService = historyService;
    }

    /**
     * Radial brush terrain sculpt (DIG, RAISE, FLATTEN).
     */
    public double sculptTerrain(UUID mapId, RadialSculptRequest request) {
        MapEntity map = getMapOrThrow(mapId);
        File demFile = getDemFileOrThrow(mapId);

        try {
            DemRaster dem = DemRaster.readFromFile(demFile);

            // Record snapshot before mutation
            historyService.recordPreMutationState(mapId, dem.getData());

            int width = dem.getWidth();
            int height = dem.getHeight();

            double minLat = map.getMinLat();
            double maxLat = map.getMaxLat();
            double minLon = map.getMinLon();
            double maxLon = map.getMaxLon();

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

            dem.writeToFile(demFile);
            return dem.getElevation(centerPxX, centerPxY);

        } catch (Exception e) {
            log.error("[TERRAIN SCULPT] Radial sculpt failed for map {}", mapId, e);
            throw new RuntimeException("Terrain sculpt execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Linear terrain sculpt (A -> B) for trenches, anti-tank ditches, and earthen berms.
     */
    public boolean sculptTerrainLine(UUID mapId, LinearSculptRequest request) {
        MapEntity map = getMapOrThrow(mapId);
        File demFile = getDemFileOrThrow(mapId);

        try {
            DemRaster dem = DemRaster.readFromFile(demFile);

            // Record snapshot for undo
            historyService.recordPreMutationState(mapId, dem.getData());

            int width = dem.getWidth();
            int height = dem.getHeight();

            double minLat = map.getMinLat();
            double maxLat = map.getMaxLat();
            double minLon = map.getMinLon();
            double maxLon = map.getMaxLon();

            double midLat = (request.startLat() + request.endLat()) / 2.0;
            double mppY = (maxLat - minLat) * METERS_PER_DEGREE / (height - 1);
            double mppX = (maxLon - minLon) * METERS_PER_DEGREE * Math.cos(Math.toRadians(midLat)) / (width - 1);

            double px1 = (request.startLon() - minLon) / (maxLon - minLon) * (width - 1);
            double py1 = (maxLat - request.startLat()) / (maxLat - minLat) * (height - 1);
            double px2 = (request.endLon() - minLon) / (maxLon - minLon) * (width - 1);
            double py2 = (maxLat - request.endLat()) / (maxLat - minLat) * (height - 1);

            double dirX = (px2 - px1) * mppX;
            double dirY = (py2 - py1) * mppY;
            double segLenMeters = Math.hypot(dirX, dirY);

            if (segLenMeters < 0.1) {
                return false;
            }

            // Ensure physical half-width is at least 1.8 grid cells to avoid sub-pixel gaps
            double minPixelSpanMeters = Math.max(mppX, mppY) * 1.8;
            double halfWidthMeters = Math.max(request.widthMeters() / 2.0, minPixelSpanMeters);

            int padPxX = (int) Math.ceil(halfWidthMeters / mppX) + 3;
            int padPxY = (int) Math.ceil(halfWidthMeters / mppY) + 3;

            int minX = Math.max(1, (int) Math.floor(Math.min(px1, px2)) - padPxX);
            int maxX = Math.min(width - 2, (int) Math.ceil(Math.max(px1, px2)) + padPxX);
            int minY = Math.max(1, (int) Math.floor(Math.min(py1, py2)) - padPxY);
            int maxY = Math.min(height - 2, (int) Math.ceil(Math.max(py1, py2)) + padPxY);

            double delta = request.deltaMeters();
            String operation = request.operation().toUpperCase();

            // 1. Calculate continuous delta field without sharp thresholding
            float[][] deltaMatrix = new float[height][width];

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    double vpxMeters = (x - px1) * mppX;
                    double vpyMeters = (y - py1) * mppY;

                    double t = (vpxMeters * dirX + vpyMeters * dirY) / (segLenMeters * segLenMeters);
                    t = Math.clamp(t, 0.0, 1.0);

                    double closestXMeters = t * dirX;
                    double closestYMeters = t * dirY;
                    double distMeters = Math.hypot(vpxMeters - closestXMeters, vpyMeters - closestYMeters);

                    if (distMeters <= halfWidthMeters) {
                        // Smooth cosine profile with C2 continuity across entire radius
                        double falloff = 0.5 * (1.0 + Math.cos(Math.PI * distMeters / halfWidthMeters));
                        deltaMatrix[y][x] = (float) (delta * falloff);
                    }
                }
            }

            // 2. Apply 3x3 Gaussian blur kernel over the delta field to eliminate raster staircase aliasing
            float[][] smoothedDelta = new float[height][width];
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (deltaMatrix[y][x] > 0.001f ||
                            deltaMatrix[y - 1][x] > 0.001f || deltaMatrix[y + 1][x] > 0.001f ||
                            deltaMatrix[y][x - 1] > 0.001f || deltaMatrix[y][x + 1] > 0.001f) {

                        float val = (
                                1.0f * deltaMatrix[y - 1][x - 1] + 2.0f * deltaMatrix[y - 1][x] + 1.0f * deltaMatrix[y - 1][x + 1] +
                                        2.0f * deltaMatrix[y][x - 1]     + 4.0f * deltaMatrix[y][x]     + 2.0f * deltaMatrix[y][x + 1] +
                                        1.0f * deltaMatrix[y + 1][x - 1] + 2.0f * deltaMatrix[y + 1][x] + 1.0f * deltaMatrix[y + 1][x + 1]
                        ) / 16.0f;

                        smoothedDelta[y][x] = val;
                    }
                }
            }

            // 3. Apply smoothed delta to DEM elevation raster
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    float d = smoothedDelta[y][x];
                    if (d > 0.0001f) {
                        float currentAlt = dem.getElevation(x, y);
                        float newAlt = switch (operation) {
                            case "TRENCH_DIG", "AT_DITCH" -> currentAlt - d;
                            case "BERM_RAISE" -> currentAlt + d;
                            default -> currentAlt;
                        };
                        dem.setElevation(x, y, newAlt);
                    }
                }
            }

            dem.writeToFile(demFile);
            log.info("[TERRAIN SCULPT] Anti-aliased linear {} applied from ({}, {}) to ({}, {}), width: {}m, delta: {}m",
                    operation, request.startLat(), request.startLon(), request.endLat(), request.endLon(),
                    request.widthMeters(), delta);

            return true;

        } catch (Exception e) {
            log.error("[TERRAIN SCULPT] Anti-aliased linear sculpt failed for map {}", mapId, e);
            throw new RuntimeException("Linear terrain sculpt execution failed: " + e.getMessage(), e);
        }
    }

    public boolean undoSculpt(UUID mapId) {
        File demFile = getDemFileOrThrow(mapId);
        try {
            DemRaster currentRaster = DemRaster.readFromFile(demFile);
            var restoredOpt = historyService.undo(mapId, currentRaster.getData());

            if (restoredOpt.isPresent()) {
                DemRaster restoredRaster = new DemRaster(currentRaster.getWidth(), currentRaster.getHeight(), restoredOpt.get());
                restoredRaster.writeToFile(demFile);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("[TERRAIN SCULPT] Undo failed for map {}", mapId, e);
            return false;
        }
    }

    public boolean redoSculpt(UUID mapId) {
        File demFile = getDemFileOrThrow(mapId);
        try {
            DemRaster currentRaster = DemRaster.readFromFile(demFile);
            var restoredOpt = historyService.redo(mapId, currentRaster.getData());

            if (restoredOpt.isPresent()) {
                DemRaster restoredRaster = new DemRaster(currentRaster.getWidth(), currentRaster.getHeight(), restoredOpt.get());
                restoredRaster.writeToFile(demFile);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("[TERRAIN SCULPT] Redo failed for map {}", mapId, e);
            return false;
        }
    }

    public Map<String, Boolean> getHistoryStatus(UUID mapId) {
        return Map.of(
                "canUndo", historyService.canUndo(mapId),
                "canRedo", historyService.canRedo(mapId)
        );
    }

    private MapEntity getMapOrThrow(UUID mapId) {
        return mapRepository.findById(mapId)
                .orElseThrow(() -> new IllegalArgumentException("Map not found: " + mapId));
    }

    private File getDemFileOrThrow(UUID mapId) {
        Path demPath = storageService.resolvePath(String.format("maps/%s/terrain.tif", mapId));
        File demFile = demPath.toFile();
        if (!demFile.exists()) {
            throw new IllegalStateException("DEM raster does not exist on disk: " + demPath);
        }
        return demFile;
    }
}