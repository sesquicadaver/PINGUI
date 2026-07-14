package io.pingui.probe;

import io.pingui.config.PingExpertEntry;
import java.io.IOException;
import java.util.OptionalDouble;

/** One-shot expert ping used by self-check / MTU runners (testable seam). */
@FunctionalInterface
public interface ExpertPingOnce {
    OptionalDouble pingOnce(String target, PingExpertEntry expert, double timeoutSeconds) throws IOException;
}
