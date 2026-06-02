package com.keybindsync.gui;

import com.keybindsync.KeybindSyncConfig;
import com.keybindsync.ProfileDiff;
import com.keybindsync.ProfileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ControlsSidePanel {

    private static final int W     = 126;
    private static final int BW    = (W - 3) / 2;
    private static final int ROW_H = 13;
    private static final int BTN_H = 14;
    private static final int HDR_H = 18;

    private static final int WHITE    = 0xFFFFFFFF;
    private static final int YELLOW   = 0xFFFFFF55;
    private static final int LGRAY    = 0xFFDDDDDD;
    private static final int MGRAY    = 0xFFAAAAAA;
    private static final int DGRAY    = 0xFF666666;
    private static final int ACCENT   = 0xFF5599EE;
    private static final int GREEN    = 0xFF44DD44;
    private static final int RED      = 0xFFEE4444;
    private static final int ORANGE   = 0xFFFFAA22;
    private static final int PANEL_BG = 0xE0050E1E;
    private static final int PANEL_BD = 0xFF1A3A6A;
    private static final int TAB_ACT  = 0xFF0E2244;
    private static final int TAB_BG   = 0xFF081428;
    private static final int TAB_HOV  = 0xFF122855;
    private static final int BTN_BG   = 0xFF0A1A2E;
    private static final int BTN_HOV  = 0xFF172F50;
    private static final int BTN_BD   = 0xFF2A4A7A;
    private static final int TOG_ON   = 0xFF0D3A0D;
    private static final int TOG_OFF  = 0xFF3A0D0D;
    private static final int SEL_BG   = 0xBB1A3A6A;
    private static final int FLD_BG   = 0xFF050E1E;
    private static final int FLD_BD   = 0xFF2A4A7A;
    private static final int FLD_FOC  = 0xFF5599EE;
    private static final int DIV      = 0x44AACCFF;

    private enum Mode  { PROFILES, SETTINGS }
    private enum Focus { NONE, PROFILE_NAME, AUTO_SAVE, AUTO_LOAD, SRV_ADDR, SRV_PROF }

    private Mode  mode  = Mode.PROFILES;
    private Focus focus = Focus.NONE;

    private int panelX  = 0;
    private int listTop = 36;
    private int listBot = 200;
    private int visRows = 6;

    private List<String>  profiles = new ArrayList<>();
    private int           selected = -1;
    private int           scroll   = 0;
    private StringBuilder nameBuf  = new StringBuilder();

    private StringBuilder autoSaveBuf = new StringBuilder();
    private StringBuilder autoLoadBuf = new StringBuilder();
    private StringBuilder srvAddrBuf  = new StringBuilder();
    private StringBuilder srvProfBuf  = new StringBuilder();

    private String status      = "";
    private int    statusColor = GREEN;

    public void buildWidgets(KeyBindsScreen screen) {
        profiles = new ArrayList<>(ProfileManager.listProfiles());
        focus    = Focus.NONE;

        int rowRight = screen.width / 2 + 85;
        int top      = 36;
        int bot      = screen.height - 36;

        for (var w : screen.children()) {
            if (w instanceof AbstractSelectionList<?> asl) {
                rowRight = asl.getRowRight();
                top      = asl.getY();
                bot      = asl.getBottom();
                break;
            }
        }

        listTop = top;
        listBot = Math.min(bot, screen.height - 20);

        int gap = screen.width - rowRight;
        // if the gap to the right of the list fits the panel, centre it there;
        // otherwise anchor to the right screen edge (may overlap the list on narrow screens)
        if (gap >= W + 8) {
            panelX = rowRight + (gap - W) / 2;
        } else {
            panelX = screen.width - W - 4;
        }

        int reserved = 6 + BTN_H + 4 + BTN_H + 4 + BTN_H + 4;
        visRows = Math.max(3, (listBot - listTop - reserved) / ROW_H);

        scroll = Math.max(0, Math.min(scroll, Math.max(0, profiles.size() - visRows)));
    }

    public void draw(GuiGraphics g, Screen screen, int mx, int my) {
        Font font = Minecraft.getInstance().font;

        int hdrTop = listTop - HDR_H - 2;
        int bottom = (mode == Mode.PROFILES) ? listBot + 4 : settingsBottom();

        g.fill(panelX - 5, hdrTop - 1, panelX + W + 5, bottom + 1, PANEL_BD);
        g.fill(panelX - 4, hdrTop,     panelX + W + 4, bottom,     PANEL_BG);

        drawTab(g, font, panelX,        hdrTop, BW,      HDR_H, "Profiles", mode == Mode.PROFILES, mx, my);
        drawTab(g, font, panelX + BW+3, hdrTop, W-BW-3,  HDR_H, "Settings", mode == Mode.SETTINGS, mx, my);

        g.fill(panelX - 4, listTop - 2, panelX + W + 4, listTop - 1, PANEL_BD);

        if (mode == Mode.PROFILES) drawProfiles(g, font, mx, my);
        else                       drawSettings(g, font, mx, my);

        if (!status.isEmpty())
            g.drawString(font, status, panelX, bottom + 3, statusColor);
    }

    private void drawProfiles(GuiGraphics g, Font font, int mx, int my) {
        int y = listTop;

        for (int i = 0; i < visRows; i++) {
            int idx = i + scroll;
            if (idx >= profiles.size()) break;
            int ey  = y + i * ROW_H;
            boolean sel = idx == selected;
            if (sel) g.fill(panelX, ey, panelX + W, ey + ROW_H, SEL_BG);
            String name = profiles.get(idx);
            if (font.width(name) > W - 6) name = shorten(font, name, W - 6);
            g.drawString(font, name, panelX + 3, ey + (ROW_H - 8) / 2, sel ? YELLOW : LGRAY);
        }

        if (profiles.isEmpty())
            g.drawString(font, "No profiles yet", panelX + 3, y + 3, DGRAY);

        if (profiles.size() > visRows) {
            int max = profiles.size() - visRows;
            g.drawString(font, scroll + "/" + max,
                    panelX + W - font.width(scroll + "/" + max) - 2,
                    y + visRows * ROW_H - ROW_H + 3, DGRAY);
        }

        int below = y + visRows * ROW_H + 3;
        g.fill(panelX, below, panelX + W, below + 1, DIV);

        int fy = below + 4;
        drawField(g, font, panelX, fy, W, BTN_H, nameBuf.toString(), "profile name…", focus == Focus.PROFILE_NAME);

        int r1 = fy + BTN_H + 3;
        int r2 = r1 + BTN_H + 3;
        drawBtn(g, font, panelX,        r1, BW, BTN_H, "Save",    mx, my);
        drawBtn(g, font, panelX + BW+3, r1, BW, BTN_H, "Load",    mx, my);
        drawBtn(g, font, panelX,        r2, BW, BTN_H, "Delete",  mx, my);
        drawBtn(g, font, panelX + BW+3, r2, BW, BTN_H, "Preview", mx, my);
    }

    private int settingsBottom() {
        return listTop
                + (BTN_H + 2) + BTN_H + 5
                + (BTN_H + 2) + BTN_H + 5
                + BTN_H + 8
                + 1 + 4 + 10
                + BTN_H + 3 + BTN_H + 4
                + 3 * 10 + 4;
    }

    private void drawSettings(GuiGraphics g, Font font, int mx, int my) {
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        int y = listTop;

        drawToggle(g, font, panelX, y, W, BTN_H,
                "Auto-save on quit: " + onOff(cfg.autoSaveOnQuit), cfg.autoSaveOnQuit, mx, my);
        y += BTN_H + 2;
        drawField(g, font, panelX, y, W, BTN_H, autoSaveBuf.toString(), "save-to profile…", focus == Focus.AUTO_SAVE);
        y += BTN_H + 5;

        drawToggle(g, font, panelX, y, W, BTN_H,
                "Auto-load on launch: " + onOff(cfg.autoLoadOnLaunch), cfg.autoLoadOnLaunch, mx, my);
        y += BTN_H + 2;
        drawField(g, font, panelX, y, W, BTN_H, autoLoadBuf.toString(), "load-from profile…", focus == Focus.AUTO_LOAD);
        y += BTN_H + 5;

        drawBtn(g, font, panelX, y, W, BTN_H, "Save Settings", mx, my);
        y += BTN_H + 8;

        g.fill(panelX, y, panelX + W, y + 1, DIV);
        y += 4;
        g.drawString(font, "Per-server:", panelX + 1, y, MGRAY);
        y += 10;

        drawField(g, font, panelX,        y, BW, BTN_H, srvAddrBuf.toString(), "server addr…", focus == Focus.SRV_ADDR);
        drawField(g, font, panelX + BW+3, y, W-BW-3, BTN_H, srvProfBuf.toString(), "profile…", focus == Focus.SRV_PROF);
        y += BTN_H + 3;

        drawBtn(g, font, panelX,        y, BW, BTN_H, "Add",    mx, my);
        drawBtn(g, font, panelX + BW+3, y, W-BW-3, BTN_H, "Remove", mx, my);
        y += BTN_H + 4;

        int shown = 0;
        for (Map.Entry<String, String> e : cfg.serverProfiles.entrySet()) {
            if (shown >= 3) { g.drawString(font, "…", panelX + 1, y, DGRAY); break; }
            String line = clip(e.getKey(), 9) + " → " + clip(e.getValue(), 8);
            g.drawString(font, line, panelX + 1, y, LGRAY);
            y += 10; shown++;
        }
        if (cfg.serverProfiles.isEmpty())
            g.drawString(font, "No rules yet", panelX + 1, y, DGRAY);
    }

    public boolean handleClick(Screen screen, double mx, double my) {
        int hdrTop = listTop - HDR_H - 2;
        int bottom = (mode == Mode.PROFILES) ? listBot + 4 : settingsBottom();

        if (mx < panelX - 4 || mx > panelX + W + 4 || my < hdrTop || my > bottom) {
            focus = Focus.NONE;
            return false;
        }

        if (my >= hdrTop && my < hdrTop + HDR_H) {
            if (mx >= panelX && mx < panelX + BW) {
                if (mode != Mode.PROFILES) { mode = Mode.PROFILES; focus = Focus.NONE; }
            } else if (mx >= panelX + BW+3) {
                if (mode != Mode.SETTINGS) openSettings();
            }
            return true;
        }

        focus = Focus.NONE;
        if (mode == Mode.PROFILES) clickProfiles(screen, mx, my);
        else                       clickSettings(screen, mx, my);
        return true;
    }

    private void clickProfiles(Screen screen, double mx, double my) {
        int y     = listTop;
        int below = y + visRows * ROW_H + 3;
        int fy    = below + 4;
        int r1    = fy + BTN_H + 3;
        int r2    = r1 + BTN_H + 3;

        if (my >= y && my < y + visRows * ROW_H) {
            int idx = (int)(my - y) / ROW_H + scroll;
            if (idx >= 0 && idx < profiles.size()) {
                selected = idx;
                nameBuf  = new StringBuilder(profiles.get(idx));
            }
            return;
        }
        if (hit(mx, my, panelX, fy, W, BTN_H)) { focus = Focus.PROFILE_NAME; return; }
        if (hit(mx, my, panelX,        r1, BW, BTN_H)) { doSave(screen);    return; }
        if (hit(mx, my, panelX + BW+3, r1, BW, BTN_H)) { doLoad(screen);    return; }
        if (hit(mx, my, panelX,        r2, BW, BTN_H)) { doDelete(screen);  return; }
        if (hit(mx, my, panelX + BW+3, r2, BW, BTN_H)) { doPreview(screen); }
    }

    private void clickSettings(Screen screen, double mx, double my) {
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        int y = listTop;

        if (hit(mx, my, panelX, y, W, BTN_H)) {
            cfg.autoSaveOnQuit = !cfg.autoSaveOnQuit; cfg.save(); return;
        }
        y += BTN_H + 2;
        if (hit(mx, my, panelX, y, W, BTN_H)) { focus = Focus.AUTO_SAVE; return; }
        y += BTN_H + 5;

        if (hit(mx, my, panelX, y, W, BTN_H)) {
            cfg.autoLoadOnLaunch = !cfg.autoLoadOnLaunch; cfg.save(); return;
        }
        y += BTN_H + 2;
        if (hit(mx, my, panelX, y, W, BTN_H)) { focus = Focus.AUTO_LOAD; return; }
        y += BTN_H + 5;

        if (hit(mx, my, panelX, y, W, BTN_H)) { saveSettings(); return; }
        y += BTN_H + 8 + 1 + 4 + 10;

        if (hit(mx, my, panelX,        y, BW, BTN_H)) { focus = Focus.SRV_ADDR; return; }
        if (hit(mx, my, panelX + BW+3, y, W-BW-3, BTN_H)) { focus = Focus.SRV_PROF; return; }
        y += BTN_H + 3;
        if (hit(mx, my, panelX,        y, BW, BTN_H)) { addRule(); return; }
        if (hit(mx, my, panelX + BW+3, y, W-BW-3, BTN_H)) { removeRule(); }
    }

    public boolean handleScroll(Screen screen, double mx, double my, double delta) {
        if (mode != Mode.PROFILES) return false;
        if (mx < panelX || mx > panelX + W) return false;
        if (my < listTop || my > listTop + visRows * ROW_H) return false;
        scroll = clamp((int)(scroll - delta), 0, Math.max(0, profiles.size() - visRows));
        return true;
    }

    public boolean isAnyFieldFocused() { return focus != Focus.NONE; }

    public void handleKeyPress(int key, int modifiers) {
        StringBuilder buf = switch (focus) {
            case PROFILE_NAME -> nameBuf;
            case AUTO_SAVE    -> autoSaveBuf;
            case AUTO_LOAD    -> autoLoadBuf;
            case SRV_ADDR     -> srvAddrBuf;
            case SRV_PROF     -> srvProfBuf;
            default           -> null;
        };
        if (buf == null) return;

        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!buf.isEmpty()) buf.deleteCharAt(buf.length() - 1);
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            focus = Focus.NONE;
        } else if (buf.length() < 48) {
            char c = toChar(key, shift);
            if (c != 0) buf.append(c);
        }
    }

    private void doSave(Screen screen) {
        String name = activeName();
        if (name.isEmpty()) { setStatus("Enter a name!", RED); return; }
        ProfileManager.saveProfile(name);
        profiles = new ArrayList<>(ProfileManager.listProfiles());
        setStatus("Saved: " + name, GREEN);
    }

    private void doLoad(Screen screen) {
        String name = activeName();
        if (name.isEmpty()) { setStatus("Select or type a name!", RED); return; }
        if (ProfileManager.loadProfile(name)) {
            int c = ProfileDiff.detectConflicts().size();
            setStatus(c > 0 ? "Loaded (" + c + " conflicts)" : "Loaded: " + name,
                      c > 0 ? ORANGE : GREEN);
        } else {
            setStatus("Not found: " + name, RED);
        }
    }

    private void doDelete(Screen screen) {
        String name = activeName();
        if (name.isEmpty()) { setStatus("Select a profile!", RED); return; }
        ProfileManager.deleteProfile(name);
        profiles = new ArrayList<>(ProfileManager.listProfiles());
        nameBuf  = new StringBuilder();
        selected = -1;
        setStatus("Deleted: " + name, ORANGE);
    }

    private void doPreview(Screen screen) {
        String name = activeName();
        if (name.isEmpty()) { setStatus("Select a profile!", RED); return; }
        Minecraft.getInstance().setScreen(new DiffScreen(screen, name));
    }

    private void openSettings() {
        mode = Mode.SETTINGS;
        focus = Focus.NONE;
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        autoSaveBuf = new StringBuilder(cfg.autoSaveProfile);
        autoLoadBuf = new StringBuilder(cfg.autoLoadProfile);
        srvAddrBuf  = new StringBuilder();
        srvProfBuf  = new StringBuilder();
    }

    private void saveSettings() {
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        cfg.autoSaveProfile = autoSaveBuf.toString().trim();
        cfg.autoLoadProfile = autoLoadBuf.toString().trim();
        cfg.save();
        setStatus("Settings saved!", GREEN);
    }

    private void addRule() {
        String addr = srvAddrBuf.toString().trim().toLowerCase();
        String prof = srvProfBuf.toString().trim();
        if (addr.isEmpty() || prof.isEmpty()) { setStatus("Fill both fields!", RED); return; }
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        cfg.serverProfiles.put(addr, prof);
        cfg.save();
        srvAddrBuf = new StringBuilder();
        srvProfBuf = new StringBuilder();
        setStatus("Added rule", GREEN);
    }

    private void removeRule() {
        String addr = srvAddrBuf.toString().trim().toLowerCase();
        if (addr.isEmpty()) { setStatus("Type address to remove", RED); return; }
        KeybindSyncConfig cfg = KeybindSyncConfig.get();
        if (cfg.serverProfiles.remove(addr) != null) { cfg.save(); setStatus("Removed: " + addr, ORANGE); }
        else setStatus("No rule for: " + addr, RED);
    }

    private static void drawTab(GuiGraphics g, Font font, int x, int y, int w, int h,
                                String label, boolean active, int mx, int my) {
        boolean hov = hit(mx, my, x, y, w, h);
        g.fill(x, y, x+w, y+h, active ? TAB_ACT : (hov ? TAB_HOV : TAB_BG));
        if (active) g.fill(x, y+h-2, x+w, y+h, ACCENT);
        int tx = x + (w - font.width(label)) / 2;
        g.drawString(font, label, tx, y + (h - 8) / 2, active ? WHITE : (hov ? LGRAY : MGRAY));
    }

    private static void drawBtn(GuiGraphics g, Font font, int x, int y, int w, int h,
                                String label, int mx, int my) {
        boolean hov = hit(mx, my, x, y, w, h);
        g.fill(x, y, x+w, y+h, hov ? BTN_HOV : BTN_BG);
        border(g, x, y, w, h, BTN_BD);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, hov ? WHITE : LGRAY);
    }

    private static void drawToggle(GuiGraphics g, Font font, int x, int y, int w, int h,
                                   String label, boolean on, int mx, int my) {
        boolean hov = hit(mx, my, x, y, w, h);
        g.fill(x, y, x+w, y+h, on ? TOG_ON : TOG_OFF);
        if (hov) g.fill(x, y, x+w, y+h, 0x1AFFFFFF);
        border(g, x, y, w, h, BTN_BD);
        String s = font.width(label) > w - 6 ? shorten(font, label, w - 6) : label;
        g.drawString(font, s, x + 3, y + (h - 8) / 2, WHITE);
    }

    private static void drawField(GuiGraphics g, Font font, int x, int y, int w, int h,
                                  String value, String hint, boolean focused) {
        g.fill(x, y, x+w, y+h, FLD_BG);
        border(g, x, y, w, h, focused ? FLD_FOC : FLD_BD);
        boolean blink = (System.currentTimeMillis() / 530) % 2 == 0;
        String display = value.isEmpty() ? hint : (value + (focused && blink ? "│" : ""));
        int color = value.isEmpty() ? DGRAY : LGRAY;
        while (font.width(display) > w - 6 && display.length() > 1)
            display = display.substring(1);
        g.drawString(font, display, x + 3, y + (h - 8) / 2, color);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x,     y,     x+w,   y+1,   c);
        g.fill(x,     y+h-1, x+w,   y+h,   c);
        g.fill(x,     y,     x+1,   y+h,   c);
        g.fill(x+w-1, y,     x+w,   y+h,   c);
    }

    private String activeName() {
        String t = nameBuf.toString().trim();
        return (!t.isEmpty()) ? t
                : (selected >= 0 && selected < profiles.size()) ? profiles.get(selected)
                : "";
    }

    private void setStatus(String msg, int color) { status = msg; statusColor = color; }
    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }
    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }
    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
    private static String shorten(Font font, String s, int maxPx) {
        while (font.width(s) > maxPx && s.length() > 1) s = s.substring(0, s.length() - 1);
        return s.endsWith("…") ? s : s + "…";
    }
    private static char toChar(int key, boolean shift) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            return shift ? (char)('A' + key - GLFW.GLFW_KEY_A) : (char)('a' + key - GLFW.GLFW_KEY_A);
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9 && !shift)
            return (char)('0' + key - GLFW.GLFW_KEY_0);
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE  -> ' ';
            case GLFW.GLFW_KEY_MINUS  -> shift ? '_' : '-';
            case GLFW.GLFW_KEY_PERIOD -> '.';
            default -> 0;
        };
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
