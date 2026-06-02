package com.keybindsync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.*;

public class ProfileDiff {

    public record Entry(String actionName, String currentKey, String newKey) {
        public boolean isNew()     { return currentKey == null; }
        public boolean isChanged() { return currentKey != null && !currentKey.equals(newKey); }
    }

    public record ConflictEntry(String key, List<String> actions) {}

    public static List<Entry> diff(String profileName) {
        JsonObject profile = ProfileManager.getCached(profileName);
        if (profile == null || !profile.has("keybinds")) return Collections.emptyList();

        Map<String, String> live = currentBindings();
        JsonObject keybinds = profile.getAsJsonObject("keybinds");

        List<Entry> changed = new ArrayList<>();
        List<Entry> added   = new ArrayList<>();

        for (Map.Entry<String, JsonElement> e : keybinds.entrySet()) {
            String action  = e.getKey();
            String newKey  = e.getValue().getAsString();
            String liveKey = live.get(action);
            if (liveKey == null)               added.add(new Entry(action, null, newKey));
            else if (!liveKey.equals(newKey))  changed.add(new Entry(action, liveKey, newKey));
        }

        List<Entry> result = new ArrayList<>(changed);
        result.addAll(added);
        return Collections.unmodifiableList(result);
    }

    public static List<ConflictEntry> detectConflicts() {
        Map<String, List<String>> keyToActions = new LinkedHashMap<>();
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            String key = km.saveString();
            if ("key.keyboard.unknown".equals(key)) continue;
            keyToActions.computeIfAbsent(key, k -> new ArrayList<>()).add(km.getName());
        }
        List<ConflictEntry> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : keyToActions.entrySet())
            if (e.getValue().size() > 1)
                conflicts.add(new ConflictEntry(e.getKey(), Collections.unmodifiableList(e.getValue())));
        return conflicts;
    }

    private static Map<String, String> currentBindings() {
        Map<String, String> map = new HashMap<>();
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings)
            map.put(km.getName(), km.saveString());
        return map;
    }
}
