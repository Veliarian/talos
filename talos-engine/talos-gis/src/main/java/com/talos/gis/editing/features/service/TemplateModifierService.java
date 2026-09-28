package com.talos.gis.editing.features.service;

import com.talos.gis.core.entity.MapEntity;
import com.talos.gis.core.repository.MapRepository;
import com.talos.gis.editing.features.entity.DefaultSurfaceModifierEntity;
import com.talos.gis.editing.features.entity.MapFeatureEntity;
import com.talos.gis.editing.features.entity.MapSurfaceModifierEntity;
import com.talos.gis.editing.features.model.SurfaceTemplateRequest;
import com.talos.gis.editing.features.model.SurfaceTemplateResponse;
import com.talos.gis.editing.features.model.TacticalModifierData;
import com.talos.gis.editing.features.repository.DefaultSurfaceModifierRepository;
import com.talos.gis.editing.features.repository.MapFeatureRepository;
import com.talos.gis.editing.features.repository.MapSurfaceModifierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TemplateModifierService {

    private static final Logger log = LoggerFactory.getLogger(TemplateModifierService.class);

    private final DefaultSurfaceModifierRepository defaultRepository;
    private final MapSurfaceModifierRepository mapModifierRepository;
    private final MapFeatureRepository featureRepository;
    private final MapRepository mapRepository;

    public TemplateModifierService(DefaultSurfaceModifierRepository defaultRepository,
                                   MapSurfaceModifierRepository mapModifierRepository,
                                   MapFeatureRepository featureRepository,
                                   MapRepository mapRepository) {
        this.defaultRepository = defaultRepository;
        this.mapModifierRepository = mapModifierRepository;
        this.featureRepository = featureRepository;
        this.mapRepository = mapRepository;
    }

    /**
     * Retrieves all templates sorted by priority descending (highest priority first).
     */
    @Transactional(readOnly = true)
    public List<SurfaceTemplateResponse> getAllTemplates() {
        return defaultRepository.findAll().stream()
                .sorted(Comparator.comparingInt(DefaultSurfaceModifierEntity::getMovementPriority).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SurfaceTemplateResponse createTemplate(SurfaceTemplateRequest request) {
        DefaultSurfaceModifierEntity entity = new DefaultSurfaceModifierEntity();
        copyProperties(request, entity);
        DefaultSurfaceModifierEntity saved = defaultRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public Optional<SurfaceTemplateResponse> updateTemplate(UUID id, SurfaceTemplateRequest request) {
        return defaultRepository.findById(id).map(entity -> {
            copyProperties(request, entity);
            return toResponse(defaultRepository.save(entity));
        });
    }

    @Transactional
    public boolean deleteTemplate(UUID id) {
        if (defaultRepository.existsById(id)) {
            defaultRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Reorders template priorities based on ordered list of IDs (index 0 is highest priority).
     */
    @Transactional
    public void reorderTemplates(List<UUID> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return;

        int total = orderedIds.size();
        for (int i = 0; i < total; i++) {
            UUID id = orderedIds.get(i);
            int calculatedPriority = (total - i) * 10; // e.g. 100, 90, 80...
            defaultRepository.findById(id).ifPresent(entity -> {
                entity.setMovementPriority(calculatedPriority);
                defaultRepository.save(entity);
            });
        }
        log.info("[TEMPLATE SERVICE] Successfully reordered {} templates in layer stack", total);
    }

    /**
     * Promotes an object type discovered on a specific map into the Master Doctrine Catalog.
     */
    @Transactional
    public SurfaceTemplateResponse promoteMapModifierToTemplate(UUID mapId, UUID modifierId) {
        MapSurfaceModifierEntity mod = mapModifierRepository.findById(modifierId)
                .orElseThrow(() -> new IllegalArgumentException("Modifier not found: " + modifierId));

        DefaultSurfaceModifierEntity template = defaultRepository.findAll().stream()
                .filter(t -> t.getOsmKey().equals(mod.getOsmKey()) && t.getOsmValue().equals(mod.getOsmValue()))
                .findFirst()
                .orElseGet(DefaultSurfaceModifierEntity::new);

        template.setCategory(mod.getCategory());
        template.setOsmKey(mod.getOsmKey());
        template.setOsmValue(mod.getOsmValue());
        template.setDescription(mod.getDescription());
        template.setMovementPriority(mod.getMovementPriority());
        template.setColor2d(mod.getColor2d());
        template.setTexture3d(mod.getTexture3d());
        template.setSpeedModifierWheeled(mod.getSpeedModifierWheeled());
        template.setSpeedModifierTracked(mod.getSpeedModifierTracked());
        template.setVisibilityMeters(mod.getVisibilityMeters());
        template.setCoverDefensePercent(mod.getCoverDefensePercent());
        template.setProperties(mod.getProperties());

        DefaultSurfaceModifierEntity saved = defaultRepository.save(template);
        log.info("[TEMPLATE SERVICE] Promoted map modifier '{}:{}' to Master Catalog", mod.getOsmKey(), mod.getOsmValue());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TacticalModifierData> getMapModifiers(UUID mapId) {
        return mapModifierRepository.findAllByMapId(mapId).stream()
                .sorted(Comparator.comparingInt(MapSurfaceModifierEntity::getMovementPriority).reversed())
                .map(m -> new TacticalModifierData(
                        m.getId(),
                        m.getCategory(),
                        m.getOsmKey(),
                        m.getOsmValue(),
                        m.getDescription(),
                        m.getMovementPriority(),
                        m.getColor2d() != null ? m.getColor2d() : "#f59e0b",
                        m.getTexture3d() != null ? m.getTexture3d() : "default",
                        m.getSpeedModifierWheeled(),
                        m.getSpeedModifierTracked(),
                        m.getVisibilityMeters(),
                        m.getCoverDefensePercent(),
                        m.getProperties()
                )).toList();
    }

    @Transactional
    public boolean updateMapModifier(UUID modifierId, TacticalModifierData updateData) {
        return mapModifierRepository.findById(modifierId).map(mod -> {
            mod.setDescription(updateData.description());
            mod.setMovementPriority(updateData.movementPriority());
            mod.setColor2d(updateData.color2d());
            mod.setTexture3d(updateData.texture3d());
            mod.setSpeedModifierWheeled(updateData.speedModifierWheeled());
            mod.setSpeedModifierTracked(updateData.speedModifierTracked());
            mod.setVisibilityMeters(updateData.visibilityMeters());
            mod.setCoverDefensePercent(updateData.coverDefensePercent());
            mod.setProperties(updateData.propertiesJson());
            mapModifierRepository.save(mod);
            return true;
        }).orElse(false);
    }

    /**
     * Applies a doctrine template directly to a specific modifier on the map by its ID.
     */
    @Transactional
    public boolean applyTemplateToModifier(UUID mapId, UUID modifierId, UUID templateId) {
        Optional<MapSurfaceModifierEntity> modOpt = mapModifierRepository.findById(modifierId);
        Optional<DefaultSurfaceModifierEntity> templateOpt = defaultRepository.findById(templateId);

        if (modOpt.isEmpty() || templateOpt.isEmpty()) return false;

        MapSurfaceModifierEntity mod = modOpt.get();
        DefaultSurfaceModifierEntity tmpl = templateOpt.get();

        // 1. Update the map surface modifier card itself
        mod.setDescription(tmpl.getDescription());
        mod.setMovementPriority(tmpl.getMovementPriority());
        mod.setColor2d(tmpl.getColor2d());
        mod.setTexture3d(tmpl.getTexture3d());
        mod.setSpeedModifierWheeled(tmpl.getSpeedModifierWheeled());
        mod.setSpeedModifierTracked(tmpl.getSpeedModifierTracked());
        mod.setVisibilityMeters(tmpl.getVisibilityMeters());
        mod.setCoverDefensePercent(tmpl.getCoverDefensePercent());
        mod.setProperties(tmpl.getProperties());
        mapModifierRepository.save(mod);

        // 2. Cascade update to all map features matching this osmKey=osmValue
        List<MapFeatureEntity> features = featureRepository.findByMapId(mapId);
        for (MapFeatureEntity f : features) {
            if (!f.isCustomModified() && f.getTypeKey().equals(mod.getOsmKey()) && f.getTypeValue().equals(mod.getOsmValue())) {
                f.setSpeedModifierOverrideWheeled(tmpl.getSpeedModifierWheeled());
                f.setSpeedModifierOverrideTracked(tmpl.getSpeedModifierTracked());
                f.setVisibilityOverride(tmpl.getVisibilityMeters());
                f.setCoverDefenseOverride(tmpl.getCoverDefensePercent());
                f.setCustomNotes(tmpl.getDescription());
            }
        }
        featureRepository.saveAll(features);

        log.info("[TEMPLATE SERVICE] Applied template '{}' to map modifier '{}' ({})",
                tmpl.getDescription(), mod.getOsmValue(), modifierId);

        return true;
    }

    /**
     * Auto-syncs both map_surface_modifiers AND map_features matching templates by osmKey=osmValue.
     */
    @Transactional
    public int autoSyncMapWithTemplates(UUID mapId) {
        List<DefaultSurfaceModifierEntity> templates = defaultRepository.findAll();
        if (templates.isEmpty()) return 0;

        Map<String, DefaultSurfaceModifierEntity> templateIndex = new HashMap<>();
        for (DefaultSurfaceModifierEntity t : templates) {
            templateIndex.put(t.getOsmKey() + "=" + t.getOsmValue(), t);
        }

        // 1. Sync all map modifier cards
        List<MapSurfaceModifierEntity> mapModifiers = mapModifierRepository.findAllByMapId(mapId);
        int syncedModifiers = 0;
        for (MapSurfaceModifierEntity mod : mapModifiers) {
            String key = mod.getOsmKey() + "=" + mod.getOsmValue();
            DefaultSurfaceModifierEntity tmpl = templateIndex.get(key);
            if (tmpl != null) {
                mod.setDescription(tmpl.getDescription());
                mod.setMovementPriority(tmpl.getMovementPriority());
                mod.setColor2d(tmpl.getColor2d());
                mod.setTexture3d(tmpl.getTexture3d());
                mod.setSpeedModifierWheeled(tmpl.getSpeedModifierWheeled());
                mod.setSpeedModifierTracked(tmpl.getSpeedModifierTracked());
                mod.setVisibilityMeters(tmpl.getVisibilityMeters());
                mod.setCoverDefensePercent(tmpl.getCoverDefensePercent());
                mod.setProperties(tmpl.getProperties());
                syncedModifiers++;
            }
        }
        if (syncedModifiers > 0) {
            mapModifierRepository.saveAll(mapModifiers);
        }

        // 2. Sync all map features
        List<MapFeatureEntity> features = featureRepository.findByMapId(mapId);
        int syncedFeatures = 0;
        for (MapFeatureEntity f : features) {
            if (f.isCustomModified()) continue;

            String key = f.getTypeKey() + "=" + f.getTypeValue();
            DefaultSurfaceModifierEntity tmpl = templateIndex.get(key);
            if (tmpl != null) {
                f.setSpeedModifierOverrideWheeled(tmpl.getSpeedModifierWheeled());
                f.setSpeedModifierOverrideTracked(tmpl.getSpeedModifierTracked());
                f.setVisibilityOverride(tmpl.getVisibilityMeters());
                f.setCoverDefenseOverride(tmpl.getCoverDefensePercent());
                f.setCustomNotes(tmpl.getDescription());
                syncedFeatures++;
            }
        }
        if (syncedFeatures > 0) {
            featureRepository.saveAll(features);
        }

        log.info("[TEMPLATE SYNC] Map {}: Synced {} modifier cards and {} features from Master Catalog",
                mapId, syncedModifiers, syncedFeatures);

        return syncedModifiers;
    }

    private void copyProperties(SurfaceTemplateRequest request, DefaultSurfaceModifierEntity entity) {
        entity.setCategory(request.category());
        entity.setOsmKey(request.osmKey());
        entity.setOsmValue(request.osmValue());
        entity.setDescription(request.description());
        entity.setMovementPriority(request.movementPriority());
        entity.setColor2d(request.color2d());
        entity.setTexture3d(request.texture3d());
        entity.setSpeedModifierWheeled(request.speedModifierWheeled());
        entity.setSpeedModifierTracked(request.speedModifierTracked());
        entity.setVisibilityMeters(request.visibilityMeters());
        entity.setCoverDefensePercent(request.coverDefensePercent());
        entity.setProperties(request.propertiesJson());
    }

    private SurfaceTemplateResponse toResponse(DefaultSurfaceModifierEntity entity) {
        return new SurfaceTemplateResponse(
                entity.getId(),
                entity.getCategory(),
                entity.getOsmKey(),
                entity.getOsmValue(),
                entity.getDescription(),
                entity.getMovementPriority(),
                entity.getColor2d() != null ? entity.getColor2d() : "#f59e0b",
                entity.getTexture3d() != null ? entity.getTexture3d() : "default",
                entity.getSpeedModifierWheeled(),
                entity.getSpeedModifierTracked(),
                entity.getVisibilityMeters(),
                entity.getCoverDefensePercent(),
                entity.getProperties()
        );
    }
}