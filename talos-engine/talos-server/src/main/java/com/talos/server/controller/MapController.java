package com.talos.server.controller;

import com.talos.gis.service.MapIngestionService;
import com.talos.gis.repository.MapLayerRepository;
import com.talos.gis.repository.MapRepository;
import com.talos.gis.repository.MapSurfaceModifierRepository;
import com.talos.gis.service.TerrainSculptService;
import com.talos.gis.service.VectorFeatureService;
import com.talos.model.dto.gis.MapCreationRequest;
import com.talos.model.dto.gis.MapDetailDto;
import com.talos.model.dto.gis.SurfaceModifierDto;
import com.talos.model.dto.gis.TerrainSculptRequest;
import com.talos.model.entity.MapEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller providing endpoints for map management, local tile streaming,
 * elevation DEM serving, vector features, and terrain sculpting.
 */
@RestController
@RequestMapping("/api/maps")
@CrossOrigin(origins = "*")
public class MapController {

    private static final Logger log = LoggerFactory.getLogger(MapController.class);

    // 1x1 Transparent PNG fallback for missing overview tiles (prevents 404 network spam)
    private static final byte[] EMPTY_TRANSPARENT_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08,
            0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0x00, 0x00, 0x00,
            0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49,
            0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private final MapRepository mapRepository;
    private final MapLayerRepository mapLayerRepository;
    private final MapSurfaceModifierRepository modifierRepository;
    private final MapIngestionService mapIngestionService;
    private final VectorFeatureService vectorFeatureService;
    private final TerrainSculptService terrainSculptService;

    @Value("${talos.storage.root:talos-data}")
    private String storageRoot;

    public MapController(MapRepository mapRepository,
                         MapLayerRepository mapLayerRepository,
                         MapSurfaceModifierRepository modifierRepository,
                         MapIngestionService mapIngestionService,
                         VectorFeatureService vectorFeatureService,
                         TerrainSculptService terrainSculptService) {
        this.mapRepository = mapRepository;
        this.mapLayerRepository = mapLayerRepository;
        this.modifierRepository = modifierRepository;
        this.mapIngestionService = mapIngestionService;
        this.vectorFeatureService = vectorFeatureService;
        this.terrainSculptService = terrainSculptService;
    }

    /**
     * Resolves the base storage directory reliably regardless of running from root or subfolder.
     */
    private Path getStorageBasePath() {
        Path p = Paths.get(storageRoot);
        if (Files.exists(p)) return p;

        Path parentP = Paths.get("..", storageRoot);
        if (Files.exists(parentP)) return parentP;

        Path grandParentP = Paths.get("../..", storageRoot);
        if (Files.exists(grandParentP)) return grandParentP;

        return p;
    }

    private Path resolvePath(String subPath) {
        return getStorageBasePath().resolve(subPath);
    }

    /**
     * 1. List all available maps.
     */
    @GetMapping
    public List<MapDetailDto> getAllMaps() {
        return mapRepository.findAll().stream().map(this::mapToDto).toList();
    }

    /**
     * 2. Get specific map metadata and available layers.
     */
    @GetMapping("/{mapId}")
    public ResponseEntity<MapDetailDto> getMapById(@PathVariable("mapId") UUID mapId) {
        return mapRepository.findById(mapId)
                .map(entity -> ResponseEntity.ok(mapToDto(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 3. Trigger ingestion of a new bounded theater map.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMap(@RequestBody MapCreationRequest request) {
        UUID newMapId = mapIngestionService.createMap(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "mapId", newMapId,
                "status", "DOWNLOADING",
                "message", "Map ingestion pipeline started in background"
        ));
    }

    /**
     * 4. Stream local raster basemap tiles directly from disk.
     * If the tile doesn't exist (e.g. overview zoom), returns transparent 1x1 PNG.
     */
    @GetMapping("/{mapId}/tiles/{layerType}/{z}/{x}/{y}.png")
    public ResponseEntity<Resource> getMapTile(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("layerType") String layerType,
            @PathVariable("z") int z,
            @PathVariable("x") int x,
            @PathVariable("y") int y) {

        String relativeSubPath = String.format("maps/%s/tiles/%s/%d/%d/%d.png",
                mapId, layerType.toLowerCase(), z, x, y);

        Path tilePath = resolvePath(relativeSubPath);

        if (!Files.exists(tilePath)) {
            // Return 1x1 transparent PNG with OK status so Cesium doesn't log 404 network errors
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(new ByteArrayResource(EMPTY_TRANSPARENT_PNG));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(new FileSystemResource(tilePath));
    }

    /**
     * 5. Download or stream the local elevation GeoTIFF.
     */
    @GetMapping(value = "/{mapId}/terrain.tif", produces = "image/tiff")
    public ResponseEntity<Resource> getMapElevationDem(@PathVariable("mapId") UUID mapId) {
        Path demPath = resolvePath(String.format("maps/%s/terrain.tif", mapId));

        if (!Files.exists(demPath)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/tiff"))
                .body(new FileSystemResource(demPath));
    }

    /**
     * 6. List all surface modifiers for this map.
     */
    @GetMapping("/{mapId}/modifiers")
    public ResponseEntity<List<SurfaceModifierDto>> getMapModifiers(@PathVariable("mapId") UUID mapId) {
        List<SurfaceModifierDto> dtos = modifierRepository.findAll().stream()
                .filter(mod -> mod.getMap().getId().equals(mapId))
                .map(mod -> new SurfaceModifierDto(
                        mod.getId(), mod.getCategory(), mod.getOsmKey(), mod.getOsmValue(),
                        mod.getDescription(), mod.getSpeedModifierWheeled(), mod.getSpeedModifierTracked(),
                        mod.getVisibilityMeters(), mod.getCoverDefensePercent()
                )).toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * 7. Update a surface modifier parameter.
     */
    @PutMapping("/{mapId}/modifiers/{modifierId}")
    public ResponseEntity<Void> updateModifier(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("modifierId") UUID modifierId,
            @RequestBody SurfaceModifierDto updateDto) {

        return modifierRepository.findById(modifierId)
                .map(mod -> {
                    mod.setSpeedModifierWheeled(updateDto.speedModifierWheeled());
                    mod.setSpeedModifierTracked(updateDto.speedModifierTracked());
                    mod.setVisibilityMeters(updateDto.visibilityMeters());
                    mod.setCoverDefensePercent(updateDto.coverDefensePercent());
                    modifierRepository.save(mod);
                    return ResponseEntity.noContent().<Void>build();
                }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * 8. Stream vector geometry (roads, forests, buildings).
     */
    @GetMapping(value = "/{mapId}/vectors", produces = "application/geo+json")
    public ResponseEntity<String> getMapVectors(@PathVariable("mapId") UUID mapId) {
        String geoJson = vectorFeatureService.getTheaterVectorsGeoJson(mapId);
        return ResponseEntity.ok(geoJson);
    }

    /**
     * 9. Sculpt elevation terrain raster (dig quarries, trenches, raise berms).
     */
    @PostMapping("/{mapId}/terrain/sculpt")
    public ResponseEntity<Map<String, Object>> sculptTerrain(
            @PathVariable("mapId") UUID mapId,
            @RequestBody TerrainSculptRequest request) {

        double newElevation = terrainSculptService.sculptTerrain(mapId, request);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "operation", request.operation(),
                "newElevationAtCenter", newElevation
        ));
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

    /**
     * 10. Streams raw elevation heightmap grid (32x32 floats) without AWT normalization.
     * Uses DemRaster to guarantee true altitudes (e.g. 124m, 318m).
     */
    @GetMapping(value = "/{mapId}/terrain/grid", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getTerrainGrid(
            @PathVariable("mapId") UUID mapId,
            @RequestParam("minLat") double minLat,
            @RequestParam("maxLat") double maxLat,
            @RequestParam("minLon") double minLon,
            @RequestParam("maxLon") double maxLon,
            @RequestParam(value = "width", defaultValue = "32") int width,
            @RequestParam(value = "height", defaultValue = "32") int height) {

        Path demPath = resolvePath(String.format("maps/%s/terrain.tif", mapId));
        File demFile = demPath.toFile();

        if (!demFile.exists() || demFile.length() < 256) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(createFlatBuffer(width, height, 130.0f));
        }

        try {
            // Read direct IEEE Float32 DEM bypassing ImageIO clamping
            com.talos.gis.util.DemRaster dem = com.talos.gis.util.DemRaster.readFromFile(demFile);
            MapEntity map = mapRepository.findById(mapId).orElse(null);
            if (map == null) return ResponseEntity.notFound().build();

            int imgW = dem.getWidth();
            int imgH = dem.getHeight();

            ByteBuffer buffer = ByteBuffer.allocate(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);

            for (int y = 0; y < height; y++) {
                double sampleLat = maxLat - (y / (double) (height - 1)) * (maxLat - minLat);
                double normY = (map.getMaxLat() - sampleLat) / (map.getMaxLat() - map.getMinLat());
                normY = Math.max(0.0, Math.min(1.0, normY));

                for (int x = 0; x < width; x++) {
                    // Col 0 is WEST (minLon) to col 31 EAST (maxLon)
                    double sampleLon = minLon + (x / (double) (width - 1)) * (maxLon - minLon);
                    double normX = (sampleLon - map.getMinLon()) / (map.getMaxLon() - map.getMinLon());
                    normX = Math.max(0.0, Math.min(1.0, normX));

                    // Smooth Bilinear Interpolation gives 100% seamless tile-to-tile stitching
                    float trueAlt = dem.getInterpolatedElevation(normX, normY);
                    buffer.putFloat(trueAlt);
                }
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(buffer.array());

        } catch (Exception e) {
            log.error("[TERRAIN GRID] Failed to sample elevation grid for map " + mapId, e);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(createFlatBuffer(width, height, 130.0f));
        }
    }

    private byte[] createFlatBuffer(int width, int height, float alt) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(width * height * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < width * height; i++) {
            buf.putFloat(alt);
        }
        return buf.array();
    }

    /**
     * 11. Delete a map, its database records (cascading), and completely wipe all files on disk.
     */
    @DeleteMapping("/{mapId}")
    public ResponseEntity<Void> deleteMap(@PathVariable("mapId") UUID mapId) {
        return mapRepository.findById(mapId).map(map -> {
            // 1. Physically delete map directory and all downloaded tiles/DEM from disk
            Path mapDir = resolvePath(String.format("maps/%s", mapId));
            try {
                if (Files.exists(mapDir)) {
                    org.springframework.util.FileSystemUtils.deleteRecursively(mapDir);
                    log.info("[MAP DELETE] Cleaned up disk assets for map: {}", mapDir);
                }
            } catch (Exception e) {
                log.warn("[MAP DELETE] Could not delete disk folder {}: {}", mapDir, e.getMessage());
            }

            // 2. Cascade delete database records (layers, modifiers, features, map)
            mapRepository.delete(map);
            log.info("[MAP DELETE] Successfully removed MapEntity from DB: {}", mapId);

            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}