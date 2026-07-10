package io.pingui.persistence;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON codec for Python-parity {@code host_session} columns (P11-010). */
final class SessionJsonCodec {
    private SessionJsonCodec() {}

    static String routeToJson(List<HopNode> route) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < route.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(hopToJson(route.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    static List<HopNode> routeFromJson(String json) {
        List<Object> items = readArray(json);
        List<HopNode> route = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new PersistenceException("Route JSON must be a list of hop objects");
            }
            route.add(hopFromMap(map));
        }
        return List.copyOf(route);
    }

    static String lastKnownToJson(Map<Integer, HopNode> mapping) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<Integer, HopNode> entry : mapping.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(String.valueOf(entry.getKey())));
            sb.append(':');
            sb.append(hopToJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    static Map<Integer, HopNode> lastKnownFromJson(String json) {
        Map<String, Object> raw = readObject(json);
        Map<Integer, HopNode> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> map)) {
                throw new PersistenceException("Last-known JSON must map hop to hop object");
            }
            result.put(Integer.parseInt(entry.getKey()), hopFromMap(map));
        }
        return result;
    }

    static String pingHistoryToJson(Map<String, List<Double>> history) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, List<Double>> entry : history.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(entry.getKey()));
            sb.append(':');
            sb.append(numberList(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    static Map<String, List<Double>> pingHistoryFromJson(String json) {
        Map<String, Object> raw = readObject(json);
        Map<String, List<Double>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!(entry.getValue() instanceof List<?> list)) {
                throw new PersistenceException("Ping history JSON must map IP to number list");
            }
            List<Double> samples = new ArrayList<>();
            for (Object value : list) {
                samples.add(asDouble(value));
            }
            result.put(entry.getKey(), samples);
        }
        return result;
    }

    static String hopStatsToJson(Map<Integer, HopProbeStats> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<Integer, HopProbeStats> entry : stats.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            HopProbeStats item = entry.getValue();
            sb.append(quote(String.valueOf(entry.getKey())));
            sb.append(':');
            sb.append('{');
            sb.append("\"probes\":").append(item.getProbes());
            sb.append(",\"successes\":").append(item.getSuccesses());
            sb.append(",\"rtt_samples\":").append(numberList(item.getRttSamples()));
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    static Map<Integer, HopProbeStats> hopStatsFromJson(String json) {
        Map<String, Object> raw = readObject(json);
        Map<Integer, HopProbeStats> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> map)) {
                throw new PersistenceException("Hop stats JSON must map hop to stats object");
            }
            HopProbeStats stats = HopProbeStats.fromSerialized(
                    asInt(map.get("probes"), 0),
                    asInt(map.get("successes"), 0),
                    readDoubleList(map.get("rtt_samples")));
            result.put(Integer.parseInt(entry.getKey()), stats);
        }
        return result;
    }

    private static String hopToJson(HopNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"hop\":").append(node.hop());
        sb.append(",\"ip\":").append(quote(node.ip()));
        if (node.pingMs() == null) {
            sb.append(",\"ping_ms\":null");
        } else {
            sb.append(",\"ping_ms\":").append(node.pingMs());
        }
        sb.append(",\"is_timeout\":").append(node.timeout());
        sb.append('}');
        return sb.toString();
    }

    private static HopNode hopFromMap(Map<?, ?> map) {
        boolean timeout = Boolean.TRUE.equals(map.get("is_timeout"));
        int hop = asInt(map.get("hop"), 0);
        if (timeout) {
            return Models.timeout(hop);
        }
        String ip = String.valueOf(map.get("ip"));
        Double pingMs = map.get("ping_ms") == null ? null : asDouble(map.get("ping_ms"));
        return new HopNode(hop, ip, pingMs, false);
    }

    private static String numberList(List<Double> values) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static List<Double> readDoubleList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Double> samples = new ArrayList<>();
        for (Object item : list) {
            samples.add(asDouble(item));
        }
        return List.copyOf(samples);
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static Map<String, Object> readObject(String json) {
        int start = skipWhitespace(json, 0);
        if (start >= json.length() || json.charAt(start) != '{') {
            throw new PersistenceException("Expected JSON object");
        }
        return readObjectAt(json, start);
    }

    private static List<Object> readArray(String json) {
        int start = skipWhitespace(json, 0);
        if (start >= json.length() || json.charAt(start) != '[') {
            throw new PersistenceException("Expected JSON array");
        }
        return readArrayAt(json, start);
    }

    private static Map<String, Object> readObjectAt(String json, int openBrace) {
        Map<String, Object> result = new LinkedHashMap<>();
        int index = openBrace + 1;
        while (true) {
            index = skipWhitespace(json, index);
            if (index < json.length() && json.charAt(index) == '}') {
                return result;
            }
            String key = readStringValue(json, index);
            index = skipAfterString(json, index);
            index = skipWhitespace(json, index);
            if (index >= json.length() || json.charAt(index) != ':') {
                throw new PersistenceException("Expected ':' after object key");
            }
            index = skipWhitespace(json, index + 1);
            ParsedValue parsed = readValue(json, index);
            result.put(key, parsed.value());
            index = skipWhitespace(json, parsed.nextIndex());
            if (index < json.length() && json.charAt(index) == ',') {
                index++;
                continue;
            }
            if (index < json.length() && json.charAt(index) == '}') {
                return result;
            }
            throw new PersistenceException("Malformed JSON object");
        }
    }

    private static List<Object> readArrayAt(String json, int openBracket) {
        List<Object> result = new ArrayList<>();
        int index = openBracket + 1;
        while (true) {
            index = skipWhitespace(json, index);
            if (index < json.length() && json.charAt(index) == ']') {
                return result;
            }
            ParsedValue parsed = readValue(json, index);
            result.add(parsed.value());
            index = skipWhitespace(json, parsed.nextIndex());
            if (index < json.length() && json.charAt(index) == ',') {
                index++;
                continue;
            }
            if (index < json.length() && json.charAt(index) == ']') {
                return result;
            }
            throw new PersistenceException("Malformed JSON array");
        }
    }

    private static ParsedValue readValue(String json, int index) {
        index = skipWhitespace(json, index);
        if (index >= json.length()) {
            throw new PersistenceException("Unexpected end of JSON");
        }
        char ch = json.charAt(index);
        if (ch == '"') {
            String value = readStringValue(json, index);
            return new ParsedValue(value, skipAfterString(json, index));
        }
        if (ch == '{') {
            Map<String, Object> object = readObjectAt(json, index);
            int end = findClosing(json, index, '{', '}');
            return new ParsedValue(object, end + 1);
        }
        if (ch == '[') {
            List<Object> array = readArrayAt(json, index);
            int end = findClosing(json, index, '[', ']');
            return new ParsedValue(array, end + 1);
        }
        if (json.startsWith("null", index)) {
            return new ParsedValue(null, index + 4);
        }
        if (ch == 't' && json.startsWith("true", index)) {
            return new ParsedValue(Boolean.TRUE, index + 4);
        }
        if (ch == 'f' && json.startsWith("false", index)) {
            return new ParsedValue(Boolean.FALSE, index + 5);
        }
        int end = index;
        while (end < json.length()) {
            char current = json.charAt(end);
            if (current == ',' || current == '}' || current == ']' || Character.isWhitespace(current)) {
                break;
            }
            end++;
        }
        String token = json.substring(index, end);
        if (token.contains(".")) {
            return new ParsedValue(Double.parseDouble(token), end);
        }
        return new ParsedValue(Long.parseLong(token), end);
    }

    private static int findClosing(String json, int openIndex, char open, char close) {
        int depth = 0;
        for (int i = openIndex; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                i = skipAfterString(json, i) - 1;
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new PersistenceException("Unterminated JSON container");
    }

    private static String readStringValue(String json, int quoteIndex) {
        if (json.charAt(quoteIndex) != '"') {
            throw new PersistenceException("Expected opening quote");
        }
        StringBuilder sb = new StringBuilder();
        int index = quoteIndex + 1;
        while (index < json.length()) {
            char ch = json.charAt(index++);
            if (ch == '"') {
                return sb.toString();
            }
            if (ch == '\\') {
                if (index >= json.length()) {
                    throw new PersistenceException("Unterminated escape");
                }
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> sb.append(escaped);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> throw new PersistenceException("Unsupported escape: \\" + escaped);
                }
                continue;
            }
            sb.append(ch);
        }
        throw new PersistenceException("Unterminated string");
    }

    private static int skipAfterString(String json, int quoteIndex) {
        int index = quoteIndex + 1;
        while (index < json.length()) {
            char ch = json.charAt(index++);
            if (ch == '"') {
                return index;
            }
            if (ch == '\\' && index < json.length()) {
                index++;
            }
        }
        return index;
    }

    private static int skipWhitespace(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private record ParsedValue(Object value, int nextIndex) {}
}
