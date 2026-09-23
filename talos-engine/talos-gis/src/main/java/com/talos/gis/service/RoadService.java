package com.talos.gis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talos.gis.repository.RoadSpatialRepository;
import com.talos.model.domain.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service managing road route extraction and surface spatial queries via JPA repositories.
 */
@Service
public class RoadService {

    private static final Logger log = LoggerFactory.getLogger(RoadService.class);

    private final RoadSpatialRepository roadSpatialRepository;
    private final ObjectMapper objectMapper;

    public RoadService(RoadSpatialRepository roadSpatialRepository, ObjectMapper objectMapper) {
        this.roadSpatialRepository = roadSpatialRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts the longest continuous road line in the theater and returns ordered waypoints.
     * Uses GeoJSON structure for reliable parsing.
     */
    public List<Unit.Waypoint> extractLongestRoadRoute() {
        List<Unit.Waypoint> roadPoints = new ArrayList<>();

        try {
            var geoJsonOpt = roadSpatialRepository.findLongestRoadGeoJson();

            if (geoJsonOpt.isPresent()) {
                JsonNode root = objectMapper.readTree(geoJsonOpt.get());
                JsonNode coordinates = root.get("coordinates");

                if (coordinates != null && coordinates.isArray()) {
                    for (JsonNode coord : coordinates) {
                        double lon = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        roadPoints.add(new Unit.Waypoint(lat, lon));
                    }
                    log.info("[ROAD SERVICE] Successfully loaded road route with {} vertices", roadPoints.size());
                }
            }
        } catch (Exception e) {
            log.warn("[ROAD SERVICE] Failed to parse road geometry from database, using fallback route: {}", e.getMessage());
        }

        // Fallback route (T-1425 highway section) if database is empty or connection fails
        if (roadPoints.isEmpty()) {
            roadPoints.add(new Unit.Waypoint(49.9880, 23.5450));
            roadPoints.add(new Unit.Waypoint(49.9895, 23.5600));
            roadPoints.add(new Unit.Waypoint(49.9870, 23.5850));
            roadPoints.add(new Unit.Waypoint(49.9920, 23.6150));
            roadPoints.add(new Unit.Waypoint(49.9955, 23.6500));
        }

        return roadPoints;
    }

    /**
     * Determines whether the given coordinate point is located on an active road surface within buffer radius.
     */
    public boolean isOnRoad(double lat, double lon, double bufferMeters) {
        try {
            return roadSpatialRepository.isPointOnRoad(lat, lon, bufferMeters);
        } catch (Exception e) {
            log.debug("[ROAD SERVICE] Failed spatial road containment check: {}", e.getMessage());
            return false;
        }
    }
}