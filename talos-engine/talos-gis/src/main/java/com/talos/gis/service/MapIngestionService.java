package com.talos.gis.service;

import com.talos.gis.dto.BoundingBox;
import com.talos.gis.dto.MapCreationRequest;
import com.talos.gis.entity.DefaultSurfaceModifierEntity;
import com.talos.gis.entity.MapEntity;
import com.talos.gis.entity.MapLayerEntity;
import com.talos.gis.entity.MapSurfaceModifierEntity;
import com.talos.gis.repository.*;
import com.talos.gis.util.GeoMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-level orchestrator service coordinating map creation, baselayer cataloging,
 * default surface modifier replication, vector extraction, and asynchronous asset streaming.
 */
@Service
public class MapIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MapIngestionService.class);

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_DOWNLOADING = "DOWNLOADING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    private final MapRepository mapRepository;
    private final MapLayerRepository mapLayerRepository;
    private final MapSurfaceModifierRepository surfaceModifierRepository;
    private final DefaultSurfaceModifierRepository defaultModifierRepository;
    private final OsmExtractionRepository osmExtractionRepository;
    private final TileDownloaderService tileDownloader;
    private final DemExtractionService demExtractionService;
    private final OverpassExtractionService overpassExtractionService;
    private final GisStorageService storageService;

    // Dedicated asynchronous task executor using Java 21 virtual threads
    private final ExecutorService ingestionExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public MapIngestionService(MapRepository mapRepository,
                               MapLayerRepository mapLayerRepository,
                               MapSurfaceModifierRepository surfaceModifierRepository,
                               DefaultSurfaceModifierRepository defaultModifierRepository,
                               OsmExtractionRepository osmExtractionRepository,
                               TileDownloaderService tileDownloader,
                               DemExtractionService demExtractionService,
                               GisStorageService storageService,
                               OverpassExtractionService overpassExtractionService) {
        this.mapRepository = mapRepository;
        this.mapLayerRepository = mapLayerRepository;
        this.surfaceModifierRepository = surfaceModifierRepository;
        this.defaultModifierRepository = defaultModifierRepository;
        this.osmExtractionRepository = osmExtractionRepository;
        this.tileDownloader = tileDownloader;
        this.demExtractionService = demExtractionService;
        this.storageService = storageService;
        this.overpassExtractionService = overpassExtractionService;
    }

    /**
     * Creates and persists a new Map entity and related layers, triggering background asset download.
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
        map.setStatus(STATUS_DOWNLOADING);

        MapEntity savedMap = mapRepository.save(map);
        UUID mapId = savedMap.getId();

        log.info("[MAP INGESTION] Saved MapEntity '{}' ({}) via JPA Repository", savedMap.getName(), mapId);

        // 3. Register baselayers as typed entities
        List<String> layerTypes = (request.layerTypes() == null || request.layerTypes().isEmpty())
                ? List.of("SATELLITE")
                : request.layerTypes();

        GeoMath.ZoomRange zoomRange = GeoMath.calculateOptimalZoomRange(request.centerLat(), request.sizeKm());
        int minZ = (request.minZoom() != null) ? request.minZoom() : zoomRange.minZoom();
        int maxZ = (request.maxZoom() != null) ? request.maxZoom() : zoomRange.maxZoom();

        for (int i = 0; i < layerTypes.size(); i++) {
            String type = layerTypes.get(i).toUpperCase();
            Path layerPath = storageService.resolvePath(String.format("maps/%s/tiles/%s", mapId, type.toLowerCase()));

            MapLayerEntity layer = new MapLayerEntity();
            layer.setMap(savedMap);
            layer.setLayerType(type);
            layer.setMinZoom(minZ);
            layer.setMaxZoom(maxZ);
            layer.setDefault(i == 0);
            layer.setStatus(STATUS_DOWNLOADING);
            layer.setLocalPath(layerPath.toString());

            mapLayerRepository.save(layer);
        }

        // 4. Trigger asynchronous asset download strictly AFTER transaction commit
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ingestionExecutor.submit(() -> processMapAssets(mapId, bbox, layerTypes, minZ, maxZ));
                }
            });
        } else {
            ingestionExecutor.submit(() -> processMapAssets(mapId, bbox, layerTypes, minZ, maxZ));
        }

        return mapId;
    }

    /**
     * Background pipeline downloading elevation DEM, XYZ tiles, and vector features.
     */
    protected void processMapAssets(UUID mapId, BoundingBox bbox, List<String> layerTypes, int minZ, int maxZ) {
        log.info("[MAP INGESTION] Starting asynchronous asset processing for map {}", mapId);
        try {
            MapEntity currentMap = mapRepository.findById(mapId).orElse(null);
            double sizeKm = (currentMap != null) ? currentMap.getSizeKm() : 20.0;

            // 1. Extract DEM GeoTIFF with adaptive scaling (~2-4 seconds)
            String demPath = demExtractionService.extractTheaterElevation(mapId, bbox, sizeKm);
            if (currentMap != null) {
                currentMap.setDemFilePath(demPath);
                mapRepository.save(currentMap);
            }

            // 2. Extract 100% OSM Vector Features (~3-5 seconds)
            if (currentMap != null) {
                try {
                    int count = overpassExtractionService.extractAndPersistVectors(currentMap, bbox);
                    log.info("[MAP INGESTION] Overpass populated {} features for map {}", count, mapId);

                    // Fallback to local PostGIS extraction if Overpass returned zero
                    if (count == 0) {
                        log.info("[MAP INGESTION] Attempting fallback extraction from local planet_osm tables...");
                        osmExtractionRepository.extractRoadsForMap(mapId, bbox.minLon(), bbox.minLat(), bbox.maxLon(), bbox.maxLat());
                        osmExtractionRepository.extractPolygonsForMap(mapId, bbox.minLon(), bbox.minLat(), bbox.maxLon(), bbox.maxLat());
                    }
                } catch (Exception e) {
                    log.warn("[MAP INGESTION] Vector extraction encountered an issue: {}", e.getMessage());
                }
            }

            // 3. Mark Map as READY for immediate operational editing
            if (currentMap != null) {
                currentMap.setStatus(STATUS_READY);
                mapRepository.save(currentMap);
            }
            log.info("[MAP INGESTION] Map {} is marked READY with DEM and Vector features", mapId);

            // 4. Download raster tiles in the background
            for (String layerType : layerTypes) {
                tileDownloader.downloadLayerTiles(mapId, layerType, bbox, minZ, maxZ);

                mapLayerRepository.findByMapIdAndLayerType(mapId, layerType.toUpperCase())
                        .ifPresent(layer -> {
                            layer.setStatus(STATUS_READY);
                            mapLayerRepository.save(layer);
                        });
            }

            log.info("[MAP INGESTION] All background tile layers completed for map {}", mapId);

        } catch (Exception e) {
            log.error("[MAP INGESTION] Error processing assets for map {}", mapId, e);
            mapRepository.findById(mapId).ifPresent(map -> {
                map.setStatus(STATUS_FAILED);
                mapRepository.save(map);
            });
        }
    }
}