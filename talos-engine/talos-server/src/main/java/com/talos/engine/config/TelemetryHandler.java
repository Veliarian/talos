package com.talos.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talos.core.SimulationEngine;
import com.talos.model.CommandDto;
import com.talos.model.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TelemetryHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(TelemetryHandler.class);
    private final SimulationEngine simulationEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    public TelemetryHandler(SimulationEngine simulationEngine) {
        this.simulationEngine = simulationEngine;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("[NET] Клієнт підключився: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("[NET] Клієнт відключився: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            CommandDto cmd = objectMapper.readValue(message.getPayload(), CommandDto.class);
            simulationEngine.handleCommand(cmd);
        } catch (Exception e) {
            log.error("[NET] Помилка команди", e);
        }
    }

    /**
     * СУВОРИЙ ТУМАН ВІЙНИ:
     * Клієнт отримує ТІЛЬКИ свої сили (BLUFOR)
     * Вороги (OPFOR) потрапляють у пакет ЛИШЕ якщо isDetectedByEnemy == true!
     */
    @Scheduled(fixedRate = 100)
    public void broadcastTelemetry() {
        if (sessions.isEmpty()) return;
        try {
            List<Unit> visibleUnits = simulationEngine.getUnits().stream()
                    .filter(u -> "BLUFOR".equalsIgnoreCase(u.getSide()) || u.isDetectedByEnemy())
                    .toList();

            String payload = objectMapper.writeValueAsString(visibleUnits);
            TextMessage message = new TextMessage(payload);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (Exception ignored) {}
    }
}