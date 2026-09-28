package com.talos.gis.core.service;

import com.talos.gis.core.entity.MapEntity;
import com.talos.gis.core.repository.MapRepository;
import com.talos.gis.creation.service.TileDownloadService;
import com.talos.gis.editing.basemap.model.MapDetailResponse;
import com.talos.gis.editing.basemap.model.MapLayerData;
import com.talos.gis.editing.elevation.util.DemRaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core domain service managing operational theater lifecycle, storage asset resolution,
 * elevation raster sampling, and high-performance live tile proxy caching.
 */
@Service
public class MapService {

    private static final Logger log = LoggerFactory.getLogger(MapService.class);

    private final MapRepository mapRepository;
    private final GisStorageService storageService;

    private final HttpClient tileHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public MapService(MapRepository mapRepository, GisStorageService storageService) {
        this.mapRepository = mapRepository;
        this.storageService = storageService;
    }

    public Path resolveStoragePath(String subPath) {
        return storageService.resolvePath(subPath);
    }

    @Transactional(readOnly = true)
    public List<MapDetailResponse> getAllMaps() {
        return mapRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<MapDetailResponse> getMapById(UUID mapId) {
        return mapRepository.findById(mapId).map(this::mapToResponse);
    }

    /**
     * Resolves tile resource: returns local disk file if present, or dynamically fetches
     * from upstream HD provider (Google Satellite HD), caches to disk, and returns it.
     */
    public Optional<Resource> getTileResource(UUID mapId, String layerType, int z, int x, int y) {
        String subPath = String.format("maps/%s/tiles/%s/%d/%d/%d.png",
                mapId, layerType.toLowerCase(), z, x, y);

        Path tilePath = resolveStoragePath(subPath);

        // 1. Fast local hit: Tile already cached locally
        if (Files.exists(tilePath)) {
            return Optional.of(new FileSystemResource(tilePath));
        }

        // 2. Live proxy cache: Fetch on the fly from upstream HD provider
        String upperLayer = layerType.toUpperCase();
        String urlTemplate = TileDownloadService.LAYER_URL_TEMPLATES.get(upperLayer);

        if (urlTemplate == null || z > 19) {
            return Optional.empty();
        }

        String requestUrl = urlTemplate
                .replace("{z}", String.valueOf(z))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = tileHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200 && response.body().length > 0) {
                Files.createDirectories(tilePath.getParent());
                Path tempFile = tilePath.resolveSibling(tilePath.getFileName() + ".tmp");
                Files.write(tempFile, response.body());
                Files.move(tempFile, tilePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

                return Optional.of(new FileSystemResource(tilePath));
            }
        } catch (Exception e) {
            log.debug("[TILE PROXY] Live fetch failed for {}/{}/{}/{}: {}", layerType, z, x, y, e.getMessage());
        }

        return Optional.empty();
    }

    public Optional<Resource> getDemResource(UUID mapId) {
        Path demPath = resolveStoragePath(String.format("maps/%s/terrain.tif", mapId));
        if (!Files.exists(demPath)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(demPath));
    }

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

    public boolean deleteMap(UUID mapId) {
        boolean dbDeleted = deleteMapFromDatabase(mapId);
        if (!dbDeleted) return false;

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

    private MapDetailResponse mapToResponse(MapEntity entity) {
        var layerDataList = entity.getLayers().stream()
                .map(layer -> new MapLayerData(
                        layer.getId(), layer.getLayerType(), layer.getMinZoom(),
                        layer.getMaxZoom(), layer.isDefault(), layer.getStatus()
                )).toList();

        return new MapDetailResponse(
                entity.getId(), entity.getName(), entity.getDescription(),
                entity.getMinLat(), entity.getMaxLat(), entity.getMinLon(), entity.getMaxLon(),
                entity.getCenterLat(), entity.getCenterLon(), entity.getSizeKm(),
                entity.getStatus(), entity.getCreatedAt(), layerDataList
        );
    }
}