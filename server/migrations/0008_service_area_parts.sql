-- Fast point-in-service-area lookups for real city boundaries.
--
-- A real city boundary has thousands of vertices. A GiST index only filters by bounding box; the
-- exact containment recheck then walks every vertex, and big geometries are TOASTed (stored
-- out of line), so each check reads extra pages. PostGIS's documented fix is ST_Subdivide: split
-- each boundary into small pieces (<= 128 vertices) with tight bounding boxes, index those, and
-- test points against the pieces. Fewer index hits, and each recheck is cheap.
--
-- ST_Segmentize first adds vertices every 1 km along the geodesic edges so the planar
-- subdivision doesn't distort a long edge (per the ST_Subdivide docs).

CREATE TABLE service_area_parts (
    id              BIGSERIAL PRIMARY KEY,
    service_area_id UUID NOT NULL REFERENCES service_areas (id) ON DELETE CASCADE,
    part            geometry(Geometry, 4326) NOT NULL
);
CREATE INDEX service_area_parts_gist ON service_area_parts USING GIST (part);
CREATE INDEX service_area_parts_area ON service_area_parts (service_area_id);

-- Keep parts in sync with service_areas.boundary, so app code and admins only ever edit the
-- boundary column.
CREATE FUNCTION service_areas_subdivide() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM service_area_parts WHERE service_area_id = NEW.id;
    INSERT INTO service_area_parts (service_area_id, part)
    SELECT NEW.id, ST_Subdivide(ST_Segmentize(NEW.boundary, 1000)::geometry, 128);
    RETURN NEW;
END;
$$;

CREATE TRIGGER service_areas_subdivide
    AFTER INSERT OR UPDATE OF boundary ON service_areas
    FOR EACH ROW EXECUTE FUNCTION service_areas_subdivide();

-- Backfill areas created before this migration.
INSERT INTO service_area_parts (service_area_id, part)
SELECT id, ST_Subdivide(ST_Segmentize(boundary, 1000)::geometry, 128)
FROM service_areas;
