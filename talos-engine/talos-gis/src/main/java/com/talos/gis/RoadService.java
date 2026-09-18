package com.talos.gis;

import com.talos.model.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoadService {
    private static final Logger log = LoggerFactory.getLogger(RoadService.class);
    private final JdbcTemplate jdbcTemplate;

    public RoadService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Витягує з PostGIS найдовшу суцільну дорогу полігону
     * і повертає список точок (вигинів дороги) у форматі Lat/Lon
     */
    public List<Unit.Waypoint> extractLongestRoadRoute() {
        List<Unit.Waypoint> roadPoints = new ArrayList<>();
        try {
            // Запит повертає лінію дороги у текстовому форматі WKT (Well-Known Text)
            String sql = """
                SELECT ST_AsText(ST_Transform(way, 4326)) 
                FROM planet_osm_line 
                WHERE highway IN ('primary', 'secondary', 'tertiary', 'unclassified', 'road')
                ORDER BY ST_Length(way) DESC 
                LIMIT 1;
            """;

            String wkt = jdbcTemplate.queryForObject(sql, String.class);

            if (wkt != null && wkt.startsWith("LINESTRING")) {
                // Парсимо рядок виду "LINESTRING(lon1 lat1, lon2 lat2, ...)"
                String coordsPart = wkt.substring(wkt.indexOf("(") + 1, wkt.indexOf(")"));
                String[] pairs = coordsPart.split(",");

                for (String pair : pairs) {
                    String[] lonLat = pair.trim().split(" ");
                    double lon = Double.parseDouble(lonLat[0]);
                    double lat = Double.parseDouble(lonLat[1]);
                    roadPoints.add(new Unit.Waypoint(lat, lon));
                }
                log.info("[ROAD SERVICE] Успішно завантажено маршрут дороги з {} точок вигину", roadPoints.size());
            }
        } catch (Exception e) {
            log.warn("[ROAD SERVICE] Не вдалося завантажити дорогу з бази, використовується резервний маршрут", e);
        }

        // Якщо база ще порожня — плавний звивистий резервний маршрут траси Т-1425
        if (roadPoints.isEmpty()) {
            roadPoints.add(new Unit.Waypoint(49.9880, 23.5450));
            roadPoints.add(new Unit.Waypoint(49.9895, 23.5600));
            roadPoints.add(new Unit.Waypoint(49.9870, 23.5850));
            roadPoints.add(new Unit.Waypoint(49.9920, 23.6150));
            roadPoints.add(new Unit.Waypoint(49.9955, 23.6500));
        }

        return roadPoints;
    }

    public boolean isOnRoad(double lat, double lon, double bufferMeters) {
        try {
            String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM planet_osm_line 
                    WHERE highway IS NOT NULL 
                    AND ST_DWithin(
                        way, 
                        ST_Transform(ST_SetSRID(ST_MakePoint(CAST(? AS double precision), CAST(? AS double precision)), 4326), 3857), 
                        CAST(? AS double precision)
                    )
                    LIMIT 1
                );
            """;
            Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, lon, lat, bufferMeters);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}