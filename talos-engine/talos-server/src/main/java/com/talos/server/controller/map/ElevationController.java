package com.talos.server.controller.map;

import com.talos.gis.core.service.MapService;
import com.talos.gis.editing.elevation.model.LinearSculptRequest;
import com.talos.gis.editing.elevation.model.RadialSculptRequest;
import com.talos.gis.editing.elevation.model.SculptResultResponse;
import com.talos.gis.editing.elevation.model.TerrainHistoryResponse;
import com.talos.gis.editing.elevation.service.TerrainHistoryService;
import com.talos.gis.editing.elevation.service.TerrainSculptService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller managing elevation models, grid sampling, radial and linear sculpting, and Undo/Redo.
 */
@RestController
@RequestMapping("/api/maps/{mapId}")
@CrossOrigin(origins = "*")
public class ElevationController {

    private final MapService mapService;
    private final TerrainSculptService sculptService;
    private final TerrainHistoryService historyService;

    public ElevationController(MapService mapService,
                               TerrainSculptService sculptService,
                               TerrainHistoryService historyService) {
        this.mapService = mapService;
        this.sculptService = sculptService;
        this.historyService = historyService;
    }

    @GetMapping(value = "/terrain.tif", produces = "image/tiff")
    public ResponseEntity<Resource> getMapElevationDem(@PathVariable UUID mapId) {
        return mapService.getDemResource(mapId)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("image/tiff"))
                        .body(resource))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/terrain/grid", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getTerrainGrid(
            @PathVariable UUID mapId,
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLon,
            @RequestParam double maxLon,
            @RequestParam(defaultValue = "64") int width,
            @RequestParam(defaultValue = "64") int height) {

        byte[] grid = mapService.sampleTerrainGrid(mapId, minLat, maxLat, minLon, maxLon, width, height);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(grid);
    }

    @PostMapping("/terrain/sculpt")
    public ResponseEntity<SculptResultResponse> sculptRadial(
            @PathVariable UUID mapId,
            @RequestBody RadialSculptRequest request) {

        double newAlt = sculptService.sculptTerrain(mapId, request);
        return ResponseEntity.ok(SculptResultResponse.success(request.operation(), newAlt));
    }

    @PostMapping("/terrain/sculpt-line")
    public ResponseEntity<SculptResultResponse> sculptLinear(
            @PathVariable UUID mapId,
            @RequestBody LinearSculptRequest request) {

        boolean success = sculptService.sculptTerrainLine(mapId, request);
        return success
                ? ResponseEntity.ok(SculptResultResponse.success(request.operation()))
                : ResponseEntity.badRequest().body(SculptResultResponse.failed(request.operation()));
    }

    @PostMapping("/terrain/undo")
    public ResponseEntity<TerrainHistoryResponse> undoSculpt(@PathVariable UUID mapId) {
        sculptService.undoSculpt(mapId);
        return ResponseEntity.ok(new TerrainHistoryResponse(
                historyService.canUndo(mapId),
                historyService.canRedo(mapId)
        ));
    }

    @PostMapping("/terrain/redo")
    public ResponseEntity<TerrainHistoryResponse> redoSculpt(@PathVariable UUID mapId) {
        sculptService.redoSculpt(mapId);
        return ResponseEntity.ok(new TerrainHistoryResponse(
                historyService.canUndo(mapId),
                historyService.canRedo(mapId)
        ));
    }

    @GetMapping("/terrain/history-status")
    public ResponseEntity<TerrainHistoryResponse> getHistoryStatus(@PathVariable UUID mapId) {
        return ResponseEntity.ok(new TerrainHistoryResponse(
                historyService.canUndo(mapId),
                historyService.canRedo(mapId)
        ));
    }
}