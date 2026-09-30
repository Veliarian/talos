// forestGenerator.ts
// Tactical proximity tree placement inside forest polygons with building collision exclusion

export interface TreeInstancePoint {
    lon: number;
    lat: number;
    scale: number;
    treeType: 'PINE' | 'DECIDUOUS';
}

export interface BuildingObstacle {
    minLon: number;
    maxLon: number;
    minLat: number;
    maxLat: number;
    ring: number[][];
}

const FOREST_OSM_VALUES = new Set([
    'wood',
    'forest',
    'tree_row',
    'orchard',
    'trees',
    'woodland',
    'park',
    'garden'
]);

export function isTreeVegetation(typeValue?: string): boolean {
    if (!typeValue) return false;
    return FOREST_OSM_VALUES.has(typeValue.toLowerCase().trim());
}

function isPointInRing(x: number, y: number, ring: number[][]): boolean {
    let inside = false;
    const len = ring.length;
    for (let i = 0, j = len - 1; i < len; j = i++) {
        const xi = ring[i][0], yi = ring[i][1];
        const xj = ring[j][0], yj = ring[j][1];

        const intersect = ((yi > y) !== (yj > y)) &&
            (x < ((xj - xi) * (y - yi)) / (yj - yi) + xi);
        if (intersect) inside = !inside;
    }
    return inside;
}

function isPointInPolygonWithHoles(lon: number, lat: number, rings: number[][][]): boolean {
    if (!rings || rings.length === 0) return false;
    const outer = rings[0];
    if (!outer || outer.length < 3 || !isPointInRing(lon, lat, outer)) return false;

    for (let h = 1; h < rings.length; h++) {
        const hole = rings[h];
        if (hole && hole.length >= 3 && isPointInRing(lon, lat, hole)) {
            return false;
        }
    }
    return true;
}

/**
 * Extracts footprint obstacles of actual buildings within the tactical sector
 */
export function extractBuildingObstacles(
    features: any[],
    centerLon: number,
    centerLat: number,
    radiusDeg: number
): BuildingObstacle[] {
    const obstacles: BuildingObstacle[] = [];
    const minLon = centerLon - radiusDeg;
    const maxLon = centerLon + radiusDeg;
    const minLat = centerLat - radiusDeg;
    const maxLat = centerLat + radiusDeg;

    // ~2.0 meters clearance buffer in degrees
    const BUFFER_DEG = 2.0 / 111000.0;

    for (const f of features) {
        const props = f.properties || {};
        // Match actual structural buildings, not district boundary polygons
        if (props.category !== 'BUILDING' || props.typeKey !== 'building') continue;

        const geom = f.geometry;
        if (!geom) continue;

        let rings: number[][][] = [];
        if (geom.type === 'Polygon') {
            rings = [geom.coordinates[0]];
        } else if (geom.type === 'MultiPolygon') {
            rings = geom.coordinates.map((p: any) => p[0]);
        }

        for (const ring of rings) {
            if (!ring || ring.length < 3) continue;

            let bMinLon = Infinity, bMaxLon = -Infinity;
            let bMinLat = Infinity, bMaxLat = -Infinity;

            for (let i = 0; i < ring.length; i++) {
                const lon = ring[i][0];
                const lat = ring[i][1];
                if (lon < bMinLon) bMinLon = lon;
                if (lon > bMaxLon) bMaxLon = lon;
                if (lat < bMinLat) bMinLat = lat;
                if (lat > bMaxLat) bMaxLat = lat;
            }

            // Exclude buildings outside tactical sector
            if (bMaxLon < minLon || bMinLon > maxLon || bMaxLat < minLat || bMinLat > maxLat) {
                continue;
            }

            obstacles.push({
                minLon: bMinLon - BUFFER_DEG,
                maxLon: bMaxLon + BUFFER_DEG,
                minLat: bMinLat - BUFFER_DEG,
                maxLat: bMaxLat + BUFFER_DEG,
                ring
            });
        }
    }

    return obstacles;
}

function isPointInsideBuildings(lon: number, lat: number, obstacles: BuildingObstacle[]): boolean {
    for (let i = 0; i < obstacles.length; i++) {
        const obs = obstacles[i];
        // Fast AABB rejection test (takes ~2 nanoseconds)
        if (lon >= obs.minLon && lon <= obs.maxLon && lat >= obs.minLat && lat <= obs.maxLat) {
            if (isPointInRing(lon, lat, obs.ring)) {
                return true; // Point is inside building footprint
            }
        }
    }
    return false;
}

function seededRandom(seed: number): () => number {
    let s = Math.abs(seed) % 2147483647;
    if (s <= 0) s += 1;
    return () => {
        s = (s * 16807) % 2147483647;
        return (s - 1) / 2147483646;
    };
}

export function generateSectorTreePoints(
    geometry: { type: string; coordinates: any },
    centerLon: number,
    centerLat: number,
    radiusDeg: number,
    densityPercent = 75,
    osmValue = 'wood',
    maxBudget = 400,
    buildingObstacles: BuildingObstacle[] = [] // <--- Passed building obstacles
): TreeInstancePoint[] {
    const points: TreeInstancePoint[] = [];
    if (!isTreeVegetation(osmValue)) return points;

    let polygonList: number[][][][] = [];
    if (geometry.type === 'Polygon') {
        polygonList = [geometry.coordinates];
    } else if (geometry.type === 'MultiPolygon') {
        polygonList = geometry.coordinates;
    } else {
        return points;
    }

    const isConiferous = osmValue === 'coniferous' || osmValue === 'wood';

    const bubbleMinLon = centerLon - radiusDeg;
    const bubbleMaxLon = centerLon + radiusDeg;
    const bubbleMinLat = centerLat - radiusDeg;
    const bubbleMaxLat = centerLat + radiusDeg;

    for (const rings of polygonList) {
        if (!rings || rings.length === 0) continue;
        const outer = rings[0];
        if (!outer || outer.length < 3) continue;

        let polyMinLon = Infinity, polyMaxLon = -Infinity;
        let polyMinLat = Infinity, polyMaxLat = -Infinity;

        for (let i = 0; i < outer.length; i++) {
            const lon = outer[i][0];
            const lat = outer[i][1];
            if (lon < polyMinLon) polyMinLon = lon;
            if (lon > polyMaxLon) polyMaxLon = lon;
            if (lat < polyMinLat) polyMinLat = lat;
            if (lat > polyMaxLat) polyMaxLat = lat;
        }

        const interMinLon = Math.max(polyMinLon, bubbleMinLon);
        const interMaxLon = Math.min(polyMaxLon, bubbleMaxLon);
        const interMinLat = Math.max(polyMinLat, bubbleMinLat);
        const interMaxLat = Math.min(polyMaxLat, bubbleMaxLat);

        const deltaLon = interMaxLon - interMinLon;
        const deltaLat = interMaxLat - interMinLat;
        if (deltaLon <= 0 || deltaLat <= 0) continue;

        const areaM2 = (deltaLon * 111000.0 * Math.cos((centerLat * Math.PI) / 180.0)) * (deltaLat * 111000.0);
        const spacingM2 = 75.0 * (100.0 / Math.max(densityPercent, 35));
        const targetCount = Math.min(maxBudget, Math.max(20, Math.floor(areaM2 / spacingM2)));
        const maxAttempts = targetCount * 12;

        const rng = seededRandom(Math.floor((interMinLon + interMinLat) * 100000));
        let placed = 0;
        let attempts = 0;

        while (placed < targetCount && attempts < maxAttempts) {
            attempts++;
            const cLon = interMinLon + rng() * deltaLon;
            const cLat = interMinLat + rng() * deltaLat;

            const dLonM = (cLon - centerLon) * 111000 * Math.cos((centerLat * Math.PI) / 180);
            const dLatM = (cLat - centerLat) * 111000;
            if (dLonM * dLonM + dLatM * dLatM > (radiusDeg * 111000) * (radiusDeg * 111000)) {
                continue;
            }

            // 1. Must be inside forest
            if (isPointInPolygonWithHoles(cLon, cLat, rings)) {
                // 2. CRITICAL: Reject candidates falling inside building footprints or on roofs
                if (isPointInsideBuildings(cLon, cLat, buildingObstacles)) {
                    continue;
                }

                const scale = 0.85 + rng() * 0.45;
                const treeType = isConiferous
                    ? (rng() > 0.25 ? 'PINE' : 'DECIDUOUS')
                    : (rng() > 0.25 ? 'DECIDUOUS' : 'PINE');

                points.push({
                    lon: cLon,
                    lat: cLat,
                    scale,
                    treeType
                });
                placed++;
            }
        }
    }

    return points;
}