package com.talos.gis.editing.basemap.service;

import com.talos.gis.core.service.GisStorageService;
import com.talos.gis.creation.service.TileDownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing tile streaming and on-demand Live Proxy Caching
 * for sub-meter Google Satellite HD and topographical basemaps.
 */
@Service
public class TileStreamingService {

    private static final Logger log = LoggerFactory.getLogger(TileStreamingService.class);

    private final GisStorageService storageService;
    private final HttpClient httpClient;

    public TileStreamingService(GisStorageService storageService) {
        this.storageService = storageService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<Resource> getTileResource(UUID mapId, String layerType, int z, int x, int y) {
        String subPath = String.format("maps/%s/tiles/%s/%d/%d/%d.png",
                mapId, layerType.toLowerCase(), z, x, y);

        Path tilePath = storageService.resolvePath(subPath);

        // 1. FAST LOCAL HIT: Tile already cached on disk (100% offline)
        if (Files.exists(tilePath)) {
            return Optional.of(new FileSystemResource(tilePath));
        }

        // 2. LIVE PROXY CACHE: Fetch on the fly from upstream HD provider and persist to disk
        String upperLayer = layerType.toUpperCase();
        String urlTemplate = TileDownloadService.LAYER_URL_TEMPLATES.get(upperLayer);

        if (urlTemplate == null || z > 19) {
            return Optional.empty();
        }

        String requestUrl = urlTemplate
                .replace("{z}", String.valueOf(z))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200 && response.body().length > 0) {
                // Thread-safe atomic write to local storage
                Files.createDirectories(tilePath.getParent());
                Path tempFile = tilePath.resolveSibling(tilePath.getFileName() + ".tmp");
                Files.write(tempFile, response.body());
                Files.move(tempFile, tilePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

                return Optional.of(new FileSystemResource(tilePath));
            }
        } catch (Exception e) {
            log.debug("[TILE PROXY] Live fetch failed for {}/{}/{}/{}: {}", layerType, z, x, y, e.getMessage());
        }

        // 3. OFFLINE FALLBACK: Return 404 so Cesium gracefully upscales the nearest parent zoom
        return Optional.empty();
    }
}