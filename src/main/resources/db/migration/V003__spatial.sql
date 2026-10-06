CREATE EXTENSION IF NOT EXISTS postgis;
CREATE SCHEMA IF NOT EXISTS spatial;
CREATE TABLE spatial.place (
  id UUID PRIMARY KEY,
  parent_place_id UUID,
  place_type VARCHAR(40) NOT NULL,
  name_ar VARCHAR(255) NOT NULL,
  name_en VARCHAR(255),
  country_code CHAR(2) NOT NULL,
  geometry geometry(Geometry, 4326),
  centroid geography(Point, 4326),
  official_code VARCHAR(120),
  source VARCHAR(80),
  status VARCHAR(30) NOT NULL
);
CREATE INDEX idx_place_geometry ON spatial.place USING GIST (geometry);
CREATE INDEX idx_place_centroid ON spatial.place USING GIST (centroid);
