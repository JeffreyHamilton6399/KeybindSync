package com.keybindsync;

import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Persisted mod settings stored in keybind-sync-config.json next to the profiles folder. */
public class KeybindSyncConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static KeybindSyncConfig INSTANCE;

    // ── fields ────────────────────────────────────────────────────────────────
    public boolean autoSaveOnQuit   = false;
    public String  autoSaveProfile  = "autosave";

    public boolean autoLoadOnLaunch = false;
    public String  autoLoadProfile  = "";

    /** server-address (lowercase) → profile name */
    public Map<String, String> serverProfiles = new LinkedHashMap<>();

    // ── singleton ─────────────────────────────────────────────────────────────
    public static KeybindSyncConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    // ── persistence ───────────────────────────────────────────────────────────
    private static Path configFile() {
        return ProfileManager.globalBase().resolve("keybind-sync-config.json");
    }

    private static KeybindSyncConfig load() {
        Path file = configFile();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                KeybindSyncConfig cfg = GSON.fromJson(r, KeybindSyncConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException | JsonParseException e) {
                KeybindSyncMod.LOGGER.error("Failed to read config, using defaults", e);
            }
        }
        KeybindSyncConfig defaults = new KeybindSyncConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(configFile())) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            KeybindSyncMod.LOGGER.error("Failed to save config", e);
        }
    }
}
