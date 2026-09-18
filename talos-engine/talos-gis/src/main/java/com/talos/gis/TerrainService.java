package com.talos.gis;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.InputStream;

@Service
public class TerrainService {
    private static final Logger log = LoggerFactory.getLogger(TerrainService.class);

    @Value("${talos.bbox.minLat}") private double minLat;
    @Value("${talos.bbox.maxLat}") private double maxLat;
    @Value("${talos.bbox.minLon}") private double minLon;
    @Value("${talos.bbox.maxLon}") private double maxLon;

    private float[][] heightMap;
    private int imgWidth = 0;
    private int imgHeight = 0;

    private final ResourceLoader resourceLoader;

    public TerrainService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadTerrain() {
        try {
            Resource resource = resourceLoader.getResource("classpath:terrain/terrain.tif");
            if (!resource.exists()) {
                log.warn("terrain.tif не знайдено! Використовується синтетичний рельєф.");
                initFallbackFlatTerrain();
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                BufferedImage image = ImageIO.read(is);
                Raster raster = image.getData();
                this.imgWidth = raster.getWidth();
                this.imgHeight = raster.getHeight();
                this.heightMap = new float[imgHeight][imgWidth];

                for (int y = 0; y < imgHeight; y++) {
                    for (int x = 0; x < imgWidth; x++) {
                        // Читання висоти (1 канал)
                        this.heightMap[y][x] = raster.getSampleFloat(x, y, 0);
                    }
                }
                log.info("Рельєф завантажено в RAM: {}x{} точок", imgWidth, imgHeight);
            }
        } catch (Exception e) {
            log.error("Помилка завантаження GeoTIFF, використовується плоский рельєф", e);
            initFallbackFlatTerrain();
        }
    }

    private void initFallbackFlatTerrain() {
        this.imgWidth = 100;
        this.imgHeight = 100;
        this.heightMap = new float[100][100];
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                this.heightMap[y][x] = 250.0f; // Базова висота 250 м для Яворова
            }
        }
    }

    public double getElevation(double lat, double lon) {
        if (heightMap == null) return 0.0;
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return 200.0;

        // Мапування Lat/Lon на індекси матриці
        int x = (int) ((lon - minLon) / (maxLon - minLon) * (imgWidth - 1));
        // Растрові координати Y зазвичай ідуть зверху вниз (від maxLat до minLat)
        int y = (int) ((maxLat - lat) / (maxLat - minLat) * (imgHeight - 1));

        x = Math.max(0, Math.min(x, imgWidth - 1));
        y = Math.max(0, Math.min(y, imgHeight - 1));

        return heightMap[y][x];
    }

    /**
     * Перевірка прямої видимості (Line of Sight) між двома точками за матрицею висот.
     * Крокує по променю з інтервалом ~25 метрів.
     */
    public boolean hasLineOfSight(double lat1, double lon1, double alt1, double eyeH1,
                                  double lat2, double lon2, double alt2, double targetH2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        double dy = dLat * 111132.0;
        double dx = dLon * 71500.0;
        double distance = Math.hypot(dx, dy);

        if (distance < 5.0) return true; // Впритул завжди видно

        // Кількість кроків трасування (крок ~25 метрів)
        int steps = (int) Math.max(5, distance / 25.0);
        double startRayH = alt1 + eyeH1;
        double endRayH = alt2 + targetH2;

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double curLat = lat1 + dLat * t;
            double curLon = lon1 + dLon * t;

            // Висота променя прямої видимості в даній точці
            double rayHeight = startRayH + (endRayH - startRayH) * t;

            // Реальна висота ґрунту за DEM
            double groundHeight = getElevation(curLat, curLon);

            // Якщо висота пагорба перевищує промінь — видимість перекрито
            if (groundHeight > rayHeight) {
                return false;
            }
        }
        return true; // Промінь пройшов без перешкод
    }
}