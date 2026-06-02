package com.keybindsync;

import com.keybindsync.gui.ControlsSidePanel;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeybindSyncMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("keybindsync");

    private static KeyMapping saveKey;
    private static KeyMapping loadKey;

    @Override
    public void onInitializeClient() {
        saveKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.keybindsync.save", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.category.keybindsync.keybindsync"));
        loadKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.keybindsync.load", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.category.keybindsync.keybindsync"));

        ProfileManager.preload();

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (saveKey.consumeClick()) {
                ProfileManager.saveProfile("quick");
                client.gui.setOverlayMessage(Component.literal("KeybindSync: Saved to 'quick'"), false);
            }
            if (loadKey.consumeClick()) {
                ProfileManager.loadProfile("quick");
                client.gui.setOverlayMessage(Component.literal("KeybindSync: Loaded 'quick'"), false);
            }
        });

        ControlsSidePanel panel = new ControlsSidePanel();
        int[] version = {0};

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof KeyBindsScreen kbs)) return;
            int myVer = ++version[0];

            ScreenEvents.afterRender(kbs).register((scr, ctx, mx, my, delta) -> {
                if (version[0] == myVer) panel.draw(ctx, scr, mx, my);
            });
            ScreenMouseEvents.afterMouseClick(kbs).register((scr, mx, my, button) -> {
                if (version[0] != myVer) return;
                panel.handleClick(scr, mx, my);
            });
            ScreenMouseEvents.afterMouseScroll(kbs).register((scr, mx, my, hAmt, vAmt) -> {
                if (version[0] != myVer) return;
                panel.handleScroll(scr, mx, my, vAmt);
            });
            ScreenKeyboardEvents.allowKeyPress(kbs).register((scr, key, scancode, modifiers) -> {
                if (version[0] != myVer) return true;
                if (panel.isAnyFieldFocused()) { panel.handleKeyPress(key, modifiers); return false; }
                return true;
            });

            panel.buildWidgets(kbs);
        });

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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            KeybindSyncConfig c = KeybindSyncConfig.get();
            if (c.autoSaveOnQuit && !c.autoSaveProfile.isBlank()) {
                ProfileManager.saveProfile(c.autoSaveProfile);
                LOGGER.info("Auto-saved profile '{}' on disconnect", c.autoSaveProfile);
            }
        });

        LOGGER.info("KeybindSync loaded (1.21.x)");
    }
}
