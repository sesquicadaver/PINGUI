package io.pingui.api;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.SessionStore;
import java.util.List;
import java.util.Objects;

/** JSON serializers for the read-only runbook API (P15-040). */
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
            HostSessionData session = store.get(host);
            json.append("{\"address\":")
                    .append(JsonStrings.quote(host))
                    .append(",\"enabled\":")
                    .append(session.isEnabled())
                    .append(",\"probe_mode\":")
                    .append(JsonStrings.quote(store.getProbeMode(host).yamlValue()))
                    .append('}');
        }
        json.append("]}");
        return json.toString();
    }

    static String routeDocument(String host, List<HopNode> hops) {
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
            json.append(",\"timeout\":").append(hop.timeout()).append('}');
        }
        json.append("]}");
        return json.toString();
    }

    /** Minimal OpenAPI 3.0 stub for the two read endpoints. */
    static String openApiDocument() {
        return """
                {
                  "openapi": "3.0.3",
                  "info": {
                    "title": "PINGUI Read-Only API",
                    "version": "0.1.0",
                    "description": "Localhost runbook API (P15-040). Auth out of scope for v1."
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
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Route hops",
                            "content": {
                              "application/json": {
                                "schema": {"type": "object"}
                              }
                            }
                          },
                          "404": {"description": "Unknown host"}
                        }
                      }
                    }
                  }
                }
                """;
    }
}
