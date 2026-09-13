package com.brody.aura.ui;

import com.brody.aura.KillAuraMod;
import com.brody.aura.module.KillAura;
import com.brody.aura.module.Module;
import com.brody.aura.module.VulcanMode;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;

import java.util.Locale;

/**
 * Brody Aura HUD overlay.
 *
 * Top-left block:
 *   - watermark "Brody v1.0"
 *   - list of enabled modules (with their bind key)
 *   - live aura status when KillAura is running: target name + distance,
 *     AI CPS, VulcanMode paranoia + profile, recent anticheat flags.
 *
 * Position persists via hudX/hudY in the JSON config; HUD can be hidden from
 * the ClickGUI (Config tab -> HUD Enabled).
 */
public class BrodyHUD implements HudRenderCallback {

    private static final int C_WATERMARK = 0xFFFF4F9A;
    private static final int C_MODULE_ON = 0xFF3DF0A2;
    private static final int C_LABEL = 0xFF8A93A6;
    private static final int C_VALUE = 0xFFE8ECF4;
    private static final int C_PANEL = 0x90120F18;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (!KillAuraMod.CONFIG.hudEnabled) return;

        int x = Math.max(0, KillAuraMod.CONFIG.hudX);
        int y = Math.max(0, KillAuraMod.CONFIG.hudY);
        var tr = mc.textRenderer;

        // watermark chip
        String wm = "Brody v1.0";
        int wmW = tr.getWidth(wm);
        context.fill(x - 3, y - 3, x + wmW + 3, y + 11, C_PANEL);
        context.fill(x - 3, y + 9, x + wmW + 3, y + 11, C_WATERMARK);
        context.drawTextWithShadow(tr, wm, x, y, C_WATERMARK);
        y += 16;

        // enabled modules
        for (Module m : KillAuraMod.MODULES.all()) {
            if (!m.isEnabled()) continue;
            String label = m.getName();
            if (m.getKeyBind() >= 0) {
                label += " [" + BrodyScreen.keyName(m.getKeyBind()) + "]";
            }
            context.fill(x - 3, y - 2, x + tr.getWidth(label) + 3, y + 10, C_PANEL);
            context.drawTextWithShadow(tr, label, x, y, C_MODULE_ON);
            y += 12;
        }

        // live aura status
        Module kaModule = KillAuraMod.MODULES.get("KillAura");
        if (kaModule instanceof KillAura aura && aura.isEnabled()) {
            y += 3;

            Entity target = aura.currentTarget();
            String targetVal = "none";
            if (target != null && mc.player != null) {
                double dist = mc.player.distanceTo(target);
                targetVal = target.getName().getString() + String.format(Locale.US, " (%.1fm)", dist);
            }
            y = drawLabeledLine(context, tr, x, y, "Target", targetVal);

            double cps = KillAuraMod.AI.effectiveCps();
            y = drawLabeledLine(context, tr, x, y, "AI CPS", String.format(Locale.US, "%.1f", cps));

            Module vuModule = KillAuraMod.MODULES.get("VulcanMode");
            if (vuModule instanceof VulcanMode vulcan && vulcan.isEnabled()) {
                String para = String.format(Locale.US, "%d%% %s",
                        (int) vulcan.getParanoia(),
                        vulcan.currentProfile().name());
                y = drawLabeledLine(context, tr, x, y, "Paranoia", para);
            }

            int flags = KillAuraMod.AI.getRecentFlags();
            y = drawLabeledLine(context, tr, x, y, "Flags", String.valueOf(flags));
        }
    }

    private int drawLabeledLine(DrawContext context, net.minecraft.client.font.TextRenderer tr,
                                int x, int y, String label, String value) {
        context.fill(x - 3, y - 2, x + tr.getWidth(label) + tr.getWidth(value) + 14, y + 10, C_PANEL);
        context.drawTextWithShadow(tr, label, x, y, C_LABEL);
        context.drawTextWithShadow(tr, value, x + tr.getWidth(label) + 6, y, C_VALUE);
        return y + 12;
    }
}
