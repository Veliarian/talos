package com.talos.gis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centralized service managing relative and absolute runtime storage paths for GIS assets.
 */
@Service
public class GisStorageService {

    @Value("${talos.storage.root:talos-data}")
    private String storageRoot;

    /**
     * Resolves the storage base directory safely regardless of whether execution
     * is run from repo root, sub-module root, or nested target context.
     */
    public Path getStorageBasePath() {
        Path direct = Paths.get(storageRoot);
        if (Files.exists(direct)) return direct.toAbsolutePath().normalize();

        Path parent = Paths.get("..", storageRoot);
        if (Files.exists(parent)) return parent.toAbsolutePath().normalize();

        Path grandParent = Paths.get("../..", storageRoot);
        if (Files.exists(grandParent)) return grandParent.toAbsolutePath().normalize();

        return direct.toAbsolutePath().normalize();
    }

    public Path resolvePath(String relativeSubPath) {
        return getStorageBasePath().resolve(relativeSubPath).normalize();
    }

    public void ensureDirectoriesExist(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }
}