package com.talos.gis.service;

import com.talos.gis.repository.MapFeatureRepository;
import com.talos.model.entity.MapFeatureEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Clean, repository-based service for serving map vector features.
 * Completely free of raw SQL queries - operates strictly via MapFeatureRepository.
 */
@Service
public class VectorFeatureService {

    private final MapFeatureRepository mapFeatureRepository;

    public VectorFeatureService(MapFeatureRepository mapFeatureRepository) {
        this.mapFeatureRepository = mapFeatureRepository;
    }

    /**
     * Retrieves vector features for the specified map purely through the repository
     * and serializes them into a GeoJSON FeatureCollection.
     */
    @Transactional(readOnly = true)
    public String getTheaterVectorsGeoJson(UUID mapId) {
        // 1. Fetch domain entities strictly via Spring Data JPA Repository
        List<MapFeatureEntity> features = mapFeatureRepository.findByMapId(mapId);

        if (features.isEmpty()) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }

        // 2. Assemble GeoJSON from typed entities
        List<String> featureJsonList = new ArrayList<>(features.size());
        for (MapFeatureEntity f : features) {
            String featureJson = String.format(
                    "{\"type\":\"Feature\",\"geometry\":%s,\"properties\":{\"id\":%d,\"category\":\"%s\",\"typeKey\":\"%s\",\"typeValue\":\"%s\",\"name\":\"%s\"}}",
                    f.getGeojson(),
                    f.getOsmId() != null ? f.getOsmId() : 0,
                    f.getCategory(),
                    f.getTypeKey(),
                    f.getTypeValue(),
                    f.getName() != null ? f.getName().replace("\"", "\\\"") : "Об'єкт"
            );
            featureJsonList.add(featureJson);
        }

        return String.format("{\"type\":\"FeatureCollection\",\"features\":[%s]}",
                String.join(",", featureJsonList));
    }
}