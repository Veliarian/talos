package com.talos.gis.service;

import com.talos.gis.dto.DefaultModifierDto;
import com.talos.gis.entity.DefaultSurfaceModifierEntity;
import com.talos.gis.entity.MapEntity;
import com.talos.gis.entity.MapSurfaceModifierEntity;
import com.talos.gis.repository.DefaultSurfaceModifierRepository;
import com.talos.gis.repository.MapRepository;
import com.talos.gis.repository.MapSurfaceModifierRepository;
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
    public List<DefaultModifierDto> getAllTemplates() {
        return defaultRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DefaultModifierDto createTemplate(DefaultModifierDto dto) {
        DefaultSurfaceModifierEntity entity = new DefaultSurfaceModifierEntity();
        copyProperties(dto, entity);
        DefaultSurfaceModifierEntity saved = defaultRepository.save(entity);
        return toDto(saved);
    }

    @Transactional
    public Optional<DefaultModifierDto> updateTemplate(UUID id, DefaultModifierDto dto) {
        return defaultRepository.findById(id).map(entity -> {
            copyProperties(dto, entity);
            return toDto(defaultRepository.save(entity));
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
     * Applies a global template to a specific theater map.
     */
    @Transactional
    public boolean applyTemplateToMap(UUID mapId, UUID templateId) {
        Optional<MapEntity> mapOpt = mapRepository.findById(mapId);
        Optional<DefaultSurfaceModifierEntity> templateOpt = defaultRepository.findById(templateId);

        if (mapOpt.isEmpty() || templateOpt.isEmpty()) {
            return false;
        }

        MapEntity map = mapOpt.get();
        DefaultSurfaceModifierEntity tmpl = templateOpt.get();

        // Check if map already has this modifier, if so update, otherwise create
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

    private void copyProperties(DefaultModifierDto dto, DefaultSurfaceModifierEntity entity) {
        entity.setCategory(dto.category());
        entity.setOsmKey(dto.osmKey());
        entity.setOsmValue(dto.osmValue());
        entity.setDescription(dto.description());
        entity.setSpeedModifierWheeled(dto.speedModifierWheeled());
        entity.setSpeedModifierTracked(dto.speedModifierTracked());
        entity.setVisibilityMeters(dto.visibilityMeters());
        entity.setCoverDefensePercent(dto.coverDefensePercent());
    }

    private DefaultModifierDto toDto(DefaultSurfaceModifierEntity entity) {
        return new DefaultModifierDto(
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
}