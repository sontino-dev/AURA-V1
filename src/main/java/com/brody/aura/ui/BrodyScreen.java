package com.brody.aura.ui;

import com.brody.aura.KillAuraMod;
import com.brody.aura.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Brody Aura ClickGUI.
 *
 * Fully hand-drawn (DrawContext only, no vanilla widgets) so the whole panel
 * behaves like a proper HVH client window: draggable header, two tabs
 * (Combat / Config), per-module keybind rebinding, sliders, toggles and
 * cyclers. Everything is persisted to the JSON config on close.
 *
 * Layout:
 *   +------------------------------------------+
 *   | Brody Aura          [Combat] [Config]    |  <- drag header
 *   |------------------------------------------|
 *   | KillAura            ON          [R]      |  <- row click toggles,
 *   | HvhMode             OFF         [H]      |     right zone = bind slot
 *   | ...                                      |
 *   |------------------------------------------|
 *   | hovered module description               |  <- footer
 *   +------------------------------------------+
 *
 * Config tab rows:
 *   sliders : Reach / Min CPS / Max CPS / Rotation Speed / Max Reach Soft
 *   cyclers : Vulcan Profile (LEGIT-BALANCED-RAGE) / Target Sort
 *   toggles : Vulcan Safe / Humanized Aim / GCD Compliant / AI Enabled / HUD
 */
public class BrodyScreen extends Screen {

    // ---- geometry ----
    private static final int PANEL_W = 252;
    private static final int HEADER_H = 24;
    private static final int FOOTER_H = 16;
    private static final int ROW_H_COMBAT = 24;
    private static final int ROW_H_CONFIG = 18;
    private static final int BIND_ZONE_W = 58;
    private static final int TAB_W = 56;

    // ---- theme ----
    private static final int C_BG = 0xE60E1118;
    private static final int C_HEADER = 0xF0151B28;
    private static final int C_ROW = 0x24181F2E;
    private static final int C_ROW_ALT = 0x16121926;
    private static final int C_HOVER = 0x2EFFFFFF;
    private static final int C_ACCENT = 0xFFFF4F9A;
    private static final int C_ACCENT2 = 0xFF53D8FB;
    private static final int C_TEXT = 0xFFE8ECF4;
    private static final int C_TEXT_DIM = 0xFF8A93A6;
    private static final int C_ON = 0xFF3DF0A2;
    private static final int C_OFF = 0xFF5C6474;
    private static final int C_TRACK = 0xFF232936;
    private static final int C_KNOB = 0xFFF2F6FF;
    private static final int C_CAPTURE = 0xFF7A5CFF;

    private static final String[] CONFIG_ROWS = {
            "slider:reach", "slider:minCps", "slider:maxCps", "slider:rotationSpeed", "slider:maxReachSoft",
            "cycle:vulcanProfile", "cycle:targetSort",
            "toggle:vulcanSafe", "toggle:humanizedAim", "toggle:gcdCompliant", "toggle:aiEnabled", "toggle:hudEnabled"
    };

    private int tab = 0; // 0 = Combat, 1 = Config
    private String awaitingBindFor = null;
    private int draggingSlider = -1;
    private boolean draggingPanel = false;
    private int dragOffX;
    private int dragOffY;
    private String hoveredModuleDesc = null;

    public BrodyScreen() {
        super(Text.literal("Brody Aura ClickGUI"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------- geometry

    private int px() {
        return clampInt(KillAuraMod.CONFIG.guiX, 2, Math.max(2, this.width - PANEL_W - 2));
    }

    private int py() {
        int h = HEADER_H + rowCount() * rowH() + FOOTER_H;
        return clampInt(KillAuraMod.CONFIG.guiY, 2, Math.max(2, this.height - h - 2));
    }

    private int rowCount() {
        return tab == 0 ? KillAuraMod.MODULES.all().size() : CONFIG_ROWS.length;
    }

    private int rowH() {
        return tab == 0 ? ROW_H_COMBAT : ROW_H_CONFIG;
    }

    private int panelH() {
        return HEADER_H + rowCount() * rowH() + FOOTER_H;
    }

    private static int clampInt(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    // --------------------------------------------------------------- render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // vanilla dim background

        int px = px();
        int py = py();
        int ph = panelH();

        // panel + accents
        context.fill(px, py, px + PANEL_W, py + ph, C_BG);
        context.fill(px, py, px + PANEL_W, py + 2, C_ACCENT);
        context.fill(px, py + HEADER_H - 1, px + PANEL_W, py + HEADER_H, C_ACCENT);

        // header
        context.drawTextWithShadow(this.textRenderer, "Brody Aura", px + 8, py + 8, C_ACCENT);

        int t1x = px + PANEL_W - 2 * TAB_W - 14;
        int t2x = px + PANEL_W - TAB_W - 8;
        drawTab(context, "Combat", t1x, py, tab == 0, mouseX, mouseY);
        drawTab(context, "Config", t2x, py, tab == 1, mouseX, mouseY);

        // rows
        hoveredModuleDesc = null;
        List<Module> modules = KillAuraMod.MODULES.all();
        for (int i = 0; i < rowCount(); i++) {
            int ry = py + HEADER_H + i * rowH();
            boolean hover = mouseX >= px + 1 && mouseX <= px + PANEL_W - 1
                    && mouseY >= ry && mouseY <= ry + rowH() - 1;
            context.fill(px + 1, ry, px + PANEL_W - 1, ry + rowH() - 1,
                    hover ? C_HOVER : (i % 2 == 0 ? C_ROW : C_ROW_ALT));

            if (tab == 0) {
                Module m = modules.get(i);
                drawModuleRow(context, m, px, ry, mouseX, mouseY);
                if (hover) hoveredModuleDesc = m.getDescription();
            } else {
                drawConfigRow(context, CONFIG_ROWS[i], px, ry);
            }
        }

        // footer
        context.fill(px + 1, py + ph - FOOTER_H, px + PANEL_W - 1, py + ph, C_HEADER);
        String footer;
        if (awaitingBindFor != null) {
            footer = "Press a key for " + awaitingBindFor + " (ESC cancel, DEL clear)";
        } else if (tab == 0 && hoveredModuleDesc != null) {
            footer = hoveredModuleDesc;
        } else {
            footer = "Drag header to move - ESC saves and closes";
        }
        context.drawTextWithShadow(this.textRenderer, footer, px + 6, py + ph - FOOTER_H + 4, C_TEXT_DIM);
    }

    private void drawTab(DrawContext context, String label, int x, int py, boolean active, int mx, int my) {
        boolean hover = mx >= x && mx <= x + TAB_W && my >= py && my <= py + HEADER_H - 1;
        int tx = x + (TAB_W - this.textRenderer.getWidth(label)) / 2;
        if (active) {
            context.fill(x, py + 3, x + TAB_W, py + HEADER_H - 3, C_ACCENT);
            context.drawTextWithShadow(this.textRenderer, label, tx, py + 8, 0xFF12141C);
        } else {
            context.drawTextWithShadow(this.textRenderer, label, tx, py + 8, hover ? C_TEXT : C_TEXT_DIM);
        }
    }

    private void drawModuleRow(DrawContext context, Module m, int px, int ry, int mouseX, int mouseY) {
        boolean on = m.isEnabled();
        String name = m.getName();
        context.drawTextWithShadow(this.textRenderer, name, px + 12, ry + 8, on ? C_ON : C_TEXT);

        // bind zone
        int bx = px + PANEL_W - BIND_ZONE_W - 4;
        boolean capturing = name.equals(awaitingBindFor);
        String keyLabel = capturing ? "[ ... ]" : "[" + keyName(m.getKeyBind()) + "]";
        int kw = this.textRenderer.getWidth(keyLabel);
        boolean bindHover = mouseX >= bx - 4 && mouseX <= px + PANEL_W - 4
                && mouseY >= ry && mouseY <= ry + rowH() - 1;
        context.fill(bx - 4, ry + 3, px + PANEL_W - 4, ry + rowH() - 3,
                capturing ? 0x507A5CFF : (bindHover ? 0x30FFFFFF : 0x2010141C));
        context.drawTextWithShadow(this.textRenderer, keyLabel, bx + (BIND_ZONE_W - kw) / 2 - 2, ry + 8,
                capturing ? C_CAPTURE : (m.getKeyBind() >= 0 ? C_ACCENT2 : C_OFF));

        // enabled pill
        context.fill(px + 4, ry + 9, px + 7, ry + rowH() - 9, on ? C_ON : C_OFF);
    }

    private void drawConfigRow(DrawContext context, String id, int px, int ry) {
        String kind = id.substring(0, id.indexOf(':'));
        String key = id.substring(id.indexOf(':') + 1);

        switch (kind) {
            case "slider" -> {
                double[] range = sliderRange(key);
                double v = sliderGet(key);
                String value = formatValue(key, v);
                context.drawTextWithShadow(this.textRenderer, rowLabel(id), px + 10, ry + 2, C_TEXT_DIM);
                context.drawTextWithShadow(this.textRenderer, value,
                        px + PANEL_W - 10 - this.textRenderer.getWidth(value), ry + 2, C_ACCENT2);
                // track
                int tx = px + 10;
                int tw = PANEL_W - 20;
                int ty = ry + rowH() - 6;
                context.fill(tx, ty, tx + tw, ty + 3, C_TRACK);
                double t = (v - range[0]) / (range[1] - range[0]);
                int fx = tx + (int) Math.round(t * tw);
                context.fill(tx, ty, fx, ty + 3, C_ACCENT);
                context.fill(fx - 1, ty - 1, fx + 1, ty + 4, C_KNOB);
            }
            case "toggle" -> {
                boolean on = toggleGet(key);
                context.drawTextWithShadow(this.textRenderer, rowLabel(id), px + 10, ry + 5, C_TEXT);
                String state = on ? "ON" : "OFF";
                context.drawTextWithShadow(this.textRenderer, state,
                        px + PANEL_W - 10 - this.textRenderer.getWidth(state), ry + 5, on ? C_ON : C_OFF);
            }
            case "cycle" -> {
                String value = cycleGet(key);
                context.drawTextWithShadow(this.textRenderer, rowLabel(id), px + 10, ry + 5, C_TEXT);
                String shown = "< " + value + " >";
                context.drawTextWithShadow(this.textRenderer, shown,
                        px + PANEL_W - 10 - this.textRenderer.getWidth(shown), ry + 5, C_ACCENT2);
            }
            default -> {
            }
        }
    }

    // ----------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int px = px();
        int py = py();

        if (button == 0) {
            // header area?
            if (mouseY >= py && mouseY <= py + HEADER_H - 1 && mouseX >= px && mouseX <= px + PANEL_W) {
                int t1x = px + PANEL_W - 2 * TAB_W - 14;
                int t2x = px + PANEL_W - TAB_W - 8;
                if (mouseX >= t1x && mouseX <= t1x + TAB_W) {
                    tab = 0;
                    return true;
                }
                if (mouseX >= t2x && mouseX <= t2x + TAB_W) {
                    tab = 1;
                    return true;
                }
                draggingPanel = true;
                dragOffX = (int) mouseX - px;
                dragOffY = (int) mouseY - py;
                return true;
            }

            // row area?
            if (mouseX >= px && mouseX <= px + PANEL_W && mouseY >= py + HEADER_H
                    && mouseY <= py + HEADER_H + rowCount() * rowH()) {
                int idx = (int) ((mouseY - py - HEADER_H) / rowH());
                if (idx >= 0 && idx < rowCount()) {
                    if (tab == 0) {
                        Module m = KillAuraMod.MODULES.all().get(idx);
                        if (mouseX >= px + PANEL_W - BIND_ZONE_W - 6) {
                            awaitingBindFor = m.getName();
                        } else {
                            KillAuraMod.MODULES.toggle(m.getName());
                        }
                        return true;
                    }
                    String id = CONFIG_ROWS[idx];
                    String kind = id.substring(0, id.indexOf(':'));
                    if (kind.equals("slider")) {
                        draggingSlider = idx;
                        applySliderFromMouse(id, mouseX);
                        return true;
                    }
                    if (kind.equals("toggle")) {
                        toggleFlip(id.substring(id.indexOf(':') + 1));
                        return true;
                    }
                    if (kind.equals("cycle")) {
                        cycleNext(id.substring(id.indexOf(':') + 1));
                        return true;
                    }
                }
            }
        } else if (button == 1) {
            // right-click a bind slot to clear it
            if (tab == 0 && mouseX >= px && mouseX <= px + PANEL_W && mouseY >= py + HEADER_H
                    && mouseY <= py + HEADER_H + rowCount() * rowH()) {
                int idx = (int) ((mouseY - py - HEADER_H) / rowH());
                if (idx >= 0 && idx < rowCount()
                        && mouseX >= px + PANEL_W - BIND_ZONE_W - 6) {
                    KillAuraMod.MODULES.all().get(idx).setKeyBind(-1);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingPanel) {
            KillAuraMod.CONFIG.guiX = clampInt((int) mouseX - dragOffX, 2, Math.max(2, this.width - PANEL_W - 2));
            KillAuraMod.CONFIG.guiY = clampInt((int) mouseY - dragOffY, 2, Math.max(2, this.height - panelH() - 2));
            return true;
        }
        if (draggingSlider >= 0 && draggingSlider < CONFIG_ROWS.length) {
            applySliderFromMouse(CONFIG_ROWS[draggingSlider], mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = -1;
        draggingPanel = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingBindFor != null) {
            Module m = KillAuraMod.MODULES.get(awaitingBindFor);
            if (m != null) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    // cancel capture
                } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    m.setKeyBind(-1);
                } else {
                    m.setKeyBind(keyCode);
                }
            }
            awaitingBindFor = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        // persist module binds + panel position on every close
        for (Module m : KillAuraMod.MODULES.all()) {
            KillAuraMod.CONFIG.setExtra("bind:" + m.getName(), m.getKeyBind());
        }
        KillAuraMod.CONFIG.save();
        super.removed();
    }

    // ---------------------------------------------------------- config glue

    private void applySliderFromMouse(String id, double mouseX) {
        String key = id.substring(id.indexOf(':') + 1);
        double[] range = sliderRange(key);
        int tx = px() + 10;
        int tw = PANEL_W - 20;
        double t = clampInt((int) Math.round((mouseX - tx) / (double) tw * 1000.0), 0, 1000) / 1000.0;
        double v = range[0] + t * (range[1] - range[0]);
        sliderSet(key, v);
    }

    private double[] sliderRange(String key) {
        return switch (key) {
            case "reach" -> new double[]{2.0, 4.0};
            case "minCps" -> new double[]{2.0, 10.0};
            case "maxCps" -> new double[]{4.0, 20.0};
            case "rotationSpeed" -> new double[]{2.0, 60.0};
            default -> new double[]{2.5, 3.5}; // maxReachSoft
        };
    }

    private double sliderGet(String key) {
        return switch (key) {
            case "reach" -> KillAuraMod.CONFIG.reach;
            case "minCps" -> KillAuraMod.CONFIG.minCps;
            case "maxCps" -> KillAuraMod.CONFIG.maxCps;
            case "rotationSpeed" -> KillAuraMod.CONFIG.rotationSpeed;
            default -> KillAuraMod.CONFIG.maxReachSoft;
        };
    }

    private void sliderSet(String key, double v) {
        double rounded = Math.round(v * 100.0) / 100.0;
        switch (key) {
            case "reach" -> KillAuraMod.CONFIG.reach = rounded;
            case "minCps" -> KillAuraMod.CONFIG.minCps = rounded;
            case "maxCps" -> KillAuraMod.CONFIG.maxCps = rounded;
            case "rotationSpeed" -> KillAuraMod.CONFIG.rotationSpeed = rounded;
            default -> KillAuraMod.CONFIG.maxReachSoft = rounded;
        }
    }

    private String formatValue(String key, double v) {
        if (key.equals("rotationSpeed")) {
            return String.format(Locale.US, "%.0f", v);
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private void toggleFlip(String key) {
        switch (key) {
            case "vulcanSafe" -> KillAuraMod.CONFIG.vulcanSafe = !KillAuraMod.CONFIG.vulcanSafe;
            case "humanizedAim" -> KillAuraMod.CONFIG.humanizedAim = !KillAuraMod.CONFIG.humanizedAim;
            case "gcdCompliant" -> KillAuraMod.CONFIG.gcdCompliant = !KillAuraMod.CONFIG.gcdCompliant;
            case "aiEnabled" -> KillAuraMod.CONFIG.aiEnabled = !KillAuraMod.CONFIG.aiEnabled;
            case "hudEnabled" -> KillAuraMod.CONFIG.hudEnabled = !KillAuraMod.CONFIG.hudEnabled;
            default -> {
            }
        }
    }

    private boolean toggleGet(String key) {
        return switch (key) {
            case "vulcanSafe" -> KillAuraMod.CONFIG.vulcanSafe;
            case "humanizedAim" -> KillAuraMod.CONFIG.humanizedAim;
            case "gcdCompliant" -> KillAuraMod.CONFIG.gcdCompliant;
            case "aiEnabled" -> KillAuraMod.CONFIG.aiEnabled;
            case "hudEnabled" -> KillAuraMod.CONFIG.hudEnabled;
            default -> false;
        };
    }

    private void cycleNext(String key) {
        if (key.equals("vulcanProfile")) {
            String[] vals = {"LEGIT", "BALANCED", "RAGE"};
            KillAuraMod.CONFIG.vulcanProfile = nextIn(vals, KillAuraMod.CONFIG.vulcanProfile);
        } else {
            String[] vals = {"DISTANCE", "HEALTH", "ANGLE", "FOV"};
            KillAuraMod.CONFIG.targetSort = nextIn(vals, KillAuraMod.CONFIG.targetSort);
        }
    }

    private String cycleGet(String key) {
        if (key.equals("vulcanProfile")) {
            return KillAuraMod.CONFIG.vulcanProfile;
        }
        return KillAuraMod.CONFIG.targetSort;
    }

    private static String nextIn(String[] vals, String current) {
        String c = current == null ? "" : current.trim().toUpperCase(Locale.US);
        for (int i = 0; i < vals.length; i++) {
            if (vals[i].equals(c)) {
                return vals[(i + 1) % vals.length];
            }
        }
        return vals[0];
    }

    private String rowLabel(String id) {
        return switch (id) {
            case "slider:reach" -> "Reach";
            case "slider:minCps" -> "Min CPS";
            case "slider:maxCps" -> "Max CPS";
            case "slider:rotationSpeed" -> "Rotation Speed";
            case "slider:maxReachSoft" -> "Max Reach Soft";
            case "cycle:vulcanProfile" -> "Vulcan Profile";
            case "cycle:targetSort" -> "Target Sort";
            case "toggle:vulcanSafe" -> "Vulcan Safe";
            case "toggle:humanizedAim" -> "Humanized Aim";
            case "toggle:gcdCompliant" -> "GCD Compliant";
            case "toggle:aiEnabled" -> "AI Enabled";
            case "toggle:hudEnabled" -> "HUD Enabled";
            default -> id;
        };
    }

    static String keyName(int code) {
        if (code < 0) return "NONE";
        String name = InputUtil.fromKeyCode(code, -1).getLocalizedText().getString();
        return name == null || name.isEmpty() ? ("KEY" + code) : name.toUpperCase(Locale.US);
    }
}
