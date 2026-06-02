package com.keybindsync.gui;

import com.keybindsync.KeybindSyncConfig;
import com.keybindsync.ProfileDiff;
import com.keybindsync.ProfileManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProfileScreen extends Screen {

    public enum Mode { SAVE, LOAD, MANAGE }
    private enum Tab  { PROFILES, SETTINGS }

    // ── layout constants ──────────────────────────────────────────────────────
    private static final int LX      = 14;    // left margin
    private static final int LIST_W  = 170;
    private static final int ROW_H   = 16;
    private static final int VISIBLE = 9;
    private static final int LIST_Y  = 44;    // list start Y
    private static final int GAP     = 14;    // right panel gap

    // ── opaque ARGB (0xFF prefix required in 26.1.2) ─────────────────────────
    private static final int WHITE    = 0xFFFFFFFF;
    private static final int YELLOW   = 0xFFFFFF55;
    private static final int LGRAY    = 0xFFDDDDDD;
    private static final int MGRAY    = 0xFFAAAAAA;
    private static final int DGRAY    = 0xFF777777;
    private static final int XDGRAY   = 0xFF555555;
    private static final int ACCENT   = 0xFF66AAFF;
    private static final int GREEN    = 0xFF55FF55;
    private static final int RED      = 0xFFFF5555;
    private static final int ORANGE   = 0xFFFFAA00;
    private static final int LIST_BG  = 0x99000000;
    private static final int SEL_BG   = 0xBB1A4080;
    private static final int DIV      = 0x44FFFFFF;

    // ── state ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final Mode   initialMode;
    private Tab          activeTab = Tab.PROFILES;

    private EditBox      nameField;
    private EditBox      searchField;
    private List<String> allProfiles;
    private List<String> shown;
    private int          selected    = -1;
    private int          scroll      = 0;
    private String       status      = "";
    private int          statusColor = GREEN;

    // settings tab fields
    private EditBox autoSaveField;
    private EditBox autoLoadField;
    private EditBox srvAddrField;
    private EditBox srvProfField;

    public ProfileScreen(Screen parent, Mode initialMode) {
        super(Component.literal("KeybindSync"));
        this.parent      = parent;
        this.initialMode = initialMode;
    }

    @Override
    protected void init() {
        allProfiles = new ArrayList<>(ProfileManager.listProfiles());
        shown       = allProfiles;
        clearWidgets();
        buildTabs();
        if (activeTab == Tab.PROFILES) buildProfilesTab();
        else                           buildSettingsTab();
    }

    // ── tabs ──────────────────────────────────────────────────────────────────
    private void buildTabs() {
        int tw = 90;
        addRenderableWidget(Button.builder(
                Component.literal(activeTab == Tab.PROFILES ? "▶ Profiles" : "  Profiles"),
                b -> { activeTab = Tab.PROFILES; rebuildWidgets(); })
                .bounds(LX, 18, tw, 16).build());
        addRenderableWidget(Button.builder(
                Component.literal(activeTab == Tab.SETTINGS ? "▶ Settings" : "  Settings"),
                b -> { activeTab = Tab.SETTINGS; rebuildWidgets(); })
                .bounds(LX + tw + 4, 18, tw, 16).build());
    }

    // ── profiles tab ──────────────────────────────────────────────────────────
    private void buildProfilesTab() {
        int listBottom = LIST_Y + VISIBLE * ROW_H;
        int rightX     = LX + LIST_W + GAP;
        int fw         = width - rightX - LX;

        // search field sits below the list
        searchField = new EditBox(font, LX, listBottom + 6, LIST_W, 14, Component.literal("search"));
        searchField.setMaxLength(64);
        searchField.setResponder(this::applySearch);
        addRenderableWidget(searchField);

        // name field
        nameField = new EditBox(font, rightX, LIST_Y, fw, 18, Component.literal("profile name"));
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);
        setInitialFocus(nameField);

        // buttons: 2×2 grid below name field
        int bh = 16, bw = (fw - 4) / 2;
        int bx2 = rightX + bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Save"),    b -> doSave()).bounds(rightX, LIST_Y + 22, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Load"),    b -> doLoad()).bounds(bx2,   LIST_Y + 22, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Preview"), b -> doPreview()).bounds(rightX, LIST_Y + 42, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"),  b -> doDelete()).bounds(bx2,   LIST_Y + 42, bw, bh).build());

        // close at bottom
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 40, height - 24, 80, 18).build());

        if (initialMode == Mode.LOAD && !shown.isEmpty()) {
            selected = 0;
            nameField.setValue(shown.get(0));
        }
    }

    // ── settings tab ─────────────────────────────────────────────────────────
    //  Layout: labels live ABOVE their widgets (no overlap).
    //  Each section: header text → toggle button → field → gap → next section.
    private void buildSettingsTab() {
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        int x  = LX;
        int fw = width - LX * 2;   // full usable width
        int y  = LIST_Y;

        // ── Auto-save ─────────────────────────────────────────────────────────
        // Toggle (self-labelled)
        addRenderableWidget(Button.builder(
                Component.literal("Auto-save on quit: " + onOff(cfg.autoSaveOnQuit)),
                b -> { cfg.autoSaveOnQuit = !cfg.autoSaveOnQuit; cfg.save(); rebuildWidgets(); })
                .bounds(x, y, fw, 16).build());
        // Field at y+20 (clear 4px gap after 16px button)
        autoSaveField = editBox(x, y + 20, fw, cfg.autoSaveProfile, "profile name to save into");
        addRenderableWidget(autoSaveField);

        // ── Auto-load ─────────────────────────────────────────────────────────
        y += 46;
        addRenderableWidget(Button.builder(
                Component.literal("Auto-load on launch: " + onOff(cfg.autoLoadOnLaunch)),
                b -> { cfg.autoLoadOnLaunch = !cfg.autoLoadOnLaunch; cfg.save(); rebuildWidgets(); })
                .bounds(x, y, fw, 16).build());
        autoLoadField = editBox(x, y + 20, fw, cfg.autoLoadProfile, "profile name to load");
        addRenderableWidget(autoLoadField);

        // ── Per-server section ────────────────────────────────────────────────
        y += 48;
        // Divider line drawn in extractBackground at this y — widgets start below
        int fieldY = y + 20;   // fields start 20px below the section header text
        int hw     = (fw - 4) / 2;
        srvAddrField = editBox(x,          fieldY,      hw, "", "server address");
        srvProfField = editBox(x + hw + 4, fieldY,      hw, "", "profile name");
        addRenderableWidget(srvAddrField);
        addRenderableWidget(srvProfField);
        addRenderableWidget(Button.builder(Component.literal("Add"),    b -> addRule())
                .bounds(x,           fieldY + 18, hw, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeRule())
                .bounds(x + hw + 4,  fieldY + 18, hw, 14).build());

        // Save Settings + Close — side by side at bottom
        addRenderableWidget(Button.builder(Component.literal("Save Settings"), b -> saveSettings())
                .bounds(width / 2 - 84, height - 24, 82, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 + 2, height - 24, 78, 18).build());
    }

    private EditBox editBox(int x, int y, int w, String val, String hint) {
        EditBox f = new EditBox(font, x, y, w, 16, Component.literal(hint));
        f.setMaxLength(128);
        f.setValue(val);
        return f;
    }

    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }

    // ── search ────────────────────────────────────────────────────────────────
    private void applySearch(String q) {
        String lq = q.trim().toLowerCase();
        shown    = lq.isEmpty() ? allProfiles
                : allProfiles.stream().filter(n -> n.toLowerCase().contains(lq)).toList();
        scroll   = 0;
        selected = -1;
    }

    // ── profile actions ───────────────────────────────────────────────────────
    private void doSave() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) { setStatus("Enter a profile name!", RED); return; }
        ProfileManager.saveProfile(name);
        allProfiles = new ArrayList<>(ProfileManager.listProfiles());
        applySearch(searchField != null ? searchField.getValue() : "");
        setStatus("Saved: " + name, GREEN);
    }

    private void doLoad() {
        String name = activeName();
        if (name == null) { setStatus("Select or type a profile name!", RED); return; }
        if (ProfileManager.loadProfile(name)) {
            int c = ProfileDiff.detectConflicts().size();
            setStatus(c > 0 ? "Loaded: " + name + "  (" + c + " conflicts!)" : "Loaded: " + name,
                      c > 0 ? ORANGE : GREEN);
        } else {
            setStatus("Not found: " + name, RED);
        }
    }

    private void doPreview() {
        String name = activeName();
        if (name == null) { setStatus("Select or type a profile name!", RED); return; }
        minecraft.setScreen(new DiffScreen(this, name));
    }

    private void doDelete() {
        String name = activeName();
        if (name == null) { setStatus("Select a profile to delete!", RED); return; }
        ProfileManager.deleteProfile(name);
        allProfiles = new ArrayList<>(ProfileManager.listProfiles());
        applySearch(searchField != null ? searchField.getValue() : "");
        nameField.setValue("");
        setStatus("Deleted: " + name, ORANGE);
    }

    private void saveSettings() {
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        cfg.autoSaveProfile = autoSaveField.getValue().trim();
        cfg.autoLoadProfile = autoLoadField.getValue().trim();
        cfg.save();
        setStatus("Settings saved!", GREEN);
    }

    private void addRule() {
        String addr = srvAddrField.getValue().trim().toLowerCase();
        String prof = srvProfField.getValue().trim();
        if (addr.isEmpty() || prof.isEmpty()) { setStatus("Fill in both fields!", RED); return; }
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        cfg.serverProfiles.put(addr, prof);
        cfg.save();
        srvAddrField.setValue(""); srvProfField.setValue("");
        setStatus("Added: " + addr + " → " + prof, GREEN);
    }

    private void removeRule() {
        String addr = srvAddrField.getValue().trim().toLowerCase();
        if (addr.isEmpty()) { setStatus("Type the address to remove!", RED); return; }
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        if (cfg.serverProfiles.remove(addr) != null) { cfg.save(); setStatus("Removed: " + addr, ORANGE); }
        else setStatus("No rule for: " + addr, RED);
    }

    private String activeName() {
        if (nameField != null) { String t = nameField.getValue().trim(); if (!t.isEmpty()) return t; }
        if (selected >= 0 && shown != null && selected < shown.size()) return shown.get(selected);
        return null;
    }

    private void setStatus(String msg, int color) { status = msg; statusColor = color; }

    // ── render ────────────────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);

        // title
        g.centeredText(font, title, width / 2, 6, WHITE);
        // tab divider
        g.fill(LX, 36, width - LX, 37, DIV);

        if (activeTab == Tab.PROFILES) drawProfilesBg(g);
        else                           drawSettingsBg(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        if (!status.isEmpty())
            g.centeredText(font, Component.literal(status), width / 2, height - 46, statusColor);
    }

    private void drawProfilesBg(GuiGraphicsExtractor g) {
        List<String> fp = shown != null ? shown : List.of();
        int lb  = LIST_Y + VISIBLE * ROW_H;
        int rx  = LX + LIST_W + GAP;

        // column labels
        g.text(font, "Profiles  " + fp.size() + "/" + allProfiles.size(), LX, LIST_Y - 10, MGRAY);
        g.text(font, "Name:", rx, LIST_Y - 10, MGRAY);

        // list background
        g.fill(LX - 1, LIST_Y - 1, LX + LIST_W + 1, lb + 1, LIST_BG);

        // rows
        for (int i = 0; i < VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= fp.size()) break;
            int ey  = LIST_Y + i * ROW_H;
            boolean sel = idx == selected;
            if (sel) g.fill(LX, ey, LX + LIST_W, ey + ROW_H, SEL_BG);
            String lbl = fp.get(idx);
            if (lbl.length() > 22) lbl = lbl.substring(0, 19) + "…";
            g.text(font, lbl, LX + 3, ey + (ROW_H - 8) / 2, sel ? YELLOW : LGRAY);
        }

        if (fp.isEmpty())
            g.text(font, allProfiles.isEmpty() ? "No profiles — type a name and Save!" : "No matches.",
                    LX + 3, LIST_Y + 4, DGRAY);

        // search label
        g.text(font, "Search:", LX, lb + 4 - 10, DGRAY);

        // scroll hint
        if (fp.size() > VISIBLE) {
            int max = fp.size() - VISIBLE;
            g.text(font, (scroll + 1) + "/" + (max + 1), LX + LIST_W - 22, lb + 6, XDGRAY);
        }
    }

    private void drawSettingsBg(GuiGraphicsExtractor g) {
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        int x = LX, y = LIST_Y;

        // Auto-save — label sits in the 4px gap between button (h=16) and field (y+20)
        // Gap: button ends at y+16, label at y+17, field at y+20  — no overlap
        g.text(font, "Profile to save into when you disconnect:", x, y + 17, DGRAY);

        y += 46;
        g.text(font, "Profile to load when Minecraft starts:", x, y + 17, DGRAY);

        // Per-server section header — sits at y+48, fields at y+48+20=y+68
        y += 48;
        g.fill(x, y - 4, width - x, y - 3, DIV);   // subtle divider above section
        g.text(font, "Per-Server Auto-Switch", x, y, ACCENT);
        g.text(font, "Loads a profile when you join a specific server.", x, y + 10, DGRAY);
        // column labels — at fieldY - 10 = y+10 — these are above the fields (at y+20)
        // fields are at y+20, column labels at y+10: 10px gap  ✓
        g.text(font, "Server address", x + 2,               y + 10 + 2, XDGRAY);
        g.text(font, "Profile name",   x + (width-LX*2)/2 + 6, y + 10 + 2, XDGRAY);

        // existing rules — rendered below the Add/Remove buttons (at y+20+18+14+6 = y+58)
        int rulesY = y + 58;
        if (cfg.serverProfiles.isEmpty()) {
            g.text(font, "No rules yet.", x + 2, rulesY, XDGRAY);
        } else {
            for (Map.Entry<String, String> e : cfg.serverProfiles.entrySet()) {
                if (rulesY > height - 60) { g.text(font, "…", x + 2, rulesY, DGRAY); break; }
                g.text(font, e.getKey() + "  →  " + e.getValue(), x + 2, rulesY, LGRAY);
                rulesY += 10;
            }
        }
    }

    // ── input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (activeTab == Tab.PROFILES && shown != null) {
            double mx = event.x(), my = event.y();
            if (mx >= LX && mx < LX + LIST_W && my >= LIST_Y && my < LIST_Y + VISIBLE * ROW_H) {
                int idx = (int)(my - LIST_Y) / ROW_H + scroll;
                if (idx >= 0 && idx < shown.size()) {
                    selected = idx;
                    nameField.setValue(shown.get(idx));
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (activeTab == Tab.PROFILES && shown != null) {
            int max = Math.max(0, shown.size() - VISIBLE);
            scroll = (int) Math.max(0, Math.min(max, scroll - vAmt));
        }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
