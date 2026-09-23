package com.talos.gis.service;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.talos.gis.dto.FeatureUpdateRequestDto;
import com.talos.gis.entity.MapFeatureEntity;
import com.talos.gis.repository.MapFeatureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing theater vector feature retrieval, fast GeoJSON streaming,
 * and individual tactical feature attribute overrides.
 */
@Service
public class VectorFeatureService {

    private static final Logger log = LoggerFactory.getLogger(VectorFeatureService.class);
    private static final String EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}";
    private static final String DEFAULT_FEATURE_NAME = "Feature";

    private final MapFeatureRepository mapFeatureRepository;
    private final JsonStringEncoder jsonEncoder = JsonStringEncoder.getInstance();

    public VectorFeatureService(MapFeatureRepository mapFeatureRepository) {
        this.mapFeatureRepository = mapFeatureRepository;
    }

    /**
     * Retrieves vector features for the specified map and compiles a valid GeoJSON FeatureCollection.
     * Incorporates entity UUID, tactical status, and individual TTX overrides into properties.
     */
    @Transactional(readOnly = true)
    public String getTheaterVectorsGeoJson(UUID mapId) {
        List<MapFeatureEntity> features = mapFeatureRepository.findByMapId(mapId);

        if (features.isEmpty()) {
            return EMPTY_FEATURE_COLLECTION;
        }

        StringBuilder sb = new StringBuilder(features.size() * 320 + 64);
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");

        boolean first = true;
        for (MapFeatureEntity f : features) {
            String geometry = f.getGeojson();
            if (geometry == null || geometry.isBlank()) {
                continue;
            }

            if (!first) {
                sb.append(',');
            }
            first = false;

            sb.append("{\"type\":\"Feature\",\"geometry\":").append(geometry)
                    .append(",\"properties\":{")
                    .append("\"id\":\"").append(f.getId()).append("\",")
                    .append("\"osmId\":").append(f.getOsmId() != null ? f.getOsmId() : "null").append(',')
                    .append("\"category\":\"");
            appendEscaped(sb, f.getCategory());
            sb.append("\",\"typeKey\":\"");
            appendEscaped(sb, f.getTypeKey());
            sb.append("\",\"typeValue\":\"");
            appendEscaped(sb, f.getTypeValue());
            sb.append("\",\"name\":\"");
            appendEscaped(sb, f.getName() != null ? f.getName() : DEFAULT_FEATURE_NAME);
            sb.append("\",\"status\":\"");
            appendEscaped(sb, f.getStatus() != null ? f.getStatus() : "OPERATIONAL");
            sb.append("\",\"isCustomModified\":").append(f.isCustomModified()).append(',')
                    .append("\"speedOverrideWheeled\":").append(f.getSpeedModifierOverrideWheeled()).append(',')
                    .append("\"speedOverrideTracked\":").append(f.getSpeedModifierOverrideTracked()).append(',')
                    .append("\"visibilityOverride\":").append(f.getVisibilityOverride()).append(',')
                    .append("\"coverOverride\":").append(f.getCoverDefenseOverride()).append(',')
                    .append("\"heightMeters\":").append(f.getHeightMeters()).append(',')
                    .append("\"widthMeters\":").append(f.getWidthMeters()).append(',')
                    .append("\"customNotes\":\"");
            appendEscaped(sb, f.getCustomNotes() != null ? f.getCustomNotes() : "");
            sb.append("\"}}");
        }

        sb.append("]}");
        return sb.toString();
    }

    /**
     * Updates an individual feature instance parameters, status, and local TTX overrides.
     *
     * @param mapId     theater map UUID
     * @param featureId target feature entity UUID
     * @param dto       update parameters
     * @return true if updated, false if feature not found or does not belong to the map
     */
    @Transactional
    public boolean updateFeature(UUID mapId, UUID featureId, FeatureUpdateRequestDto dto) {
        Optional<MapFeatureEntity> featureOpt = mapFeatureRepository.findById(featureId);

        if (featureOpt.isEmpty()) {
            log.warn("[VECTOR SERVICE] Feature not found: {}", featureId);
            return false;
        }

        MapFeatureEntity feature = featureOpt.get();
        if (!feature.getMap().getId().equals(mapId)) {
            log.warn("[VECTOR SERVICE] Feature {} does not belong to Map {}", featureId, mapId);
            return false;
        }

        if (dto.name() != null && !dto.name().isBlank()) {
            feature.setName(dto.name());
        }
        if (dto.status() != null) {
            feature.setStatus(dto.status());
        }

        feature.setSpeedModifierOverrideWheeled(dto.speedModifierOverrideWheeled());
        feature.setSpeedModifierOverrideTracked(dto.speedModifierOverrideTracked());
        feature.setVisibilityOverride(dto.visibilityOverride());
        feature.setCoverDefenseOverride(dto.coverDefenseOverride());
        feature.setCustomNotes(dto.customNotes());

        if (dto.heightMeters() != null) {
            feature.setHeightMeters(dto.heightMeters());
        }
        if (dto.widthMeters() != null) {
            feature.setWidthMeters(dto.widthMeters());
        }

        feature.setCustomModified(true);
        mapFeatureRepository.save(feature);

        log.info("[VECTOR SERVICE] Feature {} successfully updated (Status: {}) for Map {}",
                featureId, feature.getStatus(), mapId);

        return true;
    }

    private void appendEscaped(StringBuilder sb, String text) {
        if (text != null) {
            char[] escaped = jsonEncoder.quoteAsString(text);
            sb.append(escaped);
        }
    }
}