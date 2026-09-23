package com.talos.gis.repository;

import com.talos.gis.entity.MapFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Dedicated repository for road spatial queries against PostGIS OSM tables.
 */
@Repository
public interface RoadSpatialRepository extends JpaRepository<MapFeatureEntity, UUID> {

    /**
     * Finds the longest contiguous road route within the theater as a GeoJSON LineString.
     */
    @Query(nativeQuery = true, value = """
        SELECT ST_AsGeoJSON(ST_Transform(way, 4326))
        FROM planet_osm_line
        WHERE highway IN ('primary', 'secondary', 'tertiary', 'unclassified', 'road')
        ORDER BY ST_Length(way) DESC
        LIMIT 1
    """)
    Optional<String> findLongestRoadGeoJson();

    /**
     * Checks if a point lies within a road buffer distance using spatial indexing.
     */
    @Query(nativeQuery = true, value = """
        SELECT EXISTS (
            SELECT 1 FROM planet_osm_line
            WHERE highway IS NOT NULL
              AND ST_DWithin(
                  way,
                  ST_Transform(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), 3857),
                  :bufferMeters
              )
            LIMIT 1
        )
    """)
    boolean isPointOnRoad(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("bufferMeters") double bufferMeters
    );
}