package com.talos.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talos.core.SimulationEngine;
import com.talos.model.domain.Unit;
import com.talos.model.dto.c2.CommandDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-frequency WebSocket handler providing tactical C2 telemetry streaming
 * and receiving unit navigation/fire orders.
 */
@Component
public class TelemetryHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryHandler.class);
    private static final String FRIENDLY_SIDE = "BLUFOR";
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final SimulationEngine simulationEngine;
    private final ObjectMapper objectMapper;
    private final List<WebSocketSession> activeSessions = new CopyOnWriteArrayList<>();

    public TelemetryHandler(SimulationEngine simulationEngine, ObjectMapper objectMapper) {
        this.simulationEngine = simulationEngine;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Wrap with Concurrent decorator to guarantee thread-safe message transmission
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT_BYTES
        );
        activeSessions.add(concurrentSession);
        log.info("[WS-NET] Client connected successfully. Session ID: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeSessions.removeIf(s -> s.getId().equals(session.getId()));
        log.info("[WS-NET] Client disconnected. Session ID: {}, Status: {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            CommandDto command = objectMapper.readValue(message.getPayload(), CommandDto.class);
            simulationEngine.handleCommand(command);
        } catch (Exception e) {
            log.error("[WS-NET] Failed to deserialize incoming command payload", e);
        }
    }

    /**
     * Broadcasts tactical telemetry at 10 Hz (every 100 ms).
     * Enforces Fog-of-War rules:
     * - Friendly forces (BLUFOR) are always broadcast.
     * - Opposing forces (OPFOR) are broadcast only when detected by sensors/LOS.
     */
    @Scheduled(fixedRate = 100)
    public void broadcastTelemetry() {
        if (activeSessions.isEmpty()) {
            return;
        }

        try {
            List<Unit> visibleUnits = simulationEngine.getUnits().stream()
                    .filter(u -> FRIENDLY_SIDE.equalsIgnoreCase(u.getSide()) || u.isDetectedByEnemy())
                    .toList();

            String payload = objectMapper.writeValueAsString(visibleUnits);
            TextMessage message = new TextMessage(payload);

            for (WebSocketSession session : activeSessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("[WS-NET] Failed to stream telemetry to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[WS-NET] Telemetry broadcast error", e);
        }
    }
}