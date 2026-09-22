package com.talos.gis.service;

import com.talos.gis.util.DemRaster;
import com.talos.gis.util.GeoMath;
import com.talos.model.dto.gis.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

/**
 * Service downloading real global elevation directly as 32-bit Float GeoTIFF (.tif) files.
 * Properly stitches full 512x512 GeoTIFF tiles without dropping data.
 */
@Service
public class DemExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DemExtractionService.class);

    // Direct open 32-bit Float GeoTIFF (.tif) dataset (Copernicus 30m + SRTM)
    private static final String GEOTIFF_TILE_URL = "https://s3.amazonaws.com/elevation-tiles-prod/geotiff/{z}/{x}/{y}.tif";

    // AWS GeoTIFF tiles have resolution of 512x512 pixels
    private static final int TILE_SIZE = 512;

    private final HttpClient httpClient;

    public DemExtractionService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String extractTheaterElevation(UUID mapId, BoundingBox bbox, String storageRoot) {
        Path targetDir = Paths.get(storageRoot, "maps", mapId.toString());
        Path targetFile = targetDir.resolve("terrain.tif");

        try {
            Files.createDirectories(targetDir);

            int demZoom = 11;
            GeoMath.TileRange range = GeoMath.getTileRange(bbox, demZoom);

            int tilesX = range.maxX() - range.minX() + 1;
            int tilesY = range.maxY() - range.minY() + 1;

            // Full pixel canvas covering all downloaded tiles without any clipping
            int fullW = tilesX * TILE_SIZE;
            int fullH = tilesY * TILE_SIZE;
            float[][] fullStitchedGrid = new float[fullH][fullW];

            log.info("[DEM STITCH] Downloading {}x{} full 512x512 GeoTIFF tiles (canvas: {}x{}) for Map {}...",
                    tilesX, tilesY, fullW, fullH, mapId);

            // 1. Download and stitch all raw GeoTIFF tiles preserving all 512x512 pixels
            for (int ty = range.minY(); ty <= range.maxY(); ty++) {
                for (int tx = range.minX(); tx <= range.maxX(); tx++) {
                    int offsetX = (tx - range.minX()) * TILE_SIZE;
                    int offsetY = (ty - range.minY()) * TILE_SIZE;

                    downloadAndPasteGeoTiffTile(demZoom, tx, ty, fullStitchedGrid, offsetX, offsetY);
                }
            }

            // 2. Crop the exact 20x20km theater from the seamless stitched grid into a 512x512 master raster
            int outputResolution = 512;
            float[] finalFloats = new float[outputResolution * outputResolution];

            for (int y = 0; y < outputResolution; y++) {
                double lat = bbox.maxLat() - (y / (double) (outputResolution - 1)) * (bbox.maxLat() - bbox.minLat());
                double ty = GeoMath.latToTileYDouble(lat, demZoom);
                double pixelY = (ty - range.minY()) * TILE_SIZE;
                pixelY = Math.max(0.0, Math.min(fullH - 1.001, pixelY));

                int y0 = (int) Math.floor(pixelY);
                int y1 = Math.min(fullH - 1, y0 + 1);
                double dy = pixelY - y0;

                for (int x = 0; x < outputResolution; x++) {
                    double lon = bbox.minLon() + (x / (double) (outputResolution - 1)) * (bbox.maxLon() - bbox.minLon());
                    double tx = GeoMath.lonToTileXDouble(lon, demZoom);
                    double pixelX = (tx - range.minX()) * TILE_SIZE;
                    pixelX = Math.max(0.0, Math.min(fullW - 1.001, pixelX));

                    int x0 = (int) Math.floor(pixelX);
                    int x1 = Math.min(fullW - 1, x0 + 1);
                    double dx = pixelX - x0;

                    // Bilinear interpolation across seamless stitched tile data
                    float h00 = fullStitchedGrid[y0][x0];
                    float h10 = fullStitchedGrid[y0][x1];
                    float h01 = fullStitchedGrid[y1][x0];
                    float h11 = fullStitchedGrid[y1][x1];

                    float hTop = (float) (h00 * (1.0 - dx) + h10 * dx);
                    float hBottom = (float) (h01 * (1.0 - dx) + h11 * dx);
                    finalFloats[y * outputResolution + x] = (float) (hTop * (1.0 - dy) + hBottom * dy);
                }
            }

            // 3. Save as clean native Float32 GeoTIFF (.tif) file
            DemRaster resultRaster = new DemRaster(outputResolution, outputResolution, finalFloats);
            resultRaster.writeToFile(targetFile.toFile());

            log.info("[DEM STITCH] Successfully created seamless master Float32 terrain.tif (512x512) for Map {}", mapId);
            return targetFile.toString();

        } catch (Exception e) {
            log.error("[DEM STITCH] Error extracting GeoTIFF DEM for map " + mapId, e);
            throw new RuntimeException("Failed to extract elevation DEM", e);
        }
    }

    /**
     * Downloads and parses all 512x512 pixels of a single Float32 GeoTIFF tile.
     */
    private void downloadAndPasteGeoTiffTile(int z, int x, int y, float[][] targetGrid, int offsetX, int offsetY) {
        String url = GEOTIFF_TILE_URL.replace("{z}", String.valueOf(z))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("User-Agent", "TALOS-Simulation-Engine/1.0")
                    .timeout(Duration.ofSeconds(12)).GET().build();

            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() == 200) {
                BufferedImage img = ImageIO.read(resp.body());
                if (img != null) {
                    Raster raster = img.getData();
                    int imgW = raster.getWidth();  // 512
                    int imgH = raster.getHeight(); // 512

                    for (int py = 0; py < imgH; py++) {
                        for (int px = 0; px < imgW; px++) {
                            float alt = raster.getSampleFloat(px, py, 0);
                            targetGrid[offsetY + py][offsetX + px] = alt;
                        }
                    }
                }
            } else {
                log.warn("[DEM STITCH] GeoTIFF tile {}/{}/{} returned HTTP {}", z, x, y, resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("[DEM STITCH] GeoTIFF tile {}/{}/{} fetch exception: {}", z, x, y, e.getMessage());
        }
    }
}