package io.pingui.probe;

import java.util.List;

/** Builds argv for an OS trace subprocess (traceroute or tracert). */
interface TraceCommandBuilder {

    /** Command line for one trace run to {@code targetHost}. */
    List<String> buildCommand(String targetHost, int maxHops, double timeoutSeconds);
}
