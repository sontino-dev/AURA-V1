package com.brody.aura.module;

import com.brody.aura.KillAuraMod;
import net.minecraft.client.MinecraftClient;

/**
 * AutoSprint - keeps the player sprinting, respects aura sprint-reset windows.
 */
public class AutoSprint extends Module {

    public AutoSprint() {
        super("AutoSprint", "Automatically sprints while moving forward");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (!KillAuraMod.CONFIG.autoSprint) return;

        if (mc.options.forwardKey.isPressed() && !mc.player.isUsingItem() && mc.player.getHungerManager() != null
                && mc.player.getHungerManager().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}
