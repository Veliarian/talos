package com.talos.gis.service;

import com.talos.gis.repository.*;
import com.talos.gis.util.GeoMath;
import com.talos.model.entity.DefaultSurfaceModifierEntity;
import com.talos.model.entity.MapEntity;
import com.talos.model.entity.MapLayerEntity;
import com.talos.model.entity.MapSurfaceModifierEntity;
import com.talos.model.dto.gis.BoundingBox;
import com.talos.model.dto.gis.MapCreationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * High-level orchestrator service using Spring Data JPA Repositories.
 * Eliminates raw SQL strings and manages entities in an object-oriented paradigm.
 */
@Service
public class MapIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MapIngestionService.class);

    private final MapRepository mapRepository;
    private final MapLayerRepository mapLayerRepository;
    private final MapSurfaceModifierRepository surfaceModifierRepository;
    private final DefaultSurfaceModifierRepository defaultModifierRepository;
    private final OsmExtractionRepository osmExtractionRepository;
    private final TileDownloaderService tileDownloader;
    private final DemExtractionService demExtractionService;

    @Value("${talos.storage.root:talos-data}")
    private String storageRoot;

    public MapIngestionService(MapRepository mapRepository,
                               MapLayerRepository mapLayerRepository,
                               MapSurfaceModifierRepository surfaceModifierRepository,
                               DefaultSurfaceModifierRepository defaultModifierRepository,
                               OsmExtractionRepository osmExtractionRepository,
                               TileDownloaderService tileDownloader,
                               DemExtractionService demExtractionService) {
        this.mapRepository = mapRepository;
        this.mapLayerRepository = mapLayerRepository;
        this.surfaceModifierRepository = surfaceModifierRepository;
        this.defaultModifierRepository = defaultModifierRepository;
        this.osmExtractionRepository = osmExtractionRepository;
        this.tileDownloader = tileDownloader;
        this.demExtractionService = demExtractionService;
    }

    /**
     * Creates and persists a new Map entity and related layers using JPA Repositories.
     */
    @Transactional
    public UUID createMap(MapCreationRequest request) {
        BoundingBox bbox = GeoMath.calculateBoundingBox(request.centerLat(), request.centerLon(), request.sizeKm());

        // 1. Create and save MapEntity
        MapEntity map = new MapEntity();
        map.setName(request.name());
        map.setDescription(request.description());
        map.setCenterLat(request.centerLat());
        map.setCenterLon(request.centerLon());
        map.setSizeKm(request.sizeKm());
        map.setMinLat(bbox.minLat());
        map.setMaxLat(bbox.maxLat());
        map.setMinLon(bbox.minLon());
        map.setMaxLon(bbox.maxLon());
        map.setStatus("DOWNLOADING");

        MapEntity savedMap = mapRepository.save(map);
        UUID mapId = savedMap.getId();

        log.info("[MAP INGESTION] Saved MapEntity '{}' ({}) via JPA Repository", savedMap.getName(), mapId);

        // 2. Clone default surface modifiers as typed entities
        List<DefaultSurfaceModifierEntity> defaults = defaultModifierRepository.findAll();
        List<MapSurfaceModifierEntity> mapModifiers = defaults.stream().map(def -> {
            MapSurfaceModifierEntity mod = new MapSurfaceModifierEntity();
            mod.setMap(savedMap);
            mod.setCategory(def.getCategory());
            mod.setOsmKey(def.getOsmKey());
            mod.setOsmValue(def.getOsmValue());
            mod.setDescription(def.getDescription());
            mod.setSpeedModifierWheeled(def.getSpeedModifierWheeled());
            mod.setSpeedModifierTracked(def.getSpeedModifierTracked());
            mod.setVisibilityMeters(def.getVisibilityMeters());
            mod.setCoverDefensePercent(def.getCoverDefensePercent());
            return mod;
        }).toList();
        surfaceModifierRepository.saveAll(mapModifiers);

        // 3. Register baselayers as typed entities
        List<String> layerTypes = (request.layerTypes() == null || request.layerTypes().isEmpty())
                ? List.of("SATELLITE")
                : request.layerTypes();

        int minZ = (request.minZoom() != null) ? request.minZoom() : 12;
        int maxZ = (request.maxZoom() != null) ? request.maxZoom() : 15;

        for (int i = 0; i < layerTypes.size(); i++) {
            String type = layerTypes.get(i).toUpperCase();
            MapLayerEntity layer = new MapLayerEntity();
            layer.setMap(savedMap);
            layer.setLayerType(type);
            layer.setMinZoom(minZ);
            layer.setMaxZoom(maxZ);
            layer.setDefault(i == 0);
            layer.setStatus("DOWNLOADING");
            layer.setLocalPath(Paths.get(storageRoot, "maps", mapId.toString(), "tiles", type.toLowerCase()).toString());

            mapLayerRepository.save(layer);
        }

        // 4. Trigger asynchronous asset download in background
        CompletableFuture.runAsync(() -> processMapAssets(mapId, bbox, layerTypes, minZ, maxZ));

        return mapId;
    }

    private void processMapAssets(UUID mapId, BoundingBox bbox, List<String> layerTypes, int minZ, int maxZ) {
        try {
            // A. Extract DEM GeoTIFF
            String demPath = demExtractionService.extractTheaterElevation(mapId, bbox, storageRoot);

            // B. Download tiles for each registered layer
            for (String layerType : layerTypes) {
                tileDownloader.downloadLayerTiles(mapId, layerType, bbox, minZ, maxZ, storageRoot);

                mapLayerRepository.findByMapIdAndLayerType(mapId, layerType.toUpperCase())
                        .ifPresent(layer -> {
                            layer.setStatus("READY");
                            mapLayerRepository.save(layer);
                        });
            }

            // Populate vector features (roads & polygons) for this map strictly via repository
            try {
                osmExtractionRepository.extractRoadsForMap(mapId, bbox.minLon(), bbox.minLat(), bbox.maxLon(), bbox.maxLat());
                osmExtractionRepository.extractPolygonsForMap(mapId, bbox.minLon(), bbox.minLat(), bbox.maxLon(), bbox.maxLat());
                log.info("[MAP INGESTION] Populated vector features for map {}", mapId);
            } catch (Exception e) {
                log.warn("[MAP INGESTION] Vector features extraction skipped: {}", e.getMessage());
            }

            // C. Mark MapEntity as READY
            mapRepository.findById(mapId).ifPresent(map -> {
                map.setDemFilePath(demPath);
                map.setStatus("READY");
                mapRepository.save(map);
            });

            log.info("[MAP INGESTION] Map {} is fully downloaded and marked READY", mapId);
        } catch (Exception e) {
            log.error("[MAP INGESTION] Error processing assets for map " + mapId, e);
            mapRepository.findById(mapId).ifPresent(map -> {
                map.setStatus("FAILED");
                mapRepository.save(map);
            });
        }
    }
}