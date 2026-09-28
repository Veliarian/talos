package com.talos.server.controller.map;

import com.talos.gis.core.service.MapService;
import com.talos.gis.creation.model.MapCreateRequest;
import com.talos.gis.creation.model.MapCreateResponse;
import com.talos.gis.creation.service.MapCreationService;
import com.talos.gis.editing.basemap.model.MapDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller managing operational theater map lifecycle (cataloging, creation, deletion).
 */
@RestController
@RequestMapping("/api/maps")
@CrossOrigin(origins = "*")
public class MapCatalogController {

    private final MapService mapService;
    private final MapCreationService mapCreationService;

    public MapCatalogController(MapService mapService, MapCreationService mapCreationService) {
        this.mapService = mapService;
        this.mapCreationService = mapCreationService;
    }

    @GetMapping
    public List<MapDetailResponse> getAllMaps() {
        return mapService.getAllMaps();
    }

    @GetMapping("/{mapId}")
    public ResponseEntity<MapDetailResponse> getMapById(@PathVariable UUID mapId) {
        return mapService.getMapById(mapId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MapCreateResponse> createMap(@RequestBody MapCreateRequest request) {
        UUID newMapId = mapCreationService.createMap(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(MapCreateResponse.initiated(newMapId));
    }

    @DeleteMapping("/{mapId}")
    public ResponseEntity<Void> deleteMap(@PathVariable UUID mapId) {
        boolean deleted = mapService.deleteMap(mapId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}