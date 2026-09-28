package com.talos.server.controller.map;

import com.talos.gis.core.service.MapService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

/**
 * REST Controller streaming local raster basemap tiles and executing live proxy caching.
 */
@RestController
@RequestMapping("/api/maps/{mapId}/tiles")
@CrossOrigin(origins = "*")
public class BasemapController {

    private final MapService mapService;

    public BasemapController(MapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping("/{layerType}/{z}/{x}/{y}.png")
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
}