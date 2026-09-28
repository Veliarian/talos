package com.talos.gis.editing.elevation.model;

/**
 * Response representing current availability state of Undo and Redo operations for a map's elevation model.
 */
public record TerrainHistoryResponse(
        boolean canUndo,
        boolean canRedo
) {}