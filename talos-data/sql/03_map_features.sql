-- ============================================================================
-- TALOS SIMULATION PLATFORM: MAP VECTOR FEATURES SCHEMA
-- Bounded vector geometry objects (roads, forests, buildings) linked to maps.
-- ============================================================================

CREATE TABLE IF NOT EXISTS map_features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    map_id UUID NOT NULL REFERENCES maps(id) ON DELETE CASCADE,
    osm_id BIGINT,
    name VARCHAR(255),
    category VARCHAR(50) NOT NULL, -- 'ROAD', 'VEGETATION', 'BUILDING', 'WATER', 'SOIL'
    type_key VARCHAR(50) NOT NULL, -- 'highway', 'building', 'natural', 'landuse'
    type_value VARCHAR(50) NOT NULL, -- 'primary', 'residential', 'wood', etc.
    geometry_type VARCHAR(50) NOT NULL, -- 'LineString', 'Polygon'
    geojson TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_map_features_map_id ON map_features(map_id);
CREATE INDEX IF NOT EXISTS idx_map_features_category ON map_features(category);
