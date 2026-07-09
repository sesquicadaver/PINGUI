-- PINGUI TimescaleDB schema (PostgreSQL-compatible).
-- Apply: psql "$PINGUI_TIMESCALE_DSN" -f scripts/timescale_schema.sql
-- When TimescaleDB extension is installed, run create_hypertable manually or
-- let the app auto-create hypertables on first connect.

CREATE TABLE IF NOT EXISTS pingui_schema_meta (
    version INTEGER NOT NULL
);

INSERT INTO pingui_schema_meta(version)
SELECT 1
WHERE NOT EXISTS (SELECT 1 FROM pingui_schema_meta);

CREATE TABLE IF NOT EXISTS pingui_ping_samples (
    time TIMESTAMPTZ NOT NULL,
    target_host TEXT NOT NULL,
    hop INTEGER NOT NULL,
    hop_ip TEXT NOT NULL,
    rtt_ms DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS pingui_route_events (
    time TIMESTAMPTZ NOT NULL,
    target_host TEXT NOT NULL,
    route_ips JSONB NOT NULL,
    route_changed BOOLEAN NOT NULL
);

-- Optional (requires TimescaleDB):
-- SELECT create_hypertable('pingui_ping_samples', 'time', if_not_exists => TRUE);
-- SELECT create_hypertable('pingui_route_events', 'time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_pingui_ping_target_time
    ON pingui_ping_samples (target_host, time DESC);

CREATE INDEX IF NOT EXISTS idx_pingui_route_target_time
    ON pingui_route_events (target_host, time DESC);
