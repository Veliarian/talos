package com.talos.gis.editing.elevation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory manager for DEM elevation snapshot history, supporting fast, deterministic
 * multi-step Undo and Redo operations without destructive matrix recalculation.
 */
@Service
public class TerrainHistoryService {

    private static final Logger log = LoggerFactory.getLogger(TerrainHistoryService.class);
    private static final int MAX_HISTORY_DEPTH = 20;

    private final Map<UUID, Deque<float[]>> undoStacks = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<float[]>> redoStacks = new ConcurrentHashMap<>();

    /**
     * Records the current elevation raster state before a modification occurs.
     * Clears redo stack for the map upon any new mutating action.
     */
    public synchronized void recordPreMutationState(UUID mapId, float[] currentData) {
        Deque<float[]> stack = undoStacks.computeIfAbsent(mapId, k -> new ArrayDeque<>());
        if (stack.size() >= MAX_HISTORY_DEPTH) {
            stack.removeLast(); // Evict oldest snapshot to maintain memory cap
        }
        stack.push(Arrays.copyOf(currentData, currentData.length));
        redoStacks.remove(mapId); // New action invalidates forward redo history

        log.debug("[TERRAIN HISTORY] Pushed pre-mutation state for map {}. Undo stack size: {}", mapId, stack.size());
    }

    /**
     * Reverts to the previous elevation matrix snapshot.
     */
    public synchronized Optional<float[]> undo(UUID mapId, float[] currentData) {
        Deque<float[]> undoStack = undoStacks.get(mapId);
        if (undoStack == null || undoStack.isEmpty()) {
            return Optional.empty();
        }

        // Push current active matrix onto redo stack before restoring
        Deque<float[]> redoStack = redoStacks.computeIfAbsent(mapId, k -> new ArrayDeque<>());
        redoStack.push(Arrays.copyOf(currentData, currentData.length));

        float[] previousState = undoStack.pop();
        log.info("[TERRAIN HISTORY] Reverted map {} to previous state. Remaining undos: {}, redos: {}",
                mapId, undoStack.size(), redoStack.size());

        return Optional.of(previousState);
    }

    /**
     * Re-applies the next elevation matrix snapshot from redo stack.
     */
    public synchronized Optional<float[]> redo(UUID mapId, float[] currentData) {
        Deque<float[]> redoStack = redoStacks.get(mapId);
        if (redoStack == null || redoStack.isEmpty()) {
            return Optional.empty();
        }

        Deque<float[]> undoStack = undoStacks.computeIfAbsent(mapId, k -> new ArrayDeque<>());
        undoStack.push(Arrays.copyOf(currentData, currentData.length));

        float[] nextState = redoStack.pop();
        log.info("[TERRAIN HISTORY] Redone state for map {}. Undos: {}, remaining redos: {}",
                mapId, undoStack.size(), redoStack.size());

        return Optional.of(nextState);
    }

    public synchronized boolean canUndo(UUID mapId) {
        Deque<float[]> stack = undoStacks.get(mapId);
        return stack != null && !stack.isEmpty();
    }

    public synchronized boolean canRedo(UUID mapId) {
        Deque<float[]> stack = redoStacks.get(mapId);
        return stack != null && !stack.isEmpty();
    }

    public synchronized void clearHistory(UUID mapId) {
        undoStacks.remove(mapId);
        redoStacks.remove(mapId);
    }
}