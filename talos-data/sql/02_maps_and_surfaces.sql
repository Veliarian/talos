-- ============================================================================
-- TALOS SIMULATION PLATFORM: MAPS & SURFACE CHARACTERISTICS SCHEMA
-- Step 1.1: Local Theater Management, Basemap Layers & Environmental Modifiers
-- ============================================================================

-- Ensure required extensions are present
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- ----------------------------------------------------------------------------
-- Table: maps
-- Defines the strictly bounded operational theater (e.g. 20x20 km bounding box)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS maps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,

    -- Geographic Bounding Box (WGS84 EPSG:4326)
    min_lat DOUBLE PRECISION NOT NULL,
    max_lat DOUBLE PRECISION NOT NULL,
    min_lon DOUBLE PRECISION NOT NULL,
    max_lon DOUBLE PRECISION NOT NULL,

    -- Theater center and dimension
    center_lat DOUBLE PRECISION NOT NULL,
    center_lon DOUBLE PRECISION NOT NULL,
    size_km DOUBLE PRECISION NOT NULL DEFAULT 20.0,

    -- Local storage path for elevation DEM GeoTIFF (e.g. talos-data/maps/{id}/terrain.tif)
    dem_file_path VARCHAR(512),

    -- Ingestion and readiness status: 'CREATED', 'DOWNLOADING', 'PROCESSING', 'READY', 'FAILED'
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',

    -- PostGIS spatial polygon representing the exact bounding box for spatial queries
    bbox_geom GEOMETRY(Polygon, 4326),

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for spatial intersection queries against bounding boxes
CREATE INDEX IF NOT EXISTS idx_maps_bbox_geom ON maps USING GIST(bbox_geom);

-- ----------------------------------------------------------------------------
-- Table: map_layers
-- Supports multiple raster/vector baselayers per map (Satellite, Topo, Tactical)
-- Stored locally on the server for 100% offline usage.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS map_layers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    map_id UUID NOT NULL REFERENCES maps(id) ON DELETE CASCADE,

    -- Layer type identifier: 'SATELLITE', 'TOPOGRAPHIC', 'TACTICAL'
    layer_type VARCHAR(50) NOT NULL,

    -- Tile storage format: 'XYZ_DIR' (directory of png/jpg), 'MBTILES', etc.
    storage_format VARCHAR(50) NOT NULL DEFAULT 'XYZ_DIR',

    -- Relative local server directory (e.g. talos-data/maps/{map_id}/tiles/satellite/)
    local_path VARCHAR(512) NOT NULL,

    -- Supported zoom range for this local bounding box
    min_zoom INT NOT NULL DEFAULT 12,
    max_zoom INT NOT NULL DEFAULT 17,

    -- Download / Processing status
    status VARCHAR(50) NOT NULL DEFAULT 'READY',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_map_layer UNIQUE (map_id, layer_type)
);

CREATE INDEX IF NOT EXISTS idx_map_layers_map_id ON map_layers(map_id);

-- ----------------------------------------------------------------------------
-- Table: default_surface_modifiers
-- Global reference template for terrain and road movement/concealment rules.
-- Cloned into map_surface_modifiers whenever a new map is generated.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS default_surface_modifiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(50) NOT NULL,    -- 'ROAD', 'VEGETATION', 'WATER', 'SOIL'
    osm_key VARCHAR(50) NOT NULL,     -- 'highway', 'natural', 'landuse', 'waterway'
    osm_value VARCHAR(50) NOT NULL,   -- 'primary', 'track', 'forest', 'wetland', etc.
    description VARCHAR(255),

    -- Speed multipliers (1.0 = base speed, 0.5 = 50% speed, 0.0 = impassable NO-GO)
    speed_modifier_wheeled REAL NOT NULL DEFAULT 1.0,
    speed_modifier_tracked REAL NOT NULL DEFAULT 1.0,

    -- Maximum optical visibility distance inside this obstacle (NULL = unrestricted)
    visibility_meters REAL DEFAULT NULL,

    -- Ballistic / Blast mitigation percentage provided by this terrain cover
    cover_defense_percent REAL NOT NULL DEFAULT 0.0,

    CONSTRAINT uq_default_surface UNIQUE (osm_key, osm_value)
);

-- ----------------------------------------------------------------------------
-- Table: map_surface_modifiers
-- Map-specific surface modifiers. Can be tweaked per map by the instructor/operator.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS map_surface_modifiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    map_id UUID NOT NULL REFERENCES maps(id) ON DELETE CASCADE,

    category VARCHAR(50) NOT NULL,
    osm_key VARCHAR(50) NOT NULL,
    osm_value VARCHAR(50) NOT NULL,
    description VARCHAR(255),

    speed_modifier_wheeled REAL NOT NULL DEFAULT 1.0,
    speed_modifier_tracked REAL NOT NULL DEFAULT 1.0,
    visibility_meters REAL DEFAULT NULL,
    cover_defense_percent REAL NOT NULL DEFAULT 0.0,

    CONSTRAINT uq_map_surface UNIQUE (map_id, osm_key, osm_value)
);

CREATE INDEX IF NOT EXISTS idx_map_surface_map_id ON map_surface_modifiers(map_id);

-- ----------------------------------------------------------------------------
-- Trigger: Automatically update bbox_geom and updated_at on map insert/update
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_update_map_metadata()
RETURNS TRIGGER AS $$
BEGIN
    NEW.bbox_geom = ST_MakeEnvelope(NEW.min_lon, NEW.min_lat, NEW.max_lon, NEW.max_lat, 4326);
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_maps_before_upsert ON maps;
CREATE TRIGGER trg_maps_before_upsert
BEFORE INSERT OR UPDATE ON maps
FOR EACH ROW EXECUTE FUNCTION trg_update_map_metadata();

-- ----------------------------------------------------------------------------
-- Seed Data: Realistic military movement & concealment presets
-- ----------------------------------------------------------------------------
INSERT INTO default_surface_modifiers
    (category, osm_key, osm_value, description, speed_modifier_wheeled, speed_modifier_tracked, visibility_meters, cover_defense_percent)
VALUES
    -- High-speed roads
    ('ROAD', 'highway', 'motorway',     'Paved Highway / Motorway',           1.00, 1.00, NULL,   0.0),
    ('ROAD', 'highway', 'trunk',        'Trunk Road',                         1.00, 1.00, NULL,   0.0),
    ('ROAD', 'highway', 'primary',      'Primary Paved Road',                 0.95, 0.95, NULL,   0.0),
    ('ROAD', 'highway', 'secondary',    'Secondary Road',                     0.90, 0.90, NULL,   0.0),
    ('ROAD', 'highway', 'tertiary',     'Tertiary Paved Road',                0.85, 0.85, NULL,   0.0),
    ('ROAD', 'highway', 'residential',  'Urban / Settlement Road',            0.75, 0.75, NULL,  10.0),

    -- Tactical & Off-road tracks
    ('ROAD', 'highway', 'unclassified', 'Rural Unpaved Road',                 0.70, 0.85, NULL,   0.0),
    ('ROAD', 'highway', 'track',        'Dirt / Forest Track',                0.55, 0.75, NULL,   5.0),

    -- Vegetation & Concealment (Zelenka)
    ('VEGETATION', 'natural', 'wood',       'Dense Natural Forest',           0.20, 0.40, 150.0, 50.0),
    ('VEGETATION', 'landuse', 'forest',     'Managed Forest / Tree Plantation',0.25, 0.45, 200.0, 40.0),
    ('VEGETATION', 'natural', 'scrub',      'Dense Bushes / Scrubland',       0.45, 0.65, 350.0, 25.0),
    ('VEGETATION', 'natural', 'tree_row',   'Tree Row / Windbreak Hedge',     0.70, 0.80, 250.0, 30.0),

    -- Water barriers (NO-GO for standard vehicles)
    ('WATER', 'natural',  'water',      'Inland Lake / Deep Water',           0.00, 0.00, NULL,   0.0),
    ('WATER', 'waterway', 'river',      'River Waterway',                     0.00, 0.00, NULL,   0.0),
    ('WATER', 'natural',  'wetland',    'Swamp / Marshland',                  0.05, 0.20, NULL,  10.0),

    -- Soils and open terrain
    ('SOIL', 'landuse', 'farmland',     'Agricultural Cultivated Field',      0.50, 0.75, NULL,   0.0),
    ('SOIL', 'natural', 'sand',         'Loose Sand',                         0.35, 0.70, NULL,   0.0)
ON CONFLICT (osm_key, osm_value) DO NOTHING;
