package io.pingui.monitor;

/**
 * Likely network segment for a multi-host problem correlation (P29-001).
 *
 * <ul>
 *   <li>{@link #LOCAL} — LAN / CPE / first hops
 *   <li>{@link #ISP} — shared WAN / provider segment
 *   <li>{@link #EDGE} — far path / near targets
 *   <li>{@link #UNKNOWN} — insufficient shared path evidence
 * </ul>
 */
public enum ProblemCorrelationScope {
    LOCAL,
    ISP,
    EDGE,
    UNKNOWN
}
