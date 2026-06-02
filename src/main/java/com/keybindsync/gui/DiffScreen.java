package com.keybindsync.gui;

import com.keybindsync.ProfileDiff;
import com.keybindsync.ProfileManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shows a diff of what will change before loading a profile. */
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
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);

        g.centeredText(font, title, width / 2, 8, COL_WHITE);

        int listX = 16;
        int listY = 26;

        if (entries.isEmpty()) {
            g.centeredText(font, Component.literal("No changes — profile already matches your keybinds."),
                    width / 2, listY + 14, COL_SAME);
            g.centeredText(font, Component.literal("You can still load it."),
                    width / 2, listY + 28, COL_MGRAY);
            return;
        }

        // column headers
        g.text(font, "Action",  listX,       listY, COL_SUBHDR);
        g.text(font, "Current", listX + 210, listY, COL_SUBHDR);
        g.text(font, "→ New",   listX + 310, listY, COL_SUBHDR);
        g.fill(listX, listY + 10, width - listX, listY + 11, 0x44FFFFFF);
        listY += 14;

        // entry rows
        for (int i = 0; i < VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;
            ProfileDiff.Entry e = entries.get(idx);
            int ey    = listY + i * ENTRY_H;
            int color = e.isNew() ? COL_NEW : COL_CHANGED;
            String action = Component.translatable(e.actionName()).getString();
            if (action.length() > 32) action = action.substring(0, 29) + "…";
            g.text(font, action, listX, ey, color);
            if (!e.isNew())
                g.text(font, friendlyKey(e.currentKey()), listX + 210, ey, COL_LGRAY);
            g.text(font, friendlyKey(e.newKey()), listX + 310, ey, COL_WHITE);
        }

        // scroll hint
        if (entries.size() > VISIBLE)
            g.text(font, "Scroll for more  (" + entries.size() + " changes total)",
                    listX, listY + VISIBLE * ENTRY_H + 4, COL_SUBHDR);

        // legend
        g.text(font, "Yellow = will change   Grey = unknown action (skipped)",
                listX, height - 44, COL_SUBHDR);

        // summary
        long changed = entries.stream().filter(ProfileDiff.Entry::isChanged).count();
        long added   = entries.stream().filter(ProfileDiff.Entry::isNew).count();
        String summary = changed + " binding(s) will change";
        if (added > 0) summary += "  ·  " + added + " skipped (mod not installed)";
        g.centeredText(font, Component.literal(summary), width / 2, height - 44, COL_SUMMARY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int max = Math.max(0, entries.size() - VISIBLE);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - vAmt));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    /** Returns a localized display name for a key string (e.g. "Left Shift" instead of "key.keyboard.left.shift"). */
    private static String friendlyKey(String key) {
        if (key == null || key.equals("key.keyboard.unknown")) return "UNBOUND";
        try {
            return InputConstants.getKey(key).getDisplayName().getString();
        } catch (Exception e) {
            return key.replace("key.keyboard.", "").replace("key.mouse.", "Mouse ").replace(".", " ");
        }
    }
}
