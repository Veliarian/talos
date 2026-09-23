package com.talos.gis.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tactical classification engine translating 100% of OpenStreetMap tags into military TTX properties.
 */
@Component
public class OsmTaxonomyClassifier {

    public record TacticalSpecs(
            String category,
            String subcategory,
            String typeKey,
            String typeValue,
            float speedModifierWheeled,
            float speedModifierTracked,
            Float visibilityMeters,
            float coverDefensePercent,
            Float heightMeters,
            Float widthMeters,
            String description
    ) {}

    public TacticalSpecs classify(Map<String, String> tags) {
        // 1. Highways & Roads (100% coverage)
        if (tags.containsKey("highway")) {
            return classifyHighway(tags);
        }

        // 2. Railways
        if (tags.containsKey("railway")) {
            return new TacticalSpecs(
                    "ROAD", "RAILWAY_TRACK", "railway", tags.get("railway"),
                    0.1f, 0.4f, null, 15.0f,
                    null, 4.0f, "Залізнична колія / насип"
            );
        }

        // 3. Buildings & Structures
        if (tags.containsKey("building")) {
            return classifyBuilding(tags);
        }

        // 4. Waterways & Water Bodies
        if (tags.containsKey("waterway") || "water".equals(tags.get("natural")) || "reservoir".equals(tags.get("landuse"))) {
            return classifyWater(tags);
        }

        // 5. Vegetation, Forestry & Land Cover
        if (tags.containsKey("natural") || tags.containsKey("landuse")) {
            TacticalSpecs land = classifyLanduseAndNatural(tags);
            if (land != null) return land;
        }

        // 6. Default Open Ground
        return new TacticalSpecs(
                "SOIL", "OPEN_TERRAIN", "surface", "ground",
                0.7f, 0.85f, null, 5.0f,
                null, null, "Відкрита місцевість / ґрунт"
        );
    }

    private TacticalSpecs classifyHighway(Map<String, String> tags) {
        String hw = tags.getOrDefault("highway", "unclassified");
        float width = resolveRoadWidth(tags, hw);

        return switch (hw) {
            case "motorway", "motorway_link", "trunk", "trunk_link" -> new TacticalSpecs(
                    "ROAD", "HIGHWAY_MOTORWAY", "highway", hw,
                    1.2f, 1.0f, null, 5.0f,
                    null, width, "Автомагістраль з капітальним покриттям"
            );
            case "primary", "primary_link" -> new TacticalSpecs(
                    "ROAD", "HIGHWAY_PRIMARY", "highway", hw,
                    1.0f, 0.9f, null, 5.0f,
                    null, width, "Магістральна дорога державного значення"
            );
            case "secondary", "secondary_link", "tertiary", "tertiary_link" -> new TacticalSpecs(
                    "ROAD", "HIGHWAY_SECONDARY", "highway", hw,
                    0.85f, 0.8f, null, 10.0f,
                    null, width, "Регіональна або міжселищна дорога"
            );
            case "residential", "living_street", "service" -> new TacticalSpecs(
                    "ROAD", "STREET_RESIDENTIAL", "highway", hw,
                    0.65f, 0.7f, null, 25.0f,
                    null, width, "Селищна / міська вулиця"
            );
            case "track" -> new TacticalSpecs(
                    "ROAD", "TRACK_UNPAVED", "highway", hw,
                    0.35f, 0.7f, null, 15.0f,
                    null, width, "Ґрунтова польова / лісова дорога"
            );
            case "path", "footway", "bridleway", "cycleway" -> new TacticalSpecs(
                    "ROAD", "PATH_PEDESTRIAN", "highway", hw,
                    0.0f, 0.15f, null, 15.0f,
                    null, width, "Стежка (непрохідна для автотранспорту)"
            );
            default -> new TacticalSpecs(
                    "ROAD", "ROAD_GENERAL", "highway", hw,
                    0.7f, 0.75f, null, 10.0f,
                    null, width, "Дорога загального користування"
            );
        };
    }

    private TacticalSpecs classifyBuilding(Map<String, String> tags) {
        String b = tags.getOrDefault("building", "yes");
        float height = resolveBuildingHeight(tags);

        return switch (b) {
            case "industrial", "warehouse", "hangar", "factory" -> new TacticalSpecs(
                    "BUILDING", "BUILDING_INDUSTRIAL", "building", b,
                    0.0f, 0.0f, 0.0f, 85.0f,
                    height, null, "Промислова споруда / цех / ангар"
            );
            case "bunker", "fortification", "military" -> new TacticalSpecs(
                    "BUILDING", "FORTIFICATION_BUNKER", "building", b,
                    0.0f, 0.0f, 0.0f, 95.0f,
                    height, null, "Капітальний бункер / укріплення"
            );
            default -> new TacticalSpecs(
                    "BUILDING", "BUILDING_RESIDENTIAL", "building", b,
                    0.0f, 0.0f, 0.0f, 75.0f,
                    height, null, "Житлова / адміністративна споруда"
            );
        };
    }

    private TacticalSpecs classifyLanduseAndNatural(Map<String, String> tags) {
        String natural = tags.get("natural");
        String landuse = tags.get("landuse");

        // Dense forests and woods
        if ("wood".equals(natural) || "forest".equals(landuse)) {
            return new TacticalSpecs(
                    "VEGETATION", "FOREST_DENSE", "natural", "wood",
                    0.05f, 0.35f, 35.0f, 60.0f,
                    15.0f, null, "Густий лісовий масив"
            );
        }
        // Scrub, bushes and low vegetation
        if ("scrub".equals(natural) || "heath".equals(natural)) {
            return new TacticalSpecs(
                    "VEGETATION", "SCRUB_BRUSH", "natural", "scrub",
                    0.35f, 0.65f, 80.0f, 35.0f,
                    2.5f, null, "Чагарники / зарості"
            );
        }
        // Settlements & Residential village sectors
        if ("residential".equals(landuse) || "village".equals(landuse)) {
            return new TacticalSpecs(
                    "BUILDING", "SETTLEMENT_ZONE", "landuse", "residential",
                    0.6f, 0.65f, 150.0f, 45.0f,
                    null, null, "Зона забудови населеного пункту"
            );
        }
        // Industrial zones
        if ("industrial".equals(landuse) || "commercial".equals(landuse)) {
            return new TacticalSpecs(
                    "BUILDING", "ZONE_INDUSTRIAL", "landuse", "industrial",
                    0.5f, 0.6f, 100.0f, 65.0f,
                    null, null, "Промислова / комерційна зона"
            );
        }
        // Meadows, pastures and grasslands
        if ("meadow".equals(landuse) || "grassland".equals(natural) || "grass".equals(landuse)) {
            return new TacticalSpecs(
                    "VEGETATION", "GRASSLAND_MEADOW", "landuse", "meadow",
                    0.7f, 0.9f, null, 5.0f,
                    null, null, "Луг / пасовище"
            );
        }
        // Agricultural farmland
        if ("farmland".equals(landuse) || "orchard".equals(landuse) || "allotments".equals(landuse)) {
            return new TacticalSpecs(
                    "VEGETATION", "FARMLAND", "landuse", landuse,
                    0.5f, 0.8f, null, 10.0f,
                    null, null, "Сільськогосподарське поле / рілля"
            );
        }
        // Wetlands and bogs
        if ("wetland".equals(natural) || "marsh".equals(natural)) {
            return new TacticalSpecs(
                    "VEGETATION", "WETLAND_MARSH", "natural", "wetland",
                    0.0f, 0.1f, null, 25.0f,
                    null, null, "Болото / мочар"
            );
        }
        return null;
    }

    private TacticalSpecs classifyWater(Map<String, String> tags) {
        String waterway = tags.get("waterway");
        String natural = tags.get("natural");

        if ("river".equals(waterway) || "water".equals(natural) || "reservoir".equals(tags.get("landuse"))) {
            return new TacticalSpecs(
                    "WATER", "WATER_MAJOR", "natural", "water",
                    0.0f, 0.0f, null, 0.0f,
                    null, 25.0f, "Річка / озеро / водойма"
            );
        }
        return new TacticalSpecs(
                "WATER", "WATER_STREAM", "waterway", waterway != null ? waterway : "stream",
                0.2f, 0.4f, null, 0.0f,
                null, 3.5f, "Струмок / канава / потік"
        );
    }

    private float resolveRoadWidth(Map<String, String> tags, String highway) {
        if (tags.containsKey("width")) {
            try {
                return Float.parseFloat(tags.get("width").replaceAll("[^0-9.]", ""));
            } catch (Exception ignored) {}
        }
        return switch (highway) {
            case "motorway", "trunk" -> 16.0f;
            case "primary" -> 10.0f;
            case "secondary" -> 8.0f;
            case "tertiary" -> 7.0f;
            case "residential" -> 6.0f;
            case "service", "track" -> 4.0f;
            case "path", "footway" -> 1.5f;
            default -> 5.0f;
        };
    }

    private float resolveBuildingHeight(Map<String, String> tags) {
        if (tags.containsKey("height")) {
            try {
                return Float.parseFloat(tags.get("height").replaceAll("[^0-9.]", ""));
            } catch (Exception ignored) {}
        }
        if (tags.containsKey("building:levels")) {
            try {
                int levels = Integer.parseInt(tags.get("building:levels").replaceAll("[^0-9]", ""));
                return Math.max(3.0f, levels * 3.2f);
            } catch (Exception ignored) {}
        }
        return 7.0f;
    }
}