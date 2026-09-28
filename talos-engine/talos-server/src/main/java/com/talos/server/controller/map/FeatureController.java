package com.talos.server.controller.map;

import com.talos.gis.editing.features.model.FeaturePatchRequest;
import com.talos.gis.editing.features.model.SurfaceTemplateRequest;
import com.talos.gis.editing.features.model.SurfaceTemplateResponse;
import com.talos.gis.editing.features.service.TemplateModifierService;
import com.talos.gis.editing.features.service.VectorFeatureService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller managing theater vector features, instance TTX overrides,
 * and the global doctrine surface template library.
 */
@RestController
@RequestMapping("/api/maps")
@CrossOrigin(origins = "*")
public class FeatureController {

    private final VectorFeatureService vectorService;
    private final TemplateModifierService templateService;

    public FeatureController(VectorFeatureService vectorService,
                             TemplateModifierService templateService) {
        this.vectorService = vectorService;
        this.templateService = templateService;
    }

    // 1. Vector features of a specific map
    @GetMapping(value = "/{mapId}/vectors", produces = "application/geo+json")
    public ResponseEntity<String> getMapVectors(@PathVariable UUID mapId) {
        return ResponseEntity.ok(vectorService.getTheaterVectorsGeoJson(mapId));
    }

    @PutMapping("/{mapId}/features/{featureId}")
    public ResponseEntity<Void> updateFeature(
            @PathVariable UUID mapId,
            @PathVariable UUID featureId,
            @RequestBody FeaturePatchRequest request) {

        boolean updated = vectorService.updateFeature(mapId, featureId, request);
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{mapId}/apply-template/{templateId}")
    public ResponseEntity<Void> applyTemplateToMap(
            @PathVariable UUID mapId,
            @PathVariable UUID templateId) {

        boolean applied = templateService.applyTemplateToMap(mapId, templateId);
        return applied ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // 2. Global doctrine templates library
    @GetMapping("/templates")
    public List<SurfaceTemplateResponse> getAllTemplates() {
        return templateService.getAllTemplates();
    }

    @PostMapping("/templates")
    public ResponseEntity<SurfaceTemplateResponse> createTemplate(@RequestBody SurfaceTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(request));
    }

    @PutMapping("/templates/{templateId}")
    public ResponseEntity<SurfaceTemplateResponse> updateTemplate(
            @PathVariable UUID templateId,
            @RequestBody SurfaceTemplateRequest request) {

        return templateService.updateTemplate(templateId, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        boolean deleted = templateService.deleteTemplate(templateId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{mapId}/modifiers")
    public ResponseEntity<List<com.talos.gis.editing.features.model.TacticalModifierData>> getMapModifiers(
            @PathVariable UUID mapId) {
        return ResponseEntity.ok(templateService.getMapModifiers(mapId));
    }

    @PutMapping("/{mapId}/modifiers/{modifierId}")
    public ResponseEntity<Void> updateModifier(
            @PathVariable UUID mapId,
            @PathVariable UUID modifierId,
            @RequestBody com.talos.gis.editing.features.model.TacticalModifierData updateData) {

        boolean updated = templateService.updateMapModifier(modifierId, updateData);
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}