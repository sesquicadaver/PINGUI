package io.pingui.api;

import io.pingui.AppInfo;
import io.pingui.geoip.RouteGeoEnrichment;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.SessionStore;
import java.util.List;
import java.util.Objects;

/** JSON serializers for the read-only runbook API (P15-040 / P36-009). */
final class ReadOnlyApiJson {
    private ReadOnlyApiJson() {}

    static String hostsDocument(SessionStore store) {
        Objects.requireNonNull(store, "store");
        StringBuilder json = new StringBuilder(128);
        json.append("{\"hosts\":[");
        List<String> hosts = store.hosts();
        for (int i = 0; i < hosts.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            String host = hosts.get(i);
            HostSessionData session = store.snapshot(host);
            json.append("{\"address\":")
                    .append(JsonStrings.quote(host))
                    .append(",\"enabled\":")
                    .append(session.isEnabled())
                    .append(",\"probe_mode\":")
                    .append(JsonStrings.quote(session.getProbeMode().yamlValue()))
                    .append('}');
        }
        json.append("]}");
        return json.toString();
    }

    static String routeDocument(String host, List<HopNode> hops) {
        return routeDocument(host, hops, false);
    }

    /**
     * @param includeGeo when true, append nullable GeoIP fields per hop ({@code ?include=geo})
     */
    static String routeDocument(String host, List<HopNode> hops, boolean includeGeo) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(hops, "hops");
        StringBuilder json = new StringBuilder(128);
        json.append("{\"host\":").append(JsonStrings.quote(host)).append(",\"hops\":[");
        for (int i = 0; i < hops.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            HopNode hop = hops.get(i);
            json.append("{\"hop\":")
                    .append(hop.hop())
                    .append(",\"ip\":")
                    .append(JsonStrings.quote(hop.ip()))
                    .append(",\"ping_ms\":");
            if (hop.pingMs() == null) {
                json.append("null");
            } else {
                json.append(hop.pingMs());
            }
            json.append(",\"timeout\":").append(hop.timeout());
            if (includeGeo) {
                RouteGeoEnrichment.appendHopGeoFields(json, RouteGeoEnrichment.resolveLiteral(hop.ip()));
            }
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    static String opsDocument(io.pingui.dns.DnsOpsSnapshot snapshot) {
        return opsDocument(snapshot, io.pingui.geoip.IpMetadataRuntime.get().opsStats());
    }

    /**
     * @param geoip enrichment counters (P36-010); {@code null} → empty snapshot
     */
    static String opsDocument(io.pingui.dns.DnsOpsSnapshot snapshot, io.pingui.geoip.GeoIpOpsStats geoip) {
        Objects.requireNonNull(snapshot, "snapshot");
        io.pingui.geoip.GeoIpOpsStats stats = geoip != null ? geoip : io.pingui.geoip.GeoIpOpsStats.empty();
        return "{\"dns\":"
                + dnsObject(snapshot.resolve())
                + ",\"dns_control\":"
                + dnsObject(snapshot.control())
                + ",\"geoip\":"
                + geoipObject(stats)
                + "}";
    }

    /** Backward-compatible single-layer ops JSON (tests / older callers). */
    static String opsDocument(io.pingui.dns.DnsOpsStats dns) {
        Objects.requireNonNull(dns, "dns");
        return opsDocument(new io.pingui.dns.DnsOpsSnapshot(dns, io.pingui.dns.DnsOpsStats.empty(0)));
    }

    private static String dnsObject(io.pingui.dns.DnsOpsStats dns) {
        return "{"
                + "\"queue_capacity\":"
                + dns.queueCapacity()
                + ",\"queued\":"
                + dns.queued()
                + ",\"in_flight\":"
                + dns.inFlight()
                + ",\"rejected\":"
                + dns.rejectedCount()
                + ",\"dropped\":"
                + dns.droppedCount()
                + ",\"coalesced\":"
                + dns.coalescedCount()
                + ",\"timeouts\":"
                + dns.timeoutCount()
                + "}";
    }

    private static String geoipObject(io.pingui.geoip.GeoIpOpsStats geoip) {
        String reload = geoip.lastSuccessfulReload() == null
                ? "null"
                : JsonStrings.quote(geoip.lastSuccessfulReload().toString());
        return "{"
                + "\"cache_size\":"
                + geoip.cacheSize()
                + ",\"cache_capacity\":"
                + geoip.cacheCapacity()
                + ",\"queue_depth\":"
                + geoip.queueDepth()
                + ",\"queue_capacity\":"
                + geoip.queueCapacity()
                + ",\"pending\":"
                + geoip.pendingLookups()
                + ",\"hits\":"
                + geoip.hits()
                + ",\"misses\":"
                + geoip.misses()
                + ",\"unknowns\":"
                + geoip.unknowns()
                + ",\"errors\":"
                + geoip.errors()
                + ",\"rejected\":"
                + geoip.rejected()
                + ",\"coalesced\":"
                + geoip.coalesced()
                + ",\"reload_failures\":"
                + geoip.reloadFailures()
                + ",\"last_successful_reload\":"
                + reload
                + "}";
    }

    /** Minimal OpenAPI 3.0 stub for the read endpoints. */
    static String openApiDocument() {
        String apiVersion = AppInfo.version().replace("-SNAPSHOT", "");
        return """
                {
                  "openapi": "3.0.3",
                  "info": {
                    "title": "PINGUI Read-Only API",
                    "version": "%s",
                    "description": "Localhost runbook API (P15-040 / P34-007). Auth out of scope for v1."
                  },
                  "servers": [{"url": "http://127.0.0.1"}],
                  "paths": {
                    "/hosts": {
                      "get": {
                        "summary": "List monitored hosts",
                        "responses": {
                          "200": {
                            "description": "Host list",
                            "content": {
                              "application/json": {
                                "schema": {"type": "object"}
                              }
                            }
                          }
                        }
                      }
                    },
                    "/routes/{host}": {
                      "get": {
                        "summary": "Current route for a host",
                        "parameters": [
                          {
                            "name": "host",
                            "in": "path",
                            "required": true,
                            "schema": {"type": "string"}
                          },
                          {
                            "name": "include",
                            "in": "query",
                            "required": false,
                            "description": "Opt-in enrichment; use include=geo for nullable country/ASN fields (P36-009)",
                            "schema": {"type": "string", "enum": ["geo"]}
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Route hops (geo fields only when include=geo)",
                            "content": {
                              "application/json": {
                                "schema": {"type": "object"}
                              }
                            }
                          },
                          "404": {"description": "Unknown host"}
                        }
                      }
                    },
                    "/ops": {
                      "get": {
                        "summary": "Operator counters (DNS resolve + DNS-control + GeoIP)",
                        "responses": {
                          "200": {
                            "description": "Ops snapshot (dns, dns_control, geoip)",
                            "content": {
                              "application/json": {
                                "schema": {"type": "object"}
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """
                .formatted(apiVersion);
    }
}
