package com.talos.core;

import com.talos.model.Unit;
import com.talos.model.CommandDto;
import com.talos.gis.RoadService;
import com.talos.gis.TerrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SimulationEngine {
    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);
    private final TerrainService terrainService;
    private final RoadService roadService;

    private final List<Unit> units = new CopyOnWriteArrayList<>();
    private final java.util.Set<String> activeContactIds = new java.util.HashSet<>();
    private final double DT = 0.1; // 100 мс

    public SimulationEngine(TerrainService terrainService, RoadService roadService) {
        this.terrainService = terrainService;
        this.roadService = roadService;
        spawnMarchColumn(); // BLUFOR
        spawnOpforForces(); // OPFOR
    }

    private void spawnMarchColumn() {
        List<Unit.Waypoint> roadRoute = roadService.extractLongestRoadRoute();
        if (roadRoute.size() < 2) return;

        Unit.Waypoint startPoint = roadRoute.get(0);
        Unit.Waypoint secondPoint = roadRoute.get(1);

        double dLat = secondPoint.lat() - startPoint.lat();
        double dLon = secondPoint.lon() - startPoint.lon();
        double dist = Math.hypot(dLat * 111132.0, dLon * 71500.0);
        double stepLat = (dLat / dist) * 35.0 / 111132.0;
        double stepLon = (dLon / dist) * 35.0 / 71500.0;

        for (int i = 0; i < 10; i++) {
            Unit u = new Unit();
            u.setId("blufor-" + (i + 1));
            u.setCallsign("БТР-4Е №" + (i + 1));
            u.setSide("BLUFOR");
            u.setBaseSpeedKmh(50.0);
            u.setLat(startPoint.lat() - (stepLat * i));
            u.setLon(startPoint.lon() - (stepLon * i));
            u.setAltitude(terrainService.getElevation(u.getLat(), u.getLon()));

            for (Unit.Waypoint wp : roadRoute) {
                u.addWaypoint(wp.lat(), wp.lon());
            }
            units.add(u);
        }
        log.info("[TALOS] Сформовано колону BLUFOR з {} машин", 10);
    }

    /**
     * Розміщення ворожих підрозділів OPFOR (Червоні)
     */
    private void spawnOpforForces() {
        List<Unit.Waypoint> roadRoute = roadService.extractLongestRoadRoute();

        // Рахуємо сумарну відстань уздовж вигинів дороги до 500 метрів
        double targetDistMeters = 500.0;
        double accumulatedDist = 0.0;
        Unit.Waypoint ambushPoint = roadRoute.get(roadRoute.size() - 1);

        for (int i = 0; i < roadRoute.size() - 1; i++) {
            Unit.Waypoint p1 = roadRoute.get(i);
            Unit.Waypoint p2 = roadRoute.get(i + 1);
            double d = Math.hypot((p2.lat() - p1.lat()) * 111132.0, (p2.lon() - p1.lon()) * 71500.0);
            accumulatedDist += d;

            if (accumulatedDist >= targetDistMeters) {
                ambushPoint = p2;
                break;
            }
        }

        // Ставимо замасковану БМП-2 рівно за 500 м від колони
        // (через inCover=true дальність виявлення 350 м — тому зараз її НЕ видно!)
        Unit opfor1 = new Unit();
        opfor1.setId("opfor-1");
        opfor1.setCallsign("БМП-2 (Засада 500м)");
        opfor1.setSide("OPFOR");
        opfor1.setInCover(true); // Максимум 350 метрів
        opfor1.setLat(ambushPoint.lat());
        opfor1.setLon(ambushPoint.lon());
        opfor1.setHeading(240.0);
        opfor1.setAltitude(terrainService.getElevation(opfor1.getLat(), opfor1.getLon()));
        units.add(opfor1);

        log.info("[TALOS] Ворога виставлено на дорозі за 500 метрів від точки старту");
    }

    @Scheduled(fixedRate = 100) // 10 Гц
    public void update() {
        try {
            // 1. Фізика руху
            for (Unit unit : units) {
                stepUnit(unit, DT);
            }

            // 2. Розрахунок сенсорів та лінії видимості (LOS)
            updateSensorsAndDetection();

        } catch (Exception e) {
            log.error("[TALOS ENGINE] Помилка в циклі симуляції", e);
        }
    }

    /**
     * Математика сенсорів: перевіряє, кого бачать сині БТРи
     */
    private void updateSensorsAndDetection() {
        List<Unit> blufor = units.stream().filter(u -> "BLUFOR".equals(u.getSide())).toList();
        List<Unit> opfor = units.stream().filter(u -> "OPFOR".equals(u.getSide())).toList();

        for (Unit enemy : opfor) {
            enemy.setDetectedByEnemy(false);
        }
        for (Unit friendly : blufor) {
            friendly.getVisibleTargetIds().clear();
        }

        java.util.Set<String> currentTickSpotted = new java.util.HashSet<>();

        for (Unit observer : blufor) {
            for (Unit enemy : opfor) {
                double dist = Math.hypot((enemy.getLat() - observer.getLat()) * 111132.0,
                        (enemy.getLon() - observer.getLon()) * 71500.0);

                if (canObserveTarget(observer, enemy, dist)) {
                    enemy.setDetectedByEnemy(true);
                    observer.getVisibleTargetIds().add(enemy.getId());
                    currentTickSpotted.add(enemy.getId());

                    // ЛОГУЄМО ЛИШЕ В МОМЕНТ ПЕРШОГО ВИЯВЛЕННЯ (БЕЗ СПАМУ!)
                    if (!activeContactIds.contains(enemy.getId())) {
                        activeContactIds.add(enemy.getId());
                        log.info(">>> [БОЙОВИЙ КОНТАКТ!] {} виявив {} на дистанції {} м! <<<",
                                observer.getCallsign(), enemy.getCallsign(), (int)dist);
                    }
                }
            }
        }

        // Якщо ціль сховалася за пагорб або дистанція збільшилася — фіксуємо втрату контакту
        activeContactIds.removeIf(id -> {
            if (!currentTickSpotted.contains(id)) {
                log.info("--- [ВТРАТА КОНТАКТУ] Зв'язок із ціллю {} втрачено ---", id);
                return true;
            }
            return false;
        });
    }

    private boolean canObserveTarget(Unit obs, Unit target, double distance) {
        double maxRange = target.isInCover() ? 350.0 : obs.getMaxOpticsRangeMeters();
        if (distance > maxRange) {
            return false;
        }

        if (distance > 200.0) {
            double dLat = target.getLat() - obs.getLat();
            double dLon = target.getLon() - obs.getLon();
            double bearingToTarget = Math.toDegrees(Math.atan2(dLon * 71500.0, dLat * 111132.0));
            if (bearingToTarget < 0) bearingToTarget += 360.0;

            double angleDiff = Math.abs(bearingToTarget - obs.getHeading());
            if (angleDiff > 180.0) angleDiff = 360.0 - angleDiff;

            if (angleDiff > 45.0) {
                return false;
            }
        }

        return terrainService.hasLineOfSight(
                obs.getLat(), obs.getLon(), obs.getAltitude(), obs.getEyeHeightMeters(),
                target.getLat(), target.getLon(), target.getAltitude(), target.getTargetHeightMeters()
        );
    }

    private void stepUnit(Unit unit, double dt) {
        if (unit.getWaypoints() == null || unit.getWaypoints().isEmpty()) {
            unit.setCurrentSpeedKmh(0.0);
            return;
        }

        Unit.Waypoint target = unit.getWaypoints().peek();
        double dLat = target.lat() - unit.getLat();
        double dLon = target.lon() - unit.getLon();
        double dy = dLat * 111132.0;
        double dx = dLon * 71500.0;
        double distance = Math.hypot(dx, dy);

        if (distance < 15.0) {
            unit.getWaypoints().poll();
            return;
        }

        double heading = Math.toDegrees(Math.atan2(dx, dy));
        if (heading < 0) heading += 360.0;
        unit.setHeading(heading);

        boolean onRoad = roadService.isOnRoad(unit.getLat(), unit.getLon(), 25.0);
        double surfaceModifier = onRoad ? 1.0 : 0.6;

        double speedMs = (unit.getBaseSpeedKmh() / 3.6) * surfaceModifier;
        unit.setCurrentSpeedKmh(speedMs * 3.6);

        double stepDist = speedMs * dt;
        double ratio = Math.min(1.0, stepDist / distance);

        unit.setLat(unit.getLat() + dLat * ratio);
        unit.setLon(unit.getLon() + dLon * ratio);
        unit.setAltitude(terrainService.getElevation(unit.getLat(), unit.getLon()));
    }

    public void handleCommand(CommandDto cmd) {
        if (cmd.unitIds() == null || cmd.unitIds().isEmpty()) return;

        for (String id : cmd.unitIds()) {
            units.stream().filter(u -> u.getId().equals(id)).findFirst().ifPresent(u -> {
                switch (cmd.type()) {
                    case "ORDER_MOVE" -> {
                        if (!Boolean.TRUE.equals(cmd.queue())) {
                            u.clearWaypoints();
                        }
                        if (cmd.targetLat() != null && cmd.targetLon() != null) {
                            u.addWaypoint(cmd.targetLat(), cmd.targetLon());
                        }
                    }
                    case "ORDER_HALT" -> {
                        u.clearWaypoints();
                        u.setCurrentSpeedKmh(0.0);
                    }
                    case "ORDER_CHANGE_SPEED" -> {
                        if (cmd.speedKmh() != null) {
                            u.setBaseSpeedKmh(cmd.speedKmh());
                        }
                    }
                }
            });
        }
    }

    public List<Unit> getUnits() {
        return units;
    }
}