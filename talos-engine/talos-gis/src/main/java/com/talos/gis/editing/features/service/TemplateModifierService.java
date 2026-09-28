package com.talos.gis.editing.features.service;

import com.talos.gis.core.entity.MapEntity;
import com.talos.gis.core.repository.MapRepository;
import com.talos.gis.editing.features.entity.DefaultSurfaceModifierEntity;
import com.talos.gis.editing.features.entity.MapSurfaceModifierEntity;
import com.talos.gis.editing.features.model.SurfaceTemplateRequest;
import com.talos.gis.editing.features.model.SurfaceTemplateResponse;
import com.talos.gis.editing.features.model.TacticalModifierData;
import com.talos.gis.editing.features.repository.DefaultSurfaceModifierRepository;
import com.talos.gis.editing.features.repository.MapSurfaceModifierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing global doctrine surface templates and assigning them to operational maps.
 */
@Service
public class TemplateModifierService {

    private final DefaultSurfaceModifierRepository defaultRepository;
    private final MapSurfaceModifierRepository mapModifierRepository;
    private final MapRepository mapRepository;

    public TemplateModifierService(DefaultSurfaceModifierRepository defaultRepository,
                                   MapSurfaceModifierRepository mapModifierRepository,
                                   MapRepository mapRepository) {
        this.defaultRepository = defaultRepository;
        this.mapModifierRepository = mapModifierRepository;
        this.mapRepository = mapRepository;
    }

    @Transactional(readOnly = true)
    public List<SurfaceTemplateResponse> getAllTemplates() {
        return defaultRepository.findAll().stream()
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

    @Transactional
    public boolean applyTemplateToMap(UUID mapId, UUID templateId) {
        Optional<MapEntity> mapOpt = mapRepository.findById(mapId);
        Optional<DefaultSurfaceModifierEntity> templateOpt = defaultRepository.findById(templateId);

        if (mapOpt.isEmpty() || templateOpt.isEmpty()) {
            return false;
        }

        MapEntity map = mapOpt.get();
        DefaultSurfaceModifierEntity tmpl = templateOpt.get();

        List<MapSurfaceModifierEntity> existing = mapModifierRepository.findAllByMapId(mapId);
        MapSurfaceModifierEntity target = existing.stream()
                .filter(m -> m.getOsmKey().equals(tmpl.getOsmKey()) && m.getOsmValue().equals(tmpl.getOsmValue()))
                .findFirst()
                .orElseGet(() -> {
                    MapSurfaceModifierEntity m = new MapSurfaceModifierEntity();
                    m.setMap(map);
                    m.setCategory(tmpl.getCategory());
                    m.setOsmKey(tmpl.getOsmKey());
                    m.setOsmValue(tmpl.getOsmValue());
                    return m;
                });

        target.setDescription(tmpl.getDescription());
        target.setSpeedModifierWheeled(tmpl.getSpeedModifierWheeled());
        target.setSpeedModifierTracked(tmpl.getSpeedModifierTracked());
        target.setVisibilityMeters(tmpl.getVisibilityMeters());
        target.setCoverDefensePercent(tmpl.getCoverDefensePercent());

        mapModifierRepository.save(target);
        return true;
    }

    private void copyProperties(SurfaceTemplateRequest request, DefaultSurfaceModifierEntity entity) {
        entity.setCategory(request.category());
        entity.setOsmKey(request.osmKey());
        entity.setOsmValue(request.osmValue());
        entity.setDescription(request.description());
        entity.setSpeedModifierWheeled(request.speedModifierWheeled());
        entity.setSpeedModifierTracked(request.speedModifierTracked());
        entity.setVisibilityMeters(request.visibilityMeters());
        entity.setCoverDefensePercent(request.coverDefensePercent());
    }

    private SurfaceTemplateResponse toResponse(DefaultSurfaceModifierEntity entity) {
        return new SurfaceTemplateResponse(
                entity.getId(),
                entity.getCategory(),
                entity.getOsmKey(),
                entity.getOsmValue(),
                entity.getDescription(),
                entity.getSpeedModifierWheeled(),
                entity.getSpeedModifierTracked(),
                entity.getVisibilityMeters(),
                entity.getCoverDefensePercent()
        );
    }

    @Transactional(readOnly = true)
    public List<TacticalModifierData> getMapModifiers(UUID mapId) {
        return mapModifierRepository.findAllByMapId(mapId).stream()
                .map(m -> new TacticalModifierData(
                        m.getId(),
                        m.getCategory(),
                        m.getOsmKey(),
                        m.getOsmValue(),
                        m.getDescription(),
                        m.getSpeedModifierWheeled(),
                        m.getSpeedModifierTracked(),
                        m.getVisibilityMeters(),
                        m.getCoverDefensePercent()
                )).toList();
    }

    @Transactional
    public boolean updateMapModifier(UUID modifierId, TacticalModifierData updateData) {
        return mapModifierRepository.findById(modifierId).map(mod -> {
            mod.setSpeedModifierWheeled(updateData.speedModifierWheeled());
            mod.setSpeedModifierTracked(updateData.speedModifierTracked());
            mod.setVisibilityMeters(updateData.visibilityMeters());
            mod.setCoverDefensePercent(updateData.coverDefensePercent());
            mapModifierRepository.save(mod);
            return true;
        }).orElse(false);
    }
}