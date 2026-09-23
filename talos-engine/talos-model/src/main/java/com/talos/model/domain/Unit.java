package com.talos.model.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Autonomous tactical simulation unit with kinematic state, sensors, and waypoints.
 */
@Getter
@Setter
@NoArgsConstructor
public class Unit {

    private String id;
    private String callsign;
    private String side; // "BLUFOR", "OPFOR", custom faction identifiers
    private String type; // e.g. "BTR_4E", "SOLDIER", "UAV", "RECON_PATROL"

    // Kinematics
    private double lat;
    private double lon;
    private double altitude;
    private double heading;
    private double baseSpeedKmh = 50.0;
    private double currentSpeedKmh = 0.0;

    // Navigation queue
    private Queue<Waypoint> waypoints = new ConcurrentLinkedQueue<>();

    // Optics, cover & sensor limits
    private double eyeHeightMeters = 2.5;
    private double targetHeightMeters = 2.2;
    private double maxOpticsRangeMeters = 1400.0;
    private double closeAwarenessRangeMeters = 250.0;
    private boolean inCover = false;
    private boolean detectedByEnemy = false;

    // Thread-safe list of identified target IDs for telemetry streaming
    private List<String> visibleTargetIds = new CopyOnWriteArrayList<>();

    public record Waypoint(double lat, double lon) {}

    public void addWaypoint(double lat, double lon) {
        this.waypoints.add(new Waypoint(lat, lon));
    }

    public void clearWaypoints() {
        this.waypoints.clear();
    }

    public void setVisibleTargetIds(List<String> targets) {
        this.visibleTargetIds = new CopyOnWriteArrayList<>(targets);
    }
}