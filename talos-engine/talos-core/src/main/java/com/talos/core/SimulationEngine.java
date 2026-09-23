package com.talos.core;

import com.talos.gis.service.RoadService;
import com.talos.gis.service.TerrainService;
import com.talos.model.domain.Unit;
import com.talos.model.dto.c2.CommandDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core simulation tick engine executing kinematic movement,
 * sensor optics, line-of-sight raycasts, and tactical orders at 10 Hz.
 */
@Component
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    // Geodetic metric approximations for theater latitude ~50 deg North
    private static final double METERS_PER_LAT_DEGREE = 111132.0;
    private static final double METERS_PER_LON_DEGREE = 71500.0;
    private static final double SIMULATION_TICK_DELTA_SEC = 0.1; // 100 ms (10 Hz)
    private static final double COVER_DETECTION_RANGE_METERS = 350.0;
    private static final double AMBUSH_DISTANCE_METERS = 500.0;
    private static final double WAYPOINT_REACHED_RADIUS_METERS = 15.0;

    private static final String SIDE_BLUFOR = "BLUFOR";
    private static final String SIDE_OPFOR = "OPFOR";

    private final TerrainService terrainService;
    private final RoadService roadService;

    private final List<Unit> units = new CopyOnWriteArrayList<>();
    private final Set<String> activeContactIds = ConcurrentHashMap.newKeySet();

    public SimulationEngine(TerrainService terrainService, RoadService roadService) {
        this.terrainService = terrainService;
        this.roadService = roadService;
        initDemoScenario();
    }

    /**
     * Initializes baseline demo scenario forces along the road network.
     * Temporary stand until dynamic scenario ingestion from database is wired.
     */
    private void initDemoScenario() {
        spawnMarchColumn();
        spawnOpforForces();
    }

    private void spawnMarchColumn() {
        List<Unit.Waypoint> roadRoute = roadService.extractLongestRoadRoute();
        if (roadRoute.size() < 2) {
            log.warn("[TALOS ENGINE] Not enough road waypoints found to spawn march column");
            return;
        }

        Unit.Waypoint startPoint = roadRoute.get(0);
        Unit.Waypoint secondPoint = roadRoute.get(1);

        double dLat = secondPoint.lat() - startPoint.lat();
        double dLon = secondPoint.lon() - startPoint.lon();
        double dist = Math.hypot(dLat * METERS_PER_LAT_DEGREE, dLon * METERS_PER_LON_DEGREE);

        if (dist <= 0.0) return;

        double stepLat = (dLat / dist) * 35.0 / METERS_PER_LAT_DEGREE;
        double stepLon = (dLon / dist) * 35.0 / METERS_PER_LON_DEGREE;

        for (int i = 0; i < 10; i++) {
            Unit unit = new Unit();
            unit.setId("blufor-" + (i + 1));
            unit.setCallsign("BTR-4E #" + (i + 1));
            unit.setSide(SIDE_BLUFOR);
            unit.setType("BTR_4E");
            unit.setBaseSpeedKmh(50.0);
            unit.setLat(startPoint.lat() - (stepLat * i));
            unit.setLon(startPoint.lon() - (stepLon * i));
            unit.setAltitude(terrainService.getElevation(unit.getLat(), unit.getLon()));

            for (Unit.Waypoint wp : roadRoute) {
                unit.addWaypoint(wp.lat(), wp.lon());
            }
            units.add(unit);
        }
        log.info("[TALOS ENGINE] Spawned BLUFOR march column: 10 units");
    }

    private void spawnOpforForces() {
        List<Unit.Waypoint> roadRoute = roadService.extractLongestRoadRoute();
        if (roadRoute.isEmpty()) return;

        double accumulatedDist = 0.0;
        Unit.Waypoint ambushPoint = roadRoute.get(roadRoute.size() - 1);

        for (int i = 0; i < roadRoute.size() - 1; i++) {
            Unit.Waypoint p1 = roadRoute.get(i);
            Unit.Waypoint p2 = roadRoute.get(i + 1);
            double dist = Math.hypot(
                    (p2.lat() - p1.lat()) * METERS_PER_LAT_DEGREE,
                    (p2.lon() - p1.lon()) * METERS_PER_LON_DEGREE
            );
            accumulatedDist += dist;

            if (accumulatedDist >= AMBUSH_DISTANCE_METERS) {
                ambushPoint = p2;
                break;
            }
        }

        // Concealed OPFOR BMP-2 positioned at ambush point
        Unit opforBmp = new Unit();
        opforBmp.setId("opfor-1");
        opforBmp.setCallsign("BMP-2 (Ambush 500m)");
        opforBmp.setSide(SIDE_OPFOR);
        opforBmp.setType("BMP_2");
        opforBmp.setInCover(true);
        opforBmp.setLat(ambushPoint.lat());
        opforBmp.setLon(ambushPoint.lon());
        opforBmp.setHeading(240.0);
        opforBmp.setAltitude(terrainService.getElevation(opforBmp.getLat(), opforBmp.getLon()));
        units.add(opforBmp);

        log.info("[TALOS ENGINE] Spawned OPFOR ambush force at 500m marker");
    }

    /**
     * Primary 10 Hz fixed simulation step tick.
     */
    @Scheduled(fixedRate = 100)
    public void update() {
        try {
            // 1. Kinematic integration step
            for (Unit unit : units) {
                stepUnit(unit, SIMULATION_TICK_DELTA_SEC);
            }

            // 2. Optical sensors & Line-of-Sight detection pass
            updateSensorsAndDetection();

        } catch (Exception e) {
            log.error("[TALOS ENGINE] Critical error in simulation tick execution", e);
        }
    }

    /**
     * Executes optical line-of-sight checks and sensor awareness.
     */
    private void updateSensorsAndDetection() {
        List<Unit> bluforUnits = units.stream().filter(u -> SIDE_BLUFOR.equals(u.getSide())).toList();
        List<Unit> opforUnits = units.stream().filter(u -> SIDE_OPFOR.equals(u.getSide())).toList();

        // Reset detection states before recalculation
        for (Unit enemy : opforUnits) {
            enemy.setDetectedByEnemy(false);
        }

        Set<String> currentTickSpotted = new HashSet<>();

        for (Unit observer : bluforUnits) {
            List<String> visibleTargets = new ArrayList<>();

            for (Unit enemy : opforUnits) {
                double dist = Math.hypot(
                        (enemy.getLat() - observer.getLat()) * METERS_PER_LAT_DEGREE,
                        (enemy.getLon() - observer.getLon()) * METERS_PER_LON_DEGREE
                );

                if (canObserveTarget(observer, enemy, dist)) {
                    enemy.setDetectedByEnemy(true);
                    visibleTargets.add(enemy.getId());
                    currentTickSpotted.add(enemy.getId());

                    // Log contact on initial identification only
                    if (activeContactIds.add(enemy.getId())) {
                        log.info(">>> [COMBAT CONTACT] {} detected target {} at range {} m <<<",
                                observer.getCallsign(), enemy.getCallsign(), (int) dist);
                    }
                }
            }
            observer.setVisibleTargetIds(visibleTargets);
        }

        // Prune lost contacts
        activeContactIds.removeIf(id -> {
            if (!currentTickSpotted.contains(id)) {
                log.info("--- [CONTACT LOST] Lost visual contact with target ID {} ---", id);
                return true;
            }
            return false;
        });
    }

    private boolean canObserveTarget(Unit obs, Unit target, double distance) {
        double maxRange = target.isInCover() ? COVER_DETECTION_RANGE_METERS : obs.getMaxOpticsRangeMeters();
        if (distance > maxRange) {
            return false;
        }

        // Field of view cone check (beyond immediate close awareness radius)
        if (distance > 200.0) {
            double dLat = target.getLat() - obs.getLat();
            double dLon = target.getLon() - obs.getLon();
            double bearingToTarget = Math.toDegrees(Math.atan2(
                    dLon * METERS_PER_LON_DEGREE,
                    dLat * METERS_PER_LAT_DEGREE
            ));
            if (bearingToTarget < 0) bearingToTarget += 360.0;

            double angleDiff = Math.abs(bearingToTarget - obs.getHeading());
            if (angleDiff > 180.0) angleDiff = 360.0 - angleDiff;

            // Optics cone angle threshold (45 degrees off-boresight)
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
        Queue<Unit.Waypoint> waypoints = unit.getWaypoints();
        if (waypoints == null || waypoints.isEmpty()) {
            unit.setCurrentSpeedKmh(0.0);
            return;
        }

        Unit.Waypoint target = waypoints.peek();
        double dLat = target.lat() - unit.getLat();
        double dLon = target.lon() - unit.getLon();
        double dy = dLat * METERS_PER_LAT_DEGREE;
        double dx = dLon * METERS_PER_LON_DEGREE;
        double distance = Math.hypot(dx, dy);

        if (distance < WAYPOINT_REACHED_RADIUS_METERS) {
            waypoints.poll();
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
        if (cmd == null || cmd.unitIds() == null || cmd.unitIds().isEmpty()) {
            return;
        }

        for (String id : cmd.unitIds()) {
            units.stream()
                    .filter(u -> u.getId().equals(id))
                    .findFirst()
                    .ifPresent(u -> applyOrder(u, cmd));
        }
    }

    private void applyOrder(Unit unit, CommandDto cmd) {
        switch (cmd.type()) {
            case "ORDER_MOVE" -> {
                if (!cmd.isQueued()) {
                    unit.clearWaypoints();
                }
                if (cmd.targetLat() != null && cmd.targetLon() != null) {
                    unit.addWaypoint(cmd.targetLat(), cmd.targetLon());
                }
            }
            case "ORDER_HALT" -> {
                unit.clearWaypoints();
                unit.setCurrentSpeedKmh(0.0);
            }
            case "ORDER_CHANGE_SPEED" -> {
                if (cmd.speedKmh() != null) {
                    unit.setBaseSpeedKmh(cmd.speedKmh());
                }
            }
            default -> log.warn("[TALOS ENGINE] Unknown command type received: {}", cmd.type());
        }
    }

    public List<Unit> getUnits() {
        return units;
    }
}