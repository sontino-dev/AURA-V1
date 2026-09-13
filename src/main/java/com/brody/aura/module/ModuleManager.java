package com.brody.aura.module;

import com.brody.aura.KillAuraMod;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry + tick pump for modules.
 */
public class ModuleManager {

    private final Map<String, Module> modules = new HashMap<>();
    private final List<Module> ordered = new ArrayList<>();

    public void registerAll() {
        register(new KillAura());
        register(new HvhMode());
        register(new AutoSprint());
        register(new VelocityFix());
        register(new VulcanMode());
    }

    public void register(Module module) {
        modules.put(module.getName(), module);
        ordered.add(module);
    }

    public Module get(String name) {
        return modules.get(name);
    }

    public List<Module> all() {
        return ordered;
    }

    public void toggle(String name) {
        Module m = modules.get(name);
        if (m != null) {
            m.setEnabled(!m.isEnabled());
            if (KillAuraMod.CONFIG.debugLog) {
                KillAuraMod.LOGGER.info("[Brody] module {} -> {}", name, m.isEnabled() ? "ON" : "OFF");
            }
        }
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        for (Module m : ordered) {
            if (m.isEnabled()) {
                try {
                    m.onTick(client);
                } catch (Exception e) {
                    if (KillAuraMod.CONFIG.debugLog) {
                        KillAuraMod.LOGGER.error("[Brody] module {} crashed", m.getName(), e);
                    }
                }
            }
        }
    }
}
