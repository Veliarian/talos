package com.talos.gis.editing.features.model;

import java.util.List;
import java.util.UUID;

/**
 * Request containing reordered template IDs from top (highest priority) to bottom.
 */
public record PriorityReorderRequest(
        List<UUID> orderedIds
) {}