package com.keybindsync;

import com.keybindsync.gui.ControlsSidePanel;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeybindSyncMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("keybindsync");

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("keybindsync", "keybindsync"));

    private static KeyMapping saveKey;
    private static KeyMapping loadKey;

    @Override
    public void onInitializeClient() {
        saveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.keybindsync.save", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY));
        loadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.keybindsync.load", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY));

        ProfileManager.preload();

        // ── auto-load on launch ───────────────────────────────────────────────
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        if (cfg.autoLoadOnLaunch && !cfg.autoLoadProfile.isBlank()) {
            ClientTickEvents.START_CLIENT_TICK.register(new ClientTickEvents.StartTick() {
                boolean done = false;
                @Override
                public void onStartTick(net.minecraft.client.Minecraft client) {
                    if (done) return;
                    done = true;
                    ProfileManager.loadProfile(cfg.autoLoadProfile);
                    LOGGER.info("Auto-loaded profile '{}' on launch", cfg.autoLoadProfile);
                }
            });
        }

        // ── quick-save / quick-load hotkeys ───────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (saveKey.consumeClick()) ProfileManager.saveProfile("quick");
            if (loadKey.consumeClick()) ProfileManager.loadProfile("quick");
        });

        // ── sidebar panel on the vanilla Controls screen ───────────────────────
        // One panel instance, reused across all opens of the Controls screen.
        // Per-screen events are re-registered every AFTER_INIT (they get cleared
        // when the screen is removed). A version counter makes stale registrations
        // from previous opens silently no-op, so events don't accumulate.
        ControlsSidePanel panel = new ControlsSidePanel();
        int[] version = {0};

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof KeyBindsScreen kbs)) return;

            int myVer = ++version[0];

            ScreenEvents.afterExtract(kbs).register((scr, g, mx, my, delta) -> {
                if (version[0] == myVer) panel.draw(g, scr, mx, my);
            });
            ScreenMouseEvents.afterMouseClick(kbs).register((scr, event, doubled) -> {
                if (version[0] != myVer) return false;
                return panel.handleClick(scr, event.x(), event.y());
            });
            ScreenMouseEvents.afterMouseScroll(kbs).register((scr, mx, my, hAmt, vAmt, b) -> {
                if (version[0] != myVer) return false;
                return panel.handleScroll(scr, mx, my, vAmt);
            });
            ScreenKeyboardEvents.allowKeyPress(kbs).register((scr, event) -> {
                if (version[0] != myVer) return true;
                if (panel.isAnyFieldFocused()) { panel.handleKeyPress(event); return false; }
                return true;
            });

            panel.buildWidgets(kbs);
        });

        // ── per-server auto-switch ────────────────────────────────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerData data = handler.getServerData();
            if (data == null) return;
            String addr = data.ip.toLowerCase();
            KeybindSyncConfig c = KeybindSyncConfig.get();
            String profile = c.serverProfiles.get(addr);
            if (profile != null && !profile.isBlank()) {
                client.execute(() -> {
                    if (ProfileManager.loadProfile(profile))
                        LOGGER.info("Auto-loaded profile '{}' for {}", profile, addr);
                });
            }
        });

        // ── auto-save on disconnect ───────────────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            KeybindSyncConfig c = KeybindSyncConfig.get();
            if (c.autoSaveOnQuit && !c.autoSaveProfile.isBlank()) {
                ProfileManager.saveProfile(c.autoSaveProfile);
                LOGGER.info("Auto-saved profile '{}' on disconnect", c.autoSaveProfile);
            }
        });

        LOGGER.info("KeybindSync loaded");
    }
}
