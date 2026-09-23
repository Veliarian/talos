package com.talos.gis.service.impl;

import com.talos.gis.dto.MapDetailDto;
import com.talos.gis.dto.SurfaceModifierDto;
import com.talos.gis.entity.MapEntity;
import com.talos.gis.repository.MapRepository;
import com.talos.gis.repository.MapSurfaceModifierRepository;
import com.talos.gis.service.GisStorageService;
import com.talos.gis.service.MapService;
import com.talos.gis.util.DemRaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of MapService managing theater map lifecycle and asset streaming.
 */
@Service
public class MapServiceImpl implements MapService {

    private static final Logger log = LoggerFactory.getLogger(MapServiceImpl.class);

    // 1x1 Transparent PNG fallback for missing overview tiles (prevents 404 console spam)
    private static final byte[] EMPTY_TRANSPARENT_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08,
            0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0x00, 0x00, 0x00,
            0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49,
            0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private final MapRepository mapRepository;
    private final MapSurfaceModifierRepository modifierRepository;
    private final GisStorageService storageService;

    public MapServiceImpl(MapRepository mapRepository,
                          MapSurfaceModifierRepository modifierRepository,
                          GisStorageService storageService) {
        this.mapRepository = mapRepository;
        this.modifierRepository = modifierRepository;
        this.storageService = storageService;
    }

    @Override
    public Path resolveStoragePath(String subPath) {
        return storageService.resolvePath(subPath);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapDetailDto> getAllMaps() {
        return mapRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MapDetailDto> getMapById(UUID mapId) {
        return mapRepository.findById(mapId).map(this::mapToDto);
    }

    private final HttpClient tileHttpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(6))
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public Optional<Resource> getTileResource(UUID mapId, String layerType, int z, int x, int y) {
        String subPath = String.format("maps/%s/tiles/%s/%d/%d/%d.png",
                mapId, layerType.toLowerCase(), z, x, y);

        Path tilePath = resolveStoragePath(subPath);

        // 1. FAST LOCAL HIT: Tile already cached on disk (100% offline)
        if (Files.exists(tilePath)) {
            return Optional.of(new FileSystemResource(tilePath));
        }

        // 2. LIVE PROXY CACHE: Fetch on the fly from upstream HD provider and persist to disk
        String upperLayer = layerType.toUpperCase();
        String urlTemplate = com.talos.gis.service.TileDownloaderService.LAYER_URL_TEMPLATES.get(upperLayer);

        if (urlTemplate == null || z > 19) {
            return Optional.empty();
        }

        String requestUrl = urlTemplate
                .replace("{z}", String.valueOf(z))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));

        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(requestUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET()
                    .build();

            java.net.http.HttpResponse<byte[]> response = tileHttpClient.send(
                    request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200 && response.body().length > 0) {
                // Thread-safe atomic write to disk storage
                Files.createDirectories(tilePath.getParent());
                Path tempFile = tilePath.resolveSibling(tilePath.getFileName() + ".tmp");
                Files.write(tempFile, response.body());
                Files.move(tempFile, tilePath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                return Optional.of(new FileSystemResource(tilePath));
            }
        } catch (Exception e) {
            log.debug("[TILE PROXY] Live fetch failed for {}/{}/{}/{}: {}", layerType, z, x, y, e.getMessage());
        }

        // 3. OFFLINE FALLBACK: Return 404 so Cesium gracefully upscales the nearest parent zoom
        return Optional.empty();
    }

    @Override
    public Optional<Resource> getDemResource(UUID mapId) {
        Path demPath = resolveStoragePath(String.format("maps/%s/terrain.tif", mapId));
        if (!Files.exists(demPath)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(demPath));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SurfaceModifierDto> getMapModifiers(UUID mapId) {
        return modifierRepository.findAllByMapId(mapId).stream()
                .map(mod -> new SurfaceModifierDto(
                        mod.getId(), mod.getCategory(), mod.getOsmKey(), mod.getOsmValue(),
                        mod.getDescription(), mod.getSpeedModifierWheeled(), mod.getSpeedModifierTracked(),
                        mod.getVisibilityMeters(), mod.getCoverDefensePercent()
                )).toList();
    }

    @Override
    @Transactional
    public boolean updateModifier(UUID modifierId, SurfaceModifierDto updateDto) {
        return modifierRepository.findById(modifierId).map(mod -> {
            mod.setSpeedModifierWheeled(updateDto.speedModifierWheeled());
            mod.setSpeedModifierTracked(updateDto.speedModifierTracked());
            mod.setVisibilityMeters(updateDto.visibilityMeters());
            mod.setCoverDefensePercent(updateDto.coverDefensePercent());
            modifierRepository.save(mod);
            return true;
        }).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] sampleTerrainGrid(UUID mapId, double minLat, double maxLat,
                                    double minLon, double maxLon, int width, int height) {
        Path demPath = resolveStoragePath(String.format("maps/%s/terrain.tif", mapId));
        File demFile = demPath.toFile();

        if (!demFile.exists() || demFile.length() < 256) {
            return createFlatBuffer(width, height, 130.0f);
        }

        try {
            DemRaster dem = DemRaster.readFromFile(demFile);
            MapEntity map = mapRepository.findById(mapId).orElse(null);
            if (map == null) return createFlatBuffer(width, height, 130.0f);

            ByteBuffer buffer = ByteBuffer.allocate(width * height * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);

            for (int y = 0; y < height; y++) {
                double sampleLat = maxLat - (y / (double) (height - 1)) * (maxLat - minLat);
                double normY = (map.getMaxLat() - sampleLat) / (map.getMaxLat() - map.getMinLat());
                normY = Math.clamp(normY, 0.0, 1.0);

                for (int x = 0; x < width; x++) {
                    double sampleLon = minLon + (x / (double) (width - 1)) * (maxLon - minLon);
                    double normX = (sampleLon - map.getMinLon()) / (map.getMaxLon() - map.getMinLon());
                    normX = Math.clamp(normX, 0.0, 1.0);

                    float trueAlt = dem.getInterpolatedElevation(normX, normY);
                    buffer.putFloat(trueAlt);
                }
            }
            return buffer.array();
        } catch (Exception e) {
            log.error("[TERRAIN GRID] Failed to sample elevation grid for map {}", mapId, e);
            return createFlatBuffer(width, height, 130.0f);
        }
    }

    @Override
    public boolean deleteMap(UUID mapId) {
        // Execute database deletion within transactional boundary
        boolean dbDeleted = deleteMapFromDatabase(mapId);
        if (!dbDeleted) {
            return false;
        }

        // Physically delete disk directory only after DB records are removed
        Path mapDir = resolveStoragePath(String.format("maps/%s", mapId));
        try {
            if (Files.exists(mapDir)) {
                FileSystemUtils.deleteRecursively(mapDir);
                log.info("[MAP DELETE] Cleaned up disk assets for map: {}", mapDir);
            }
        } catch (Exception e) {
            log.warn("[MAP DELETE] Could not delete disk folder {}: {}", mapDir, e.getMessage());
        }
        return true;
    }

    @Transactional
    protected boolean deleteMapFromDatabase(UUID mapId) {
        return mapRepository.findById(mapId).map(map -> {
            mapRepository.delete(map);
            log.info("[MAP DELETE] Successfully removed MapEntity from DB: {}", mapId);
            return true;
        }).orElse(false);
    }

    private byte[] createFlatBuffer(int width, int height, float alt) {
        ByteBuffer buf = ByteBuffer.allocate(width * height * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < width * height; i++) {
            buf.putFloat(alt);
        }
        return buf.array();
    }

    private MapDetailDto mapToDto(MapEntity entity) {
        var layerDtos = entity.getLayers().stream()
                .map(layer -> new MapDetailDto.MapLayerDto(
                        layer.getId(), layer.getLayerType(), layer.getMinZoom(),
                        layer.getMaxZoom(), layer.isDefault(), layer.getStatus()
                )).toList();

        return new MapDetailDto(
                entity.getId(), entity.getName(), entity.getDescription(),
                entity.getMinLat(), entity.getMaxLat(), entity.getMinLon(), entity.getMaxLon(),
                entity.getCenterLat(), entity.getCenterLon(), entity.getSizeKm(),
                entity.getStatus(), entity.getCreatedAt(), layerDtos
        );
    }
}