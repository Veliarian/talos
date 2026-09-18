package com.talos.gis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.talos.model.entity.MapFeatureEntity;
import java.util.UUID;

/**
 * Dedicated data-access repository handling initial feature ingestion into map_features.
 * Encapsulates all spatial extraction logic behind repository methods.
 */
@Repository
public interface OsmExtractionRepository extends JpaRepository<MapFeatureEntity, UUID> {

    /**
     * Extracts roads from planet_osm_line and populates map_features for this map.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO map_features (id, map_id, osm_id, name, category, type_key, type_value, geometry_type, geojson)
        SELECT gen_random_uuid(), :mapId, osm_id, COALESCE(name, 'Дорога'), 'ROAD', 'highway', highway, 'LineString',
               ST_AsGeoJSON(ST_Transform(way, 4326))
        FROM planet_osm_line
        WHERE highway IS NOT NULL
          AND way && ST_Transform(ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326), 3857)
        LIMIT 300
    """)
    void extractRoadsForMap(
            @Param("mapId") UUID mapId,
            @Param("minLon") double minLon,
            @Param("minLat") double minLat,
            @Param("maxLon") double maxLon,
            @Param("maxLat") double maxLat
    );

    /**
     * Extracts area polygons (forests, buildings, water) and populates map_features.
     * Keyword "natural" is properly quoted.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO map_features (id, map_id, osm_id, name, category, type_key, type_value, geometry_type, geojson)
        SELECT gen_random_uuid(), :mapId, osm_id, COALESCE(name, 'Ділянка'),
               CASE 
                   WHEN building IS NOT NULL THEN 'BUILDING'
                   WHEN "natural" = 'wood' OR landuse = 'forest' THEN 'VEGETATION'
                   WHEN "natural" = 'water' THEN 'WATER'
                   ELSE 'SOIL'
               END,
               CASE 
                   WHEN building IS NOT NULL THEN 'building'
                   WHEN "natural" IS NOT NULL THEN 'natural'
                   ELSE 'landuse'
               END,
               COALESCE(building, "natural", landuse, 'area'),
               'Polygon',
               ST_AsGeoJSON(ST_Transform(way, 4326))
        FROM planet_osm_polygon
        WHERE (building IS NOT NULL OR "natural" IS NOT NULL OR landuse IS NOT NULL)
          AND way && ST_Transform(ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326), 3857)
        LIMIT 300
    """)
    void extractPolygonsForMap(
            @Param("mapId") UUID mapId,
            @Param("minLon") double minLon,
            @Param("minLat") double minLat,
            @Param("maxLon") double maxLon,
            @Param("maxLat") double maxLat
    );
}