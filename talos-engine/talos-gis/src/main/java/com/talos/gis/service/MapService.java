package com.talos.gis.service;

import com.talos.gis.dto.MapDetailDto;
import com.talos.gis.dto.SurfaceModifierDto;
import org.springframework.core.io.Resource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MapService {

    List<MapDetailDto> getAllMaps();

    Optional<MapDetailDto> getMapById(UUID mapId);

    Optional<Resource> getTileResource(UUID mapId, String layerType, int z, int x, int y);

    Optional<Resource> getDemResource(UUID mapId);

    List<SurfaceModifierDto> getMapModifiers(UUID mapId);

    boolean updateModifier(UUID modifierId, SurfaceModifierDto dto);

    byte[] sampleTerrainGrid(UUID mapId, double minLat, double maxLat, double minLon, double maxLon, int width, int height);

    boolean deleteMap(UUID mapId);

    Path resolveStoragePath(String subPath);
}