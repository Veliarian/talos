package com.talos.gis.service;

import com.talos.gis.util.GeoMath;
import com.talos.model.dto.gis.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads and caches basemap tiles locally on the server for offline air-gapped execution.
 */
@Service
public class TileDownloaderService {

    private static final Logger log = LoggerFactory.getLogger(TileDownloaderService.class);

    // Base URL templates for supported layers
    private static final Map<String, String> LAYER_URL_TEMPLATES = Map.of(
            "SATELLITE", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            "TOPOGRAPHIC", "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            "TACTICAL", "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
    );

    private final HttpClient httpClient;

    public TileDownloaderService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Downloads all tiles within the bounding box across specified zoom levels and stores them in local disk storage.
     */
    public int downloadLayerTiles(UUID mapId, String layerType, BoundingBox bbox, int minZoom, int maxZoom, String storageRoot) {
        String urlTemplate = LAYER_URL_TEMPLATES.get(layerType.toUpperCase());
        if (urlTemplate == null) {
            throw new IllegalArgumentException("Unsupported baselayer type: " + layerType);
        }

        Path baseOutputDir = Paths.get(storageRoot, "maps", mapId.toString(), "tiles", layerType.toLowerCase());
        AtomicInteger downloadedCount = new AtomicInteger(0);

        log.info("[TILE DOWNLOADER] Starting download for Map {} (Layer: {}, Zooms: {}-{})",
                mapId, layerType, minZoom, maxZoom);

        // Use Java 21 Virtual Threads for non-blocking parallel downloads
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int z = minZoom; z <= maxZoom; z++) {
                GeoMath.TileRange range = GeoMath.getTileRange(bbox, z);

                for (int x = range.minX(); x <= range.maxX(); x++) {
                    for (int y = range.minY(); y <= range.maxY(); y++) {
                        final int tileZ = z;
                        final int tileX = x;
                        final int tileY = y;

                        executor.submit(() -> {
                            boolean success = downloadSingleTileWithRetry(urlTemplate, baseOutputDir, tileZ, tileX, tileY, 3);
                            if (success) {
                                downloadedCount.incrementAndGet();
                            }
                        });
                    }
                }
            }
        }

        log.info("[TILE DOWNLOADER] Completed download for Map {}. Total tiles stored: {}", mapId, downloadedCount.get());
        return downloadedCount.get();
    }

    private boolean downloadSingleTileWithRetry(String urlTemplate, Path baseDir, int z, int x, int y, int retries) {
        Path tilePath = baseDir.resolve(String.valueOf(z))
                .resolve(String.valueOf(x))
                .resolve(y + ".png");

        if (Files.exists(tilePath)) {
            return true;
        }

        String requestUrl = urlTemplate.replace("{z}", String.valueOf(z))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                Files.createDirectories(tilePath.getParent());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(requestUrl))
                        .header("User-Agent", "TALOS-Simulation-Engine/1.0 (Defense Research Map Client)")
                        .timeout(Duration.ofSeconds(12))
                        .GET()
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200) {
                    Files.write(tilePath, response.body());
                    return true;
                } else if (response.statusCode() == 503 || response.statusCode() == 429) {
                    // Rate limit hit: wait exponentially before retry (100ms, 250ms, 500ms)
                    Thread.sleep(attempt * 150L);
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}