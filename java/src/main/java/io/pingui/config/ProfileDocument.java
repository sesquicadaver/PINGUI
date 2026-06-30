package io.pingui.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Root YAML document: multiple tracing profiles and active selection. */
public final class ProfileDocument {
    public static final String DEFAULT_PROFILE = "default";

    private String activeProfile;
    private final Map<String, TracingProfile> profiles = new LinkedHashMap<>();

    public ProfileDocument(String activeProfile, Map<String, TracingProfile> profiles) {
        this.activeProfile =
                activeProfile != null && !activeProfile.isBlank() ? activeProfile.strip() : DEFAULT_PROFILE;
        if (profiles != null) {
            this.profiles.putAll(profiles);
        }
        if (this.profiles.isEmpty()) {
            this.profiles.put(DEFAULT_PROFILE, TracingProfile.defaults(List.of()));
        }
        if (!this.profiles.containsKey(this.activeProfile)) {
            this.activeProfile = this.profiles.keySet().iterator().next();
        }
    }

    public static ProfileDocument singleDefault(TracingProfile profile) {
        return new ProfileDocument(DEFAULT_PROFILE, Map.of(DEFAULT_PROFILE, profile));
    }

    public String activeProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String name) {
        String key = name != null ? name.strip() : "";
        if (key.isBlank()) {
            throw new io.pingui.config.ConfigError("Profile name cannot be empty");
        }
        if (!profiles.containsKey(key)) {
            throw new io.pingui.config.ConfigError("Unknown profile: " + key);
        }
        activeProfile = key;
    }

    public Map<String, TracingProfile> profiles() {
        return Map.copyOf(profiles);
    }

    public TracingProfile active() {
        return profiles.get(activeProfile);
    }

    public void putProfile(String name, TracingProfile profile) {
        String key = Objects.requireNonNull(name, "name").strip();
        if (key.isBlank()) {
            throw new io.pingui.config.ConfigError("Profile name cannot be empty");
        }
        profiles.put(key, profile);
    }

    public void removeProfile(String name) {
        if (profiles.size() <= 1) {
            throw new io.pingui.config.ConfigError("Cannot delete the last profile");
        }
        if (!profiles.containsKey(name)) {
            throw new io.pingui.config.ConfigError("Unknown profile: " + name);
        }
        profiles.remove(name);
        if (activeProfile.equals(name)) {
            activeProfile = profiles.keySet().iterator().next();
        }
    }

    public boolean hasProfile(String name) {
        return profiles.containsKey(name);
    }
}
