import { type ShallowRef } from 'vue';
import {
    Viewer,
    Primitive,
    GroundPrimitive,
    GeometryInstance,
    PolygonGeometry,
    PolygonHierarchy,
    Cartesian3,
    Color,
    ColorGeometryInstanceAttribute,
    PerInstanceColorAppearance,
    GroundPolylinePrimitive,
    GroundPolylineGeometry,
    PolylineColorAppearance,
    BillboardCollection,
    DistanceDisplayCondition,
    HeightReference, Cartographic
} from 'cesium';
import type { TacticalModifierData } from '../types';

export function useVectorPrimitives(viewer: ShallowRef<Viewer | null>) {
    let buildingPrimitive: Primitive | null = null;
    let roadPrimitive: GroundPolylinePrimitive | null = null;
    let waterPrimitive: GroundPrimitive | null = null;
    let vegetationPrimitive: GroundPrimitive | null = null;
    let treeBillboards: BillboardCollection | null = null;

    const clearPrimitives = () => {
        const v = viewer.value;
        if (!v) return;

        if (buildingPrimitive) { v.scene.primitives.remove(buildingPrimitive); buildingPrimitive = null; }
        if (roadPrimitive) { v.scene.primitives.remove(roadPrimitive); roadPrimitive = null; }
        if (waterPrimitive) { v.scene.primitives.remove(waterPrimitive); waterPrimitive = null; }
        if (vegetationPrimitive) { v.scene.primitives.remove(vegetationPrimitive); vegetationPrimitive = null; }
        if (treeBillboards) { v.scene.primitives.remove(treeBillboards); treeBillboards = null; }
    };

    const renderFeaturesBatched = (
        geoJson: any,
        modifiersList: TacticalModifierData[],
        is3D: boolean,
        highlightedOsmValue: string | null = null
    ) => {
        const v = viewer.value;
        if (!v || !geoJson?.features) return;

        clearPrimitives();

        const buildingInstances: GeometryInstance[] = [];
        const roadInstances: GeometryInstance[] = [];
        const waterInstances: GeometryInstance[] = [];
        const vegetationInstances: GeometryInstance[] = [];

        if (is3D) {
            treeBillboards = v.scene.primitives.add(new BillboardCollection({ scene: v.scene }));
        }

        const features = geoJson.features;

        for (const f of features) {
            const geom = f.geometry;
            const props = f.properties || {};
            const category = props.category;
            const typeValue = props.typeValue;
            const featureId = props.id || crypto.randomUUID();

            const isHighlighted = (highlightedOsmValue !== null && typeValue === highlightedOsmValue);
            const isDimmed = (highlightedOsmValue !== null && typeValue !== highlightedOsmValue);

            const mod = modifiersList.find(m => m.osmValue === typeValue);
            const colorHex = mod?.color2d || getDefaultColor(category);

            // Resolve dynamic color based on highlight state
            let baseColor: Color;
            if (isHighlighted) {
                baseColor = Color.fromCssColorString('#00ffff');
            } else if (isDimmed) {
                baseColor = Color.fromCssColorString(colorHex).withAlpha(0.2);
            } else {
                baseColor = Color.fromCssColorString(colorHex);
            }

            // 1. BUILDINGS
            if (category === 'BUILDING' && geom.type === 'Polygon' && geom.coordinates?.[0]?.length >= 3) {
                const positions = coordsToCartesianArray(geom.coordinates[0]);
                const heightMeters = props.heightMeters || 6.5;

                let groundAlt = props.groundAlt;
                if ((groundAlt === undefined || groundAlt === null || groundAlt === 0) && is3D) {
                    const centroidCarto = Cartographic.fromDegrees(geom.coordinates[0][0][0], geom.coordinates[0][0][1]);
                    groundAlt = v.scene.globe.getHeight(centroidCarto) || 120.0;
                }
                const baseAlt = is3D ? (groundAlt || 0.0) : 0.0;

                const foundationAlt = is3D ? Math.max(0.0, baseAlt - 1.5) : 0.0;
                const roofAlt = is3D ? (baseAlt + heightMeters) : 0.0;

                const instance = new GeometryInstance({
                    geometry: new PolygonGeometry({
                        polygonHierarchy: new PolygonHierarchy(positions),
                        height: foundationAlt,
                        extrudedHeight: roofAlt,
                        vertexFormat: PerInstanceColorAppearance.VERTEX_FORMAT
                    }),
                    attributes: {
                        color: ColorGeometryInstanceAttribute.fromColor(baseColor)
                    },
                    id: featureId
                });
                buildingInstances.push(instance);
            }

            // 2. ROADS
            else if (category === 'ROAD' && (geom.type === 'LineString' || geom.type === 'MultiLineString')) {
                const lineCoords = geom.type === 'LineString' ? [geom.coordinates] : geom.coordinates;
                for (const coords of lineCoords) {
                    if (coords.length < 2) continue;
                    const positions = coordsToCartesianArray(coords);

                    const instance = new GeometryInstance({
                        geometry: new GroundPolylineGeometry({
                            positions,
                            width: isHighlighted ? 7.0 : (props.widthMeters ? Math.max(3.0, props.widthMeters * 0.8) : 4.0)
                        }),
                        attributes: {
                            color: ColorGeometryInstanceAttribute.fromColor(baseColor)
                        },
                        id: featureId
                    });
                    roadInstances.push(instance);
                }
            }

            // 3. WATER
            else if ((category === 'RIVER' || category === 'OPEN_WATER' || category === 'WATER') && geom.type === 'Polygon') {
                const positions = coordsToCartesianArray(geom.coordinates[0]);
                const instance = new GeometryInstance({
                    geometry: new PolygonGeometry({
                        polygonHierarchy: new PolygonHierarchy(positions),
                        vertexFormat: PerInstanceColorAppearance.VERTEX_FORMAT
                    }),
                    attributes: {
                        color: ColorGeometryInstanceAttribute.fromColor(isHighlighted ? Color.fromCssColorString('#00ffff') : Color.fromCssColorString('#0284c7').withAlpha(isDimmed ? 0.2 : 0.75))
                    },
                    id: featureId
                });
                waterInstances.push(instance);
            }

            // 4. VEGETATION
            else if (category === 'VEGETATION' && geom.type === 'Polygon') {
                const positions = coordsToCartesianArray(geom.coordinates[0]);
                const instance = new GeometryInstance({
                    geometry: new PolygonGeometry({
                        polygonHierarchy: new PolygonHierarchy(positions),
                        vertexFormat: PerInstanceColorAppearance.VERTEX_FORMAT
                    }),
                    attributes: {
                        color: ColorGeometryInstanceAttribute.fromColor(isHighlighted ? Color.fromCssColorString('#00ffff') : baseColor.withAlpha(isDimmed ? 0.2 : 0.6))
                    },
                    id: featureId
                });
                vegetationInstances.push(instance);

                if (is3D && treeBillboards && (typeValue === 'wood' || typeValue === 'forest') && !isDimmed) {
                    scatterTreesInPolygon(treeBillboards, geom.coordinates[0], props.heightMeters || 18.0);
                }
            }
        }

        if (buildingInstances.length > 0) {
            buildingPrimitive = v.scene.primitives.add(new Primitive({
                geometryInstances: buildingInstances,
                appearance: new PerInstanceColorAppearance({ closed: true, translucent: false }),
                asynchronous: false
            }));
        }

        if (roadInstances.length > 0) {
            roadPrimitive = v.scene.primitives.add(new GroundPolylinePrimitive({
                geometryInstances: roadInstances,
                appearance: new PolylineColorAppearance(),
                asynchronous: false
            }));
        }

        if (waterInstances.length > 0) {
            waterPrimitive = v.scene.primitives.add(new GroundPrimitive({
                geometryInstances: waterInstances,
                appearance: new PerInstanceColorAppearance({ closed: false, translucent: true }),
                asynchronous: false
            }));
        }

        if (vegetationInstances.length > 0) {
            vegetationPrimitive = v.scene.primitives.add(new GroundPrimitive({
                geometryInstances: vegetationInstances,
                appearance: new PerInstanceColorAppearance({ closed: false, translucent: true }),
                asynchronous: false
            }));
        }

        v.scene.requestRender();
    };

    const scatterTreesInPolygon = (billboards: BillboardCollection, ring: number[][], heightMeters: number) => {
        const sampleCount = Math.min(8, Math.max(2, Math.floor(ring.length / 3)));
        for (let i = 0; i < sampleCount; i++) {
            const pt = ring[i * 2 % ring.length];
            billboards.add({
                position: Cartesian3.fromDegrees(pt[0], pt[1]),
                color: Color.fromCssColorString('#14532d').withAlpha(0.9),
                pixelOffset: Cartesian3.ZERO,
                width: heightMeters * 0.6,
                height: heightMeters,
                heightReference: HeightReference.CLAMP_TO_GROUND,
                distanceDisplayCondition: new DistanceDisplayCondition(10.0, 3500.0)
            });
        }
    };

    const coordsToCartesianArray = (coords: number[][]): Cartesian3[] => {
        const arr: Cartesian3[] = [];
        for (const pt of coords) {
            arr.push(Cartesian3.fromDegrees(pt[0], pt[1]));
        }
        return arr;
    };

    const getDefaultColor = (category: string) => {
        switch (category) {
            case 'ROAD': return '#f59e0b';
            case 'BRIDGE': return '#ffffff';
            case 'RIVER':
            case 'OPEN_WATER':
            case 'WATER': return '#0284c7';
            case 'VEGETATION': return '#15803d';
            case 'BUILDING': return '#dc2626';
            default: return '#78350f';
        }
    };

    return {
        renderFeaturesBatched,
        clearPrimitives
    };
}