package com.talos.model.domain;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Data
public class Unit {
    private String id;
    private String callsign;
    private String side; // "BLUFOR", "OPFOR", "GREENFOR"
    private String type; // "BTR_4E", "SOLDIER", "UAV", "RECON_PATROL"
    private double lat;
    private double lon;
    private double altitude;
    private double heading;
    private double baseSpeedKmh = 50.0;
    private double currentSpeedKmh = 0.0;
    private Queue<Waypoint> waypoints = new ConcurrentLinkedQueue<>();

    private double eyeHeightMeters = 2.5;
    private double targetHeightMeters = 2.2;
    private double maxOpticsRangeMeters = 1400.0;
    private double closeAwarenessRangeMeters = 250.0;
    private boolean inCover = false;
    private boolean detectedByEnemy = false;

    private List<String> visibleTargetIds = new ArrayList<>();

    public record Waypoint(double lat, double lon) {}

    public void addWaypoint(double lat, double lon) {
        this.waypoints.add(new Waypoint(lat, lon));
    }

    public void clearWaypoints() {
        this.waypoints.clear();
    }
}