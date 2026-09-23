package com.talos.gis.service;

import com.talos.gis.util.DemRaster;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * High-performance terrain elevation service holding active theater DEM heightmap in memory.
 * Provides microsecond-latency elevation lookups and optical Line-of-Sight (LOS) raymarching.
 */
@Service
public class TerrainService {

    private static final Logger log = LoggerFactory.getLogger(TerrainService.class);

    private static final double METERS_PER_LAT_DEGREE = 111132.0;
    private static final double METERS_PER_LON_DEGREE = 71500.0;
    private static final double LOS_STEP_METERS = 25.0;
    private static final float DEFAULT_SEA_LEVEL_ALTITUDE = 130.0f;

    @Value("${talos.bbox.minLat:49.95}")
    private double minLat;

    @Value("${talos.bbox.maxLat:50.05}")
    private double maxLat;

    @Value("${talos.bbox.minLon:23.50}")
    private double minLon;

    @Value("${talos.bbox.maxLon:23.70}")
    private double maxLon;

    private float[][] heightMap;
    private int imgWidth = 0;
    private int imgHeight = 0;

    private final GisStorageService storageService;

    public TerrainService(GisStorageService storageService) {
        this.storageService = storageService;
    }

    @PostConstruct
    public void loadTerrain() {
        // Attempt to load baseline or default active theater terrain from disk storage
        Path diskDemPath = storageService.resolvePath("maps/default/terrain.tif");

        // Alternative check in local demo directory
        if (!Files.exists(diskDemPath)) {
            diskDemPath = storageService.resolvePath("gis/terrain.tif");
        }

        if (Files.exists(diskDemPath)) {
            loadFromDisk(diskDemPath.toFile());
        } else {
            log.warn("[TERRAIN SERVICE] No local terrain.tif detected at {}. Initializing baseline synthetic elevation.",
                    diskDemPath);
            initFallbackFlatTerrain(250.0f);
        }
    }

    /**
     * Loads a specific Float32 GeoTIFF into active memory.
     */
    public synchronized void loadFromDisk(File demFile) {
        try {
            DemRaster raster = DemRaster.readFromFile(demFile);
            this.imgWidth = raster.getWidth();
            this.imgHeight = raster.getHeight();
            this.heightMap = new float[imgHeight][imgWidth];

            for (int y = 0; y < imgHeight; y++) {
                for (int x = 0; x < imgWidth; x++) {
                    this.heightMap[y][x] = raster.getElevation(x, y);
                }
            }
            log.info("[TERRAIN SERVICE] Successfully loaded DEM raster into RAM: {}x{} matrix from {}",
                    imgWidth, imgHeight, demFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("[TERRAIN SERVICE] Failed to parse DEM GeoTIFF from disk, using fallback elevation", e);
            initFallbackFlatTerrain(DEFAULT_SEA_LEVEL_ALTITUDE);
        }
    }

    private void initFallbackFlatTerrain(float baselineElevation) {
        this.imgWidth = 256;
        this.imgHeight = 256;
        this.heightMap = new float[imgHeight][imgWidth];
        for (int y = 0; y < imgHeight; y++) {
            for (int x = 0; x < imgWidth; x++) {
                this.heightMap[y][x] = baselineElevation;
            }
        }
    }

    /**
     * Samples terrain elevation at given coordinates using smooth bilinear interpolation.
     */
    public double getElevation(double lat, double lon) {
        if (heightMap == null || imgWidth < 2 || imgHeight < 2) {
            return DEFAULT_SEA_LEVEL_ALTITUDE;
        }

        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) {
            return DEFAULT_SEA_LEVEL_ALTITUDE;
        }

        // Normalized 0.0 to 1.0 coordinates
        double normX = (lon - minLon) / (maxLon - minLon);
        double normY = (maxLat - lat) / (maxLat - minLat); // Inverted Y (North to South)

        normX = Math.clamp(normX, 0.0, 1.0);
        normY = Math.clamp(normY, 0.0, 1.0);

        double pixelX = normX * (imgWidth - 1);
        double pixelY = normY * (imgHeight - 1);

        int x0 = (int) Math.floor(pixelX);
        int x1 = Math.min(x0 + 1, imgWidth - 1);
        int y0 = (int) Math.floor(pixelY);
        int y1 = Math.min(y0 + 1, imgHeight - 1);

        double dx = pixelX - x0;
        double dy = pixelY - y0;

        float h00 = heightMap[y0][x0];
        float h10 = heightMap[y0][x1];
        float h01 = heightMap[y1][x0];
        float h11 = heightMap[y1][x1];

        // Bilinear interpolation
        double hTop = h00 * (1.0 - dx) + h10 * dx;
        double hBottom = h01 * (1.0 - dx) + h11 * dx;

        return hTop * (1.0 - dy) + hBottom * dy;
    }

    /**
     * Determines optical Line of Sight (LOS) between two tactical points.
     * Uses numerical raymarching along elevation matrix.
     */
    public boolean hasLineOfSight(double lat1, double lon1, double alt1, double eyeH1,
                                  double lat2, double lon2, double alt2, double targetH2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        double dy = dLat * METERS_PER_LAT_DEGREE;
        double dx = dLon * METERS_PER_LON_DEGREE;
        double distance = Math.hypot(dx, dy);

        // Point-blank range always has clear visibility
        if (distance < 5.0) {
            return true;
        }

        // Number of raycast steps (stride of ~25 meters)
        int steps = (int) Math.max(5, distance / LOS_STEP_METERS);
        double startRayAlt = alt1 + eyeH1;
        double endRayAlt = alt2 + targetH2;

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double sampleLat = lat1 + dLat * t;
            double sampleLon = lon1 + dLon * t;

            // Optical ray trajectory height at sample position
            double rayAltitude = startRayAlt + (endRayAlt - startRayAlt) * t;

            // Sample ground terrain height
            double groundAltitude = getElevation(sampleLat, sampleLon);

            // Obstruction detected: hill/terrain intersects optical ray
            if (groundAltitude > rayAltitude) {
                return false;
            }
        }
        return true;
    }
}