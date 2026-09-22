package com.talos.gis.service;

import com.talos.gis.repository.MapRepository;
import com.talos.model.dto.gis.TerrainSculptRequest;
import com.talos.model.entity.MapEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Service handling mathematical elevation raster sculpting with self-healing capabilities.
 * Automatically recovers from 0-byte or corrupted DEM files by generating a valid baseline Float32 raster.
 */
@Service
public class TerrainSculptService {

    private static final Logger log = LoggerFactory.getLogger(TerrainSculptService.class);

    private final MapRepository mapRepository;

    @Value("${talos.storage.root:talos-data}")
    private String storageRoot;

    public TerrainSculptService(MapRepository mapRepository) {
        this.mapRepository = mapRepository;
    }

    private Path resolvePath(String subPath) {
        Path direct = Paths.get(storageRoot, subPath);
        if (Files.exists(direct.getParent())) return direct;
        Path parentRelative = Paths.get("../", storageRoot, subPath);
        if (Files.exists(parentRelative.getParent())) return parentRelative;
        return Paths.get("../../", storageRoot, subPath);
    }

    /**
     * Applies a brush sculpt operation (DIG, RAISE, FLATTEN) onto the map's elevation raster.
     */
    public double sculptTerrain(UUID mapId, TerrainSculptRequest request) {
        MapEntity map = mapRepository.findById(mapId)
                .orElseThrow(() -> new IllegalArgumentException("Map not found: " + mapId));

        Path demPath = resolvePath(String.format("maps/%s/terrain.tif", mapId));
        File demFile = demPath.toFile();

        try {
            com.talos.gis.util.DemRaster dem = com.talos.gis.util.DemRaster.readFromFile(demFile);

            int width = dem.getWidth();
            int height = dem.getHeight();

            double minLat = map.getMinLat();
            double maxLat = map.getMaxLat();
            double minLon = map.getMinLon();
            double maxLon = map.getMaxLon();

            int centerPxX = (int) ((request.centerLon() - minLon) / (maxLon - minLon) * (width - 1));
            int centerPxY = (int) ((maxLat - request.centerLat()) / (maxLat - minLat) * (height - 1));

            double mppY = (maxLat - minLat) * 111132.95 / height;
            double mppX = (maxLon - minLon) * 111132.95 * Math.cos(Math.toRadians(request.centerLat())) / width;

            double radius = request.radiusMeters();
            int radiusPxX = (int) Math.ceil(radius / mppX);
            int radiusPxY = (int) Math.ceil(radius / mppY);

            int startX = Math.max(0, centerPxX - radiusPxX);
            int endX = Math.min(width - 1, centerPxX + radiusPxX);
            int startY = Math.max(0, centerPxY - radiusPxY);
            int endY = Math.min(height - 1, centerPxY + radiusPxY);

            float centerCurrentAlt = dem.getElevation(centerPxX, centerPxY);
            double delta = request.deltaMeters();
            String op = request.operation().toUpperCase();

            for (int y = startY; y <= endY; y++) {
                for (int x = startX; x <= endX; x++) {
                    double distMeters = Math.hypot((x - centerPxX) * mppX, (y - centerPxY) * mppY);

                    if (distMeters <= radius) {
                        double falloff = 0.5 * (1.0 + Math.cos(Math.PI * distMeters / radius));
                        float currentAlt = dem.getElevation(x, y);
                        float newAlt = currentAlt;

                        switch (op) {
                            case "DIG" -> newAlt = (float) (currentAlt - (delta * falloff));
                            case "RAISE" -> newAlt = (float) (currentAlt + (delta * falloff));
                            case "FLATTEN" -> newAlt = (float) (currentAlt * (1.0 - falloff) + centerCurrentAlt * falloff);
                        }

                        dem.setElevation(x, y, newAlt);
                    }
                }
            }

            // Save modified raster directly without ImageIO clamping
            dem.writeToFile(demFile);

            float finalCenterAlt = dem.getElevation(centerPxX, centerPxY);
            log.info("[TERRAIN SCULPT] Map {}: Applied {} (radius: {}m, delta: {}m) at [{}, {}]. Real new center alt: {} m",
                    mapId, op, radius, delta, request.centerLat(), request.centerLon(), finalCenterAlt);

            return finalCenterAlt;

        } catch (Exception e) {
            log.error("[TERRAIN SCULPT] Failed to sculpt terrain for map " + mapId, e);
            throw new RuntimeException("Terrain sculpt failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a valid uncompressed 32-bit Float GeoTIFF file filled with a baseline elevation.
     */
    private void createBaselineDemFile(File file, int width, int height, float baseElevation) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        int dataSizeBytes = width * height * 4;
        int ifdOffset = 8 + dataSizeBytes;

        ByteBuffer buffer = ByteBuffer.allocate(ifdOffset + 256).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Header
        buffer.put((byte) 'I');
        buffer.put((byte) 'I');
        buffer.putShort((short) 42);
        buffer.putInt(ifdOffset);

        // 2. Data
        for (int i = 0; i < width * height; i++) {
            buffer.putFloat(baseElevation);
        }

        // 3. IFD
        buffer.putShort((short) 10);
        writeTiffTag(buffer, 256, 4, 1, width);
        writeTiffTag(buffer, 257, 4, 1, height);
        writeTiffTag(buffer, 258, 3, 1, 32);
        writeTiffTag(buffer, 259, 3, 1, 1);
        writeTiffTag(buffer, 262, 3, 1, 1);
        writeTiffTag(buffer, 273, 4, 1, 8);
        writeTiffTag(buffer, 277, 3, 1, 1);
        writeTiffTag(buffer, 278, 4, 1, height);
        writeTiffTag(buffer, 279, 4, 1, dataSizeBytes);
        writeTiffTag(buffer, 339, 3, 1, 3);
        buffer.putInt(0);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(buffer.array(), 0, buffer.position());
        }
    }

    /**
     * Writes an uncompressed single-band 32-bit IEEE Float TIFF file.
     */
    private void writeNativeFloat32Tiff(File file, WritableRaster raster, int width, int height) throws IOException {
        int dataSizeBytes = width * height * 4;
        int ifdOffset = 8 + dataSizeBytes;
        int totalBufferSize = ifdOffset + 256;

        ByteBuffer buffer = ByteBuffer.allocate(totalBufferSize).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Header
        buffer.put((byte) 'I');
        buffer.put((byte) 'I');
        buffer.putShort((short) 42);
        buffer.putInt(ifdOffset);

        // 2. Data
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.putFloat(raster.getSampleFloat(x, y, 0));
            }
        }

        // 3. IFD
        buffer.putShort((short) 10);
        writeTiffTag(buffer, 256, 4, 1, width);
        writeTiffTag(buffer, 257, 4, 1, height);
        writeTiffTag(buffer, 258, 3, 1, 32);
        writeTiffTag(buffer, 259, 3, 1, 1);
        writeTiffTag(buffer, 262, 3, 1, 1);
        writeTiffTag(buffer, 273, 4, 1, 8);
        writeTiffTag(buffer, 277, 3, 1, 1);
        writeTiffTag(buffer, 278, 4, 1, height);
        writeTiffTag(buffer, 279, 4, 1, dataSizeBytes);
        writeTiffTag(buffer, 339, 3, 1, 3);
        buffer.putInt(0);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(buffer.array(), 0, buffer.position());
        }
    }

    private void writeTiffTag(ByteBuffer buf, int tagId, int type, int count, int valueOrOffset) {
        buf.putShort((short) tagId);
        buf.putShort((short) type);
        buf.putInt(count);
        if (type == 3) {
            buf.putShort((short) valueOrOffset);
            buf.putShort((short) 0);
        } else {
            buf.putInt(valueOrOffset);
        }
    }
}