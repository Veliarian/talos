package com.talos.server.controller;

import com.talos.gis.service.MapIngestionService;
import com.talos.gis.service.VectorFeatureService;
import com.talos.gis.repository.MapLayerRepository;
import com.talos.gis.repository.MapRepository;
import com.talos.gis.repository.MapSurfaceModifierRepository;
import com.talos.model.dto.gis.MapCreationRequest;
import com.talos.model.dto.gis.MapDetailDto;
import com.talos.model.dto.gis.SurfaceModifierDto;
import com.talos.model.entity.MapEntity;
import com.talos.model.entity.MapSurfaceModifierEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller providing endpoints for map management, local tile streaming,
 * elevation DEM serving, and terrain surface modifier adjustments.
 */
@RestController
@RequestMapping("/api/maps")
@CrossOrigin(origins = "*") // Allows communication with Vue 3 / Cesium client
public class MapController {

    private static final Logger log = LoggerFactory.getLogger(MapController.class);

    private final MapRepository mapRepository;
    private final MapLayerRepository mapLayerRepository;
    private final MapSurfaceModifierRepository modifierRepository;
    private final MapIngestionService mapIngestionService;
    private final VectorFeatureService vectorFeatureService;

    @Value("${talos.storage.root:talos-data}")
    private String storageRoot;

    public MapController(MapRepository mapRepository,
                         MapLayerRepository mapLayerRepository,
                         MapSurfaceModifierRepository modifierRepository,
                         MapIngestionService mapIngestionService,
                         VectorFeatureService vectorFeatureService) {
        this.mapRepository = mapRepository;
        this.mapLayerRepository = mapLayerRepository;
        this.modifierRepository = modifierRepository;
        this.mapIngestionService = mapIngestionService;
        this.vectorFeatureService = vectorFeatureService;
    }

    /**
     * Resolves storage path reliably across different launch directories.
     */
    private Path resolvePath(String subPath) {
        Path directPath = Paths.get(storageRoot, subPath);
        if (Files.exists(directPath.getParent())) {
            return directPath;
        }
        // Fallback if launched from subproject folder
        Path parentRelative = Paths.get("../", storageRoot, subPath);
        if (Files.exists(parentRelative.getParent())) {
            return parentRelative;
        }
        return Paths.get("../../", storageRoot, subPath);
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
     * Completely offline - serves cached tiles to CesiumJS.
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
            return ResponseEntity.notFound().build();
        }

        Resource tileResource = new FileSystemResource(tilePath);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(tileResource);
    }

    /**
     * 5. Download or stream the local elevation GeoTIFF for this map.
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
     * 6. List all surface modifiers (roads, vegetation, cover) for this map.
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
     * 7. Update a surface modifier parameter (speed, visibility, cover defense).
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

    @GetMapping(value = "/{mapId}/vectors", produces = "application/geo+json")
    public ResponseEntity<String> getMapVectors(@PathVariable("mapId") UUID mapId) {
        String geoJson = vectorFeatureService.getTheaterVectorsGeoJson(mapId);
        return ResponseEntity.ok(geoJson);
    }
}