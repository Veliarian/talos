package com.talos.gis.editing.features.service;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talos.gis.core.entity.MapEntity;
import com.talos.gis.core.repository.MapRepository;
import com.talos.gis.core.service.GisStorageService;
import com.talos.gis.editing.elevation.util.DemRaster;
import com.talos.gis.editing.features.entity.MapFeatureEntity;
import com.talos.gis.editing.features.model.FeaturePatchRequest;
import com.talos.gis.editing.features.repository.MapFeatureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing theater vector feature retrieval, GeoJSON compilation with exact
 * DEM ground altitude sampling, and individual feature attribute overrides.
 */
@Service
public class VectorFeatureService {

    private static final Logger log = LoggerFactory.getLogger(VectorFeatureService.class);
    private static final String EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}";
    private static final String DEFAULT_FEATURE_NAME = "Feature";

    private final MapFeatureRepository mapFeatureRepository;
    private final MapRepository mapRepository;
    private final GisStorageService storageService;
    private final ObjectMapper objectMapper;
    private final JsonStringEncoder jsonEncoder = JsonStringEncoder.getInstance();

    public VectorFeatureService(MapFeatureRepository mapFeatureRepository,
                                MapRepository mapRepository,
                                GisStorageService storageService,
                                ObjectMapper objectMapper) {
        this.mapFeatureRepository = mapFeatureRepository;
        this.mapRepository = mapRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public String getTheaterVectorsGeoJson(UUID mapId) {
        List<MapFeatureEntity> features = mapFeatureRepository.findByMapId(mapId);

        if (features.isEmpty()) {
            return EMPTY_FEATURE_COLLECTION;
        }

        // Load map DEM raster if available to sample exact ground altitudes for 3D extrusion
        DemRaster dem = null;
        MapEntity map = mapRepository.findById(mapId).orElse(null);
        if (map != null) {
            Path demPath = storageService.resolvePath(String.format("maps/%s/terrain.tif", mapId));
            File demFile = demPath.toFile();
            if (demFile.exists() && demFile.length() > 256) {
                try {
                    dem = DemRaster.readFromFile(demFile);
                } catch (Exception e) {
                    log.warn("[VECTOR SERVICE] Could not read terrain.tif for ground heights: {}", e.getMessage());
                }
            }
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

            // Sample ground elevation under the feature's first vertex
            float groundAlt = 0.0f;
            if (dem != null && map != null) {
                groundAlt = extractGroundAltitude(geometry, dem, map);
            }

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
                    .append("\"groundAlt\":").append(groundAlt).append(',')
                    .append("\"customNotes\":\"");
            appendEscaped(sb, f.getCustomNotes() != null ? f.getCustomNotes() : "");
            sb.append("\"}}");
        }

        sb.append("]}");
        return sb.toString();
    }

    private float extractGroundAltitude(String geojson, DemRaster dem, MapEntity map) {
        try {
            JsonNode geomNode = objectMapper.readTree(geojson);
            JsonNode coords = geomNode.get("coordinates");
            if (coords == null || !coords.isArray() || coords.isEmpty()) return 0.0f;

            double lon;
            double lat;

            // Extract first coordinate pair
            if ("Polygon".equals(geomNode.path("type").asText())) {
                JsonNode firstRing = coords.get(0);
                if (firstRing == null || !firstRing.isArray() || firstRing.isEmpty()) return 0.0f;
                lon = firstRing.get(0).get(0).asDouble();
                lat = firstRing.get(0).get(1).asDouble();
            } else {
                lon = coords.get(0).get(0).asDouble();
                lat = coords.get(0).get(1).asDouble();
            }

            double normX = (lon - map.getMinLon()) / (map.getMaxLon() - map.getMinLon());
            double normY = (map.getMaxLat() - lat) / (map.getMaxLat() - map.getMinLat());

            return dem.getInterpolatedElevation(normX, normY);
        } catch (Exception e) {
            return 0.0f;
        }
    }

    @Transactional
    public boolean updateFeature(UUID mapId, UUID featureId, FeaturePatchRequest dto) {
        Optional<MapFeatureEntity> featureOpt = mapFeatureRepository.findById(featureId);
        if (featureOpt.isEmpty()) return false;

        MapFeatureEntity feature = featureOpt.get();
        if (!feature.getMap().getId().equals(mapId)) return false;

        if (dto.name() != null && !dto.name().isBlank()) feature.setName(dto.name());
        if (dto.status() != null) feature.setStatus(dto.status());

        feature.setSpeedModifierOverrideWheeled(dto.speedModifierOverrideWheeled());
        feature.setSpeedModifierOverrideTracked(dto.speedModifierOverrideTracked());
        feature.setVisibilityOverride(dto.visibilityOverride());
        feature.setCoverDefenseOverride(dto.coverDefenseOverride());
        feature.setCustomNotes(dto.customNotes());

        if (dto.heightMeters() != null) feature.setHeightMeters(dto.heightMeters());
        if (dto.widthMeters() != null) feature.setWidthMeters(dto.widthMeters());

        feature.setCustomModified(true);
        mapFeatureRepository.save(feature);
        return true;
    }

    private void appendEscaped(StringBuilder sb, String text) {
        if (text != null) {
            char[] escaped = jsonEncoder.quoteAsString(text);
            sb.append(escaped);
        }
    }
}