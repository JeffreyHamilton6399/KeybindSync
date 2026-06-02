package com.keybindsync;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class ProfileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "keybindsync-io");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, JsonObject> CACHE = new ConcurrentHashMap<>();
    private static final AtomicReference<List<String>> NAME_LIST =
            new AtomicReference<>(new ArrayList<>());

    public static void preload() {
        IO.submit(() -> {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDir(), "*.json")) {
                for (Path p : stream) {
                    String name = stem(p);
                    try (Reader r = Files.newBufferedReader(p)) {
                        JsonObject obj = GSON.fromJson(r, JsonObject.class);
                        if (obj != null) CACHE.put(name, obj);
                    } catch (Exception e) {
                        KeybindSyncMod.LOGGER.warn("Could not preload '{}': {}", name, e.getMessage());
                    }
                }
            } catch (IOException e) {
                KeybindSyncMod.LOGGER.error("Preload failed", e);
            }
            refreshNameList();
            KeybindSyncMod.LOGGER.info("KeybindSync preloaded {} profile(s)", CACHE.size());
        });
    }

    public static void saveProfile(String name) {
        Minecraft client = Minecraft.getInstance();
        JsonObject root = new JsonObject();
        root.addProperty("name", name);

        JsonObject bindings = new JsonObject();
        for (KeyMapping km : client.options.keyMappings) {
            bindings.addProperty(km.getName(), km.saveString());
        }
        root.add("keybinds", bindings);

        CACHE.put(name, root);
        refreshNameList();

        final Path file = profilesDir().resolve(sanitize(name) + ".json");
        IO.submit(() -> {
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(root, w);
                KeybindSyncMod.LOGGER.info("Saved profile '{}'", name);
            } catch (IOException e) {
                KeybindSyncMod.LOGGER.error("Failed to write profile '{}'", name, e);
            }
        });
    }

    public static boolean loadProfile(String name) {
        JsonObject root = CACHE.get(name);
        if (root == null) {
            Path file = profilesDir().resolve(sanitize(name) + ".json");
            if (!Files.exists(file)) return false;
            try (Reader r = Files.newBufferedReader(file)) {
                root = GSON.fromJson(r, JsonObject.class);
                if (root != null) CACHE.put(name, root);
            } catch (IOException | JsonParseException e) {
                KeybindSyncMod.LOGGER.error("Failed to read profile '{}'", name, e);
                return false;
            }
        }
        if (root == null || !root.has("keybinds")) return false;

        Minecraft client = Minecraft.getInstance();
        Map<String, KeyMapping> byName = new HashMap<>();
        for (KeyMapping km : client.options.keyMappings) byName.put(km.getName(), km);

        JsonObject keybinds = root.getAsJsonObject("keybinds");
        int applied = 0;
        for (Map.Entry<String, JsonElement> e : keybinds.entrySet()) {
            KeyMapping km = byName.get(e.getKey());
            if (km == null) continue;
            try {
                InputConstants.Key key = InputConstants.getKey(e.getValue().getAsString());
                km.setKey(key);
                applied++;
            } catch (Exception ex) {
                KeybindSyncMod.LOGGER.warn("Skipping '{}': {}", e.getKey(), ex.getMessage());
            }
        }

        KeyMapping.resetMapping();
        client.options.save();
        KeybindSyncMod.LOGGER.info("Loaded profile '{}' ({} bindings)", name, applied);
        return true;
    }

    public static boolean deleteProfile(String name) {
        CACHE.remove(name);
        refreshNameList();
        final Path file = profilesDir().resolve(sanitize(name) + ".json");
        IO.submit(() -> {
            try {
                Files.deleteIfExists(file);
                KeybindSyncMod.LOGGER.info("Deleted profile '{}'", name);
            } catch (IOException e) {
                KeybindSyncMod.LOGGER.error("Failed to delete profile '{}'", name, e);
            }
        });
        return true;
    }

    public static List<String> listProfiles() { return NAME_LIST.get(); }

    public static JsonObject getCached(String name) { return CACHE.get(name); }

    private static void refreshNameList() {
        List<String> names = new ArrayList<>(CACHE.keySet());
        Collections.sort(names);
        NAME_LIST.set(Collections.unmodifiableList(names));
    }

    /** Global folder shared across all Minecraft instances / modpacks. */
    public static Path profilesDir() {
        Path dir = globalBase().resolve("profiles");
        try { Files.createDirectories(dir); }
        catch (IOException e) { KeybindSyncMod.LOGGER.error("Cannot create profiles dir", e); }
        return dir;
    }

    public static Path globalBase() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base;
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            base = appdata != null ? Path.of(appdata) : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
        } else if (os.contains("mac")) {
            base = Path.of(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            base = Path.of(System.getProperty("user.home"), ".config");
        }
        return base.resolve("KeybindSync");
    }

    private static String stem(Path p) {
        String n = p.getFileName().toString();
        return n.endsWith(".json") ? n.substring(0, n.length() - 5) : n;
    }

    private static String sanitize(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9_.\\-]", "_");
    }
}
