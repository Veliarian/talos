package com.talos.gis.service;

import com.talos.gis.dto.BoundingBox;
import com.talos.gis.util.GeoMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads and caches basemap tiles locally on the server for 100% offline air-gapped execution.
 * Pulls tiles hierarchically from minZoom (overview) down to maxZoom (tactical detail).
 */
@Service
public class TileDownloaderService {

    private static final Logger log = LoggerFactory.getLogger(TileDownloaderService.class);

    public static final Map<String, String> LAYER_URL_TEMPLATES = Map.of(
            // Google Satellite HD (sub-meter resolution up to Zoom 19-20)
            "SATELLITE", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}",
            "TOPOGRAPHIC", "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            "TACTICAL", "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
    );

    private static final long TILE_FETCH_DELAY_MS = 320;

    private final HttpClient httpClient;
    private final GisStorageService storageService;

    public TileDownloaderService(GisStorageService storageService) {
        this.storageService = storageService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Downloads tiles hierarchically covering the bounding box from minZoom to maxZoom.
     */
    public int downloadLayerTiles(UUID mapId, String layerType, BoundingBox bbox, int minZoom, int maxZoom) {
        String urlTemplate = LAYER_URL_TEMPLATES.get(layerType.toUpperCase());
        if (urlTemplate == null) {
            log.warn("[TILE DOWNLOADER] Unknown baselayer type: {}. Skipping.", layerType);
            return 0;
        }

        Path baseOutputDir = storageService.resolvePath(String.format("maps/%s/tiles/%s", mapId, layerType.toLowerCase()));
        AtomicInteger downloadedCount = new AtomicInteger(0);

        log.info("[TILE DOWNLOADER] Caching layer '{}' for Map {} (Zooms: z{}-z{})",
                layerType, mapId, minZoom, maxZoom);

        // Download hierarchically: overview zoom first, tactical detail last
        for (int z = minZoom; z <= maxZoom; z++) {
            GeoMath.TileRange range = GeoMath.getTileRange(bbox, z);
            int tilesInLevel = (range.maxX() - range.minX() + 1) * (range.maxY() - range.minY() + 1);

            log.info("[TILE DOWNLOADER] Map {} | Layer: {} | Zoom {}: downloading {} tiles",
                    mapId, layerType, z, tilesInLevel);

            for (int x = range.minX(); x <= range.maxX(); x++) {
                for (int y = range.minY(); y <= range.maxY(); y++) {
                    boolean success = downloadSingleTileWithRetry(urlTemplate, baseOutputDir, z, x, y, 3);
                    if (success) {
                        downloadedCount.incrementAndGet();
                    }
                    throttle();
                }
            }
        }

        log.info("[TILE DOWNLOADER] Layer '{}' finished for Map {}. Total offline tiles cached: {}",
                layerType, mapId, downloadedCount.get());

        return downloadedCount.get();
    }

    private boolean downloadSingleTileWithRetry(String urlTemplate, Path baseDir, int z, int x, int y, int retries) {
        Path tilePath = baseDir.resolve(String.valueOf(z))
                .resolve(String.valueOf(x))
                .resolve(y + ".png");

        if (Files.exists(tilePath)) {
            return true; // Already cached locally
        }

        String requestUrl = urlTemplate
                .replace("{z}", String.valueOf(z))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                if (!Files.exists(tilePath.getParent())) {
                    Files.createDirectories(tilePath.getParent());
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(requestUrl))
                        .header("User-Agent", "TALOS-Simulation-Engine/1.0 (tactical simulation platform)")
                        .timeout(Duration.ofSeconds(12))
                        .GET()
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200) {
                    Files.write(tilePath, response.body());
                    return true;
                } else if (response.statusCode() == 429 || response.statusCode() == 503) {
                    Thread.sleep(attempt * 1000L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException ignored) {}
        }
        return false;
    }

    private void throttle() {
        try {
            Thread.sleep(TILE_FETCH_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}