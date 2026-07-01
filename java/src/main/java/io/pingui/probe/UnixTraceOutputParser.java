package io.pingui.probe;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses BSD/GNU/Linux/macOS traceroute stdout into hop nodes. */
final class UnixTraceOutputParser {

    private static final Pattern UNIX_LINE =
            Pattern.compile("^\\s*(\\d+)\\s+(\\S+)(?:\\s+(\\d+(?:\\.\\d+)?)\\s*ms)?.*");

    private UnixTraceOutputParser() {}

    static List<HopNode> parse(List<String> lines) {
        List<HopNode> nodes = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = UNIX_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int hop = Integer.parseInt(matcher.group(1));
            String token = normalizeHopToken(matcher.group(2));
            if ("*".equals(token)) {
                nodes.add(Models.timeout(hop));
                continue;
            }
            Double pingMs = matcher.group(3) != null ? Double.parseDouble(matcher.group(3)) : null;
            nodes.add(new HopNode(hop, token, pingMs, false));
        }
        return nodes;
    }

    private static String normalizeHopToken(String token) {
        if (token.startsWith("[") && token.endsWith("]") && token.length() >= 2) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }
}
