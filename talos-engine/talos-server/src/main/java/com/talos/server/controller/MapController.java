package com.talos.server.controller;

import com.talos.gis.dto.MapCreationRequest;
import com.talos.gis.dto.MapDetailDto;
import com.talos.gis.dto.SurfaceModifierDto;
import com.talos.gis.dto.TerrainSculptRequest;
import com.talos.gis.service.*;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller providing HTTP endpoints for map management, tile streaming,
 * elevation DEM serving, vector features, and terrain sculpting.
 */
@RestController
@RequestMapping("/api/maps")
@CrossOrigin(origins = "*")
public class MapController {

    private final MapService mapService;
    private final MapIngestionService mapIngestionService;
    private final VectorFeatureService vectorFeatureService;
    private final TerrainSculptService terrainSculptService;
    private final TemplateModifierService templateModifierService;

    public MapController(MapService mapService,
                         MapIngestionService mapIngestionService,
                         VectorFeatureService vectorFeatureService,
                         TerrainSculptService terrainSculptService,
                         TemplateModifierService templateModifierService) {
        this.mapService = mapService;
        this.mapIngestionService = mapIngestionService;
        this.vectorFeatureService = vectorFeatureService;
        this.terrainSculptService = terrainSculptService;
        this.templateModifierService = templateModifierService;
    }

    @GetMapping
    public List<MapDetailDto> getAllMaps() {
        return mapService.getAllMaps();
    }

    @GetMapping("/{mapId}")
    public ResponseEntity<MapDetailDto> getMapById(@PathVariable UUID mapId) {
        return mapService.getMapById(mapId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createMap(@RequestBody MapCreationRequest request) {
        UUID newMapId = mapIngestionService.createMap(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "mapId", newMapId,
                "status", "DOWNLOADING",
                "message", "Map ingestion pipeline started in background"
        ));
    }

    @GetMapping("/{mapId}/tiles/{layerType}/{z}/{x}/{y}.png")
    public ResponseEntity<Resource> getMapTile(
            @PathVariable UUID mapId,
            @PathVariable String layerType,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {

        return mapService.getTileResource(mapId, layerType, z, x, y)
                .map(tile -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                        .body(tile))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{mapId}/terrain.tif", produces = "image/tiff")
    public ResponseEntity<Resource> getMapElevationDem(@PathVariable UUID mapId) {
        return mapService.getDemResource(mapId)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("image/tiff"))
                        .body(resource))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{mapId}/modifiers")
    public ResponseEntity<List<SurfaceModifierDto>> getMapModifiers(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.getMapModifiers(mapId));
    }

    @PutMapping("/{mapId}/modifiers/{modifierId}")
    public ResponseEntity<Void> updateModifier(
            @PathVariable UUID mapId,
            @PathVariable UUID modifierId,
            @RequestBody SurfaceModifierDto updateDto) {

        boolean updated = mapService.updateModifier(modifierId, updateDto);
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/{mapId}/vectors", produces = "application/geo+json")
    public ResponseEntity<String> getMapVectors(@PathVariable UUID mapId) {
        String geoJson = vectorFeatureService.getTheaterVectorsGeoJson(mapId);
        return ResponseEntity.ok(geoJson);
    }

    @PostMapping("/{mapId}/terrain/sculpt")
    public ResponseEntity<Map<String, Object>> sculptTerrain(
            @PathVariable UUID mapId,
            @RequestBody TerrainSculptRequest request) {

        double newElevation = terrainSculptService.sculptTerrain(mapId, request);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "operation", request.operation(),
                "newElevationAtCenter", newElevation
        ));
    }

    @GetMapping(value = "/{mapId}/terrain/grid", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getTerrainGrid(
            @PathVariable UUID mapId,
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLon,
            @RequestParam double maxLon,
            @RequestParam(defaultValue = "32") int width,
            @RequestParam(defaultValue = "32") int height) {

        byte[] grid = mapService.sampleTerrainGrid(mapId, minLat, maxLat, minLon, maxLon, width, height);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(grid);
    }

    @DeleteMapping("/{mapId}")
    public ResponseEntity<Void> deleteMap(@PathVariable UUID mapId) {
        boolean deleted = mapService.deleteMap(mapId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{mapId}/features/{featureId}")
    public ResponseEntity<Void> updateMapFeature(
            @PathVariable UUID mapId,
            @PathVariable UUID featureId,
            @RequestBody com.talos.gis.dto.FeatureUpdateRequestDto updateDto) {

        boolean updated = vectorFeatureService.updateFeature(mapId, featureId, updateDto);
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/templates")
    public List<com.talos.gis.dto.DefaultModifierDto> getAllTemplates() {
        return templateModifierService.getAllTemplates();
    }

    @PostMapping("/templates")
    public ResponseEntity<com.talos.gis.dto.DefaultModifierDto> createTemplate(
            @RequestBody com.talos.gis.dto.DefaultModifierDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateModifierService.createTemplate(dto));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<com.talos.gis.dto.DefaultModifierDto> updateTemplate(
            @PathVariable UUID id,
            @RequestBody com.talos.gis.dto.DefaultModifierDto dto) {
        return templateModifierService.updateTemplate(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        return templateModifierService.deleteTemplate(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{mapId}/apply-template/{templateId}")
    public ResponseEntity<Void> applyTemplateToMap(
            @PathVariable UUID mapId,
            @PathVariable UUID templateId) {
        return templateModifierService.applyTemplateToMap(mapId, templateId)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }
}