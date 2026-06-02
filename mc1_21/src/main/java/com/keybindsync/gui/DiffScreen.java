package com.keybindsync.gui;

import com.keybindsync.ProfileDiff;
import com.keybindsync.ProfileManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DiffScreen extends Screen {

    private static final int COL_CHANGED = 0xFFFFFF55;
    private static final int COL_NEW     = 0xFF888888;
    private static final int COL_SAME    = 0xFF55FF55;
    private static final int COL_SUBHDR  = 0xFF666666;
    private static final int COL_WHITE   = 0xFFFFFFFF;
    private static final int COL_LGRAY   = 0xFFCCCCCC;
    private static final int COL_MGRAY   = 0xFF888888;
    private static final int COL_SUMMARY = 0xFFAAAAAA;
    private static final int ENTRY_H     = 11;
    private static final int VISIBLE     = 18;

    private final Screen parent;
    private final String profileName;
    private List<ProfileDiff.Entry> entries;
    private int scrollOffset = 0;

    public DiffScreen(Screen parent, String profileName) {
        super(Component.literal("Preview: " + profileName));
        this.parent      = parent;
        this.profileName = profileName;
    }

    @Override
    protected void init() {
        entries = ProfileDiff.diff(profileName);

        addRenderableWidget(Button.builder(Component.literal("Load Profile"), b -> {
            ProfileManager.loadProfile(profileName);
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 82, height - 26, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(width / 2 + 2, height - 26, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        // Avoid "Can only blur once per frame" crash by skipping the blur shader
        g.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        drawContent(g);
        super.render(g, mx, my, delta);
    }

    private void drawContent(GuiGraphics g) {
        g.drawCenteredString(font, title.getString(), width / 2, 8, COL_WHITE);

        int listX = 16;
        int listY = 26;

        if (entries.isEmpty()) {
            g.drawCenteredString(font, "No changes — profile already matches your keybinds.", width / 2, listY + 14, COL_SAME);
            g.drawCenteredString(font, "You can still load it.", width / 2, listY + 28, COL_MGRAY);
            return;
        }

        g.drawString(font, "Action",  listX,       listY, COL_SUBHDR);
        g.drawString(font, "Current", listX + 210, listY, COL_SUBHDR);
        g.drawString(font, "→ New",   listX + 310, listY, COL_SUBHDR);
        g.fill(listX, listY + 10, width - listX, listY + 11, 0x44FFFFFF);
        listY += 14;

        for (int i = 0; i < VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;
            ProfileDiff.Entry e = entries.get(idx);
            int ey    = listY + i * ENTRY_H;
            int color = e.isNew() ? COL_NEW : COL_CHANGED;
            String action = Component.translatable(e.actionName()).getString();
            if (action.length() > 32) action = action.substring(0, 29) + "…";
            g.drawString(font, action, listX, ey, color);
            if (!e.isNew())
                g.drawString(font, friendlyKey(e.currentKey()), listX + 210, ey, COL_LGRAY);
            g.drawString(font, friendlyKey(e.newKey()), listX + 310, ey, COL_WHITE);
        }

        if (entries.size() > VISIBLE)
            g.drawString(font, "Scroll for more  (" + entries.size() + " changes total)",
                    listX, listY + VISIBLE * ENTRY_H + 4, COL_SUBHDR);

        long changed = entries.stream().filter(ProfileDiff.Entry::isChanged).count();
        long added   = entries.stream().filter(ProfileDiff.Entry::isNew).count();
        String summary = changed + " binding(s) will change";
        if (added > 0) summary += "  ·  " + added + " skipped (mod not installed)";
        g.drawCenteredString(font, summary, width / 2, height - 44, COL_SUMMARY);
        g.drawString(font, "Yellow = will change   Grey = unknown action (skipped)",
                listX, height - 36, COL_SUBHDR);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int max = Math.max(0, entries.size() - VISIBLE);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - vAmt));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private static String friendlyKey(String key) {
        if (key == null || key.equals("key.keyboard.unknown")) return "UNBOUND";
        try {
            return InputConstants.getKey(key).getDisplayName().getString();
        } catch (Exception e) {
            return key.replace("key.keyboard.", "").replace("key.mouse.", "Mouse ").replace(".", " ");
        }
    }
}
