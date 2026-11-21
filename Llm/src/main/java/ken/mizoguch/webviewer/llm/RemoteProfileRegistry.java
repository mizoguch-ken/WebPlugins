package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Thread-safe registry of named remote endpoint configurations and active profile selection.
 *
 * @author mizoguch-ken
 */
final class RemoteProfileRegistry {

    static final String DEFAULT_PROFILE = "default";
    static final int MAX_PROFILES = 32;

    private static final Pattern VALID_PROFILE_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    private final Map<String, RemoteConfig> profiles_ = new LinkedHashMap<>();
    private String activeProfile_ = DEFAULT_PROFILE;

    synchronized RemoteConfig configure(String profileName, String configInput) {
        String name = ((profileName == null) || profileName.trim().isEmpty())
                ? activeProfile_
                : normalizeProfileName(profileName);
        if (!profiles_.containsKey(name) && (profiles_.size() >= MAX_PROFILES)) {
            throw LlmRemoteException.config("Maximum number of remote profiles reached ("
                    + MAX_PROFILES + ")");
        }
        RemoteConfig existing = profiles_.get(name);
        RemoteConfig updated = RemoteConfig.parseAndMerge(existing, configInput);
        profiles_.put(name, updated);
        return updated;
    }

    synchronized RemoteConfig get(String profileName) {
        if ((profileName == null) || profileName.trim().isEmpty()) {
            return profiles_.get(activeProfile_);
        }
        return profiles_.get(normalizeProfileName(profileName));
    }

    synchronized RemoteConfig require(String profileName) {
        if ((profileName == null) || profileName.trim().isEmpty()) {
            RemoteConfig active = profiles_.get(activeProfile_);
            if (active == null) {
                throw LlmRemoteException.config("Remote endpoint is not configured");
            }
            return active;
        }
        String name = normalizeProfileName(profileName);
        RemoteConfig config = profiles_.get(name);
        if (config == null) {
            throw LlmRemoteException.config("Remote profile \"" + name + "\" is not configured");
        }
        return config;
    }

    synchronized String resolveName(String profileName) {
        if ((profileName == null) || profileName.trim().isEmpty()) {
            return activeProfile_;
        }
        return normalizeProfileName(profileName);
    }

    synchronized String activeName() {
        return activeProfile_;
    }

    synchronized RemoteConfig use(String profileName) {
        String name = normalizeProfileName(profileName);
        RemoteConfig target = profiles_.get(name);
        if (target == null) {
            throw LlmRemoteException.config("Remote profile \"" + name + "\" is not configured");
        }
        activeProfile_ = name;
        return target;
    }

    synchronized boolean remove(String profileName) {
        String name = ((profileName == null) || profileName.trim().isEmpty())
                ? activeProfile_
                : normalizeProfileName(profileName);
        boolean removed = profiles_.remove(name) != null;
        if (name.equals(activeProfile_)) {
            activeProfile_ = DEFAULT_PROFILE;
        }
        return removed;
    }

    synchronized void clearAll() {
        profiles_.clear();
        activeProfile_ = DEFAULT_PROFILE;
    }

    synchronized JsonObject profilePublicJson(String profileName, String state) {
        String resolved = resolveName(profileName);
        RemoteConfig cfg = profiles_.get(resolved);
        JsonObject json = (cfg != null)
                ? cfg.toPublicJson(state)
                : RemoteConfig.emptyPublicJson(state);
        json.addProperty("profile", resolved);
        return json;
    }

    synchronized JsonObject allProfilesPublicJson(String activeState) {
        JsonObject root = new JsonObject();
        root.addProperty("active", activeProfile_);
        JsonObject map = new JsonObject();
        for (Map.Entry<String, RemoteConfig> entry : profiles_.entrySet()) {
            String state = entry.getKey().equals(activeProfile_) ? activeState : "idle";
            map.add(entry.getKey(), entry.getValue().toPublicJson(state));
        }
        root.add("profiles", map);
        return root;
    }

    private static String normalizeProfileName(String raw) {
        if ((raw == null) || raw.trim().isEmpty()) {
            throw LlmRemoteException.config("Profile name must not be empty");
        }
        String trimmed = raw.trim();
        if (!VALID_PROFILE_NAME.matcher(trimmed).matches()) {
            throw LlmRemoteException.config(
                    "Invalid profile name \"" + trimmed + "\": use 1..64 [A-Za-z0-9_.-] chars");
        }
        return trimmed;
    }
}
