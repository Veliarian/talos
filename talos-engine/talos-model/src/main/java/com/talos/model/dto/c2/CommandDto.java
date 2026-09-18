package com.talos.model.dto.c2;

import java.util.List;

public record CommandDto(
        String type,
        List<String> unitIds,
        Double targetLat,
        Double targetLon,
        Boolean queue,
        Double speedKmh
) {}