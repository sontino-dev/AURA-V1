package com.brody.aura;

import com.brody.aura.config.AuraConfig;
import com.brody.aura.module.Module;
import com.brody.aura.module.ModuleManager;
import com.brody.aura.rotation.RotationManager;
import com.brody.aura.ai.AuraAI;
import com.brody.aura.anticheat.AnticheatManager;
import com.brody.aura.ui.BrodyHUD;
import com.brody.aura.ui.BrodyScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Main entrypoint. Wires up module manager, AI, rotation engine, HUD,
 * ClickGUI and the dynamic keybind system.
 *
 * Keybinds are no longer hardcoded Fabric KeyBindings: every module owns a
 * bind (default R/H/V) that can be REBOUND live from the ClickGUI
 * (Right Shift -> Combat tab -> click the [key] slot). Binds persist in the
 * JSON config and are edge-detected every client tick.
 */
public class KillAuraMod implements ClientModInitializer {

    public static final String MOD_ID = "brody-killaura";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final AuraConfig CONFIG = new AuraConfig();
    public static final ModuleManager MODULES = new ModuleManager();
    public static final RotationManager ROTATIONS = new RotationManager();
    public static final AuraAI AI = new AuraAI();
    public static final AnticheatManager ANTICHEAT = new AnticheatManager();

    // edge-detection state for the dynamic bind system
    private boolean guiBindHeld = false;
    private final Map<String, Boolean> bindHeld = new HashMap<>();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Brody] kill aura loading...");

        CONFIG.load();

        MODULES.registerAll();
        applyDefaultBinds();
        AI.loadBrain();

        // HUD overlay (watermark, module list, aura status)
        HudRenderCallback.EVENT.register(new BrodyHUD());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleBinds(client);
            MODULES.tick(client);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && client.player != null) {
                AI.tick();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            persistBinds();
            CONFIG.save();
            AI.saveBrain();
        }));

        LOGGER.info("[Brody] kill aura loaded, we locked in 😭✌️");
    }

    // ------------------------------------------------------------ keybinds

    /**
     * Default binds (R = KillAura, H = HvhMode, V = VulcanMode), overridden
     * by whatever the user rebound in the ClickGUI (persisted as "bind:<name>"
     * in the config extra map).
     */
    private void applyDefaultBinds() {
        Module ka = MODULES.get("KillAura");
        if (ka != null) ka.setKeyBind(GLFW.GLFW_KEY_R);
        Module hv = MODULES.get("HvhMode");
        if (hv != null) hv.setKeyBind(GLFW.GLFW_KEY_H);
        Module vu = MODULES.get("VulcanMode");
        if (vu != null) vu.setKeyBind(GLFW.GLFW_KEY_V);

        for (Module m : MODULES.all()) {
            double saved = CONFIG.getExtra("bind:" + m.getName(), -1000);
            if (saved != -1000) {
                m.setKeyBind((int) saved);
            }
        }
    }

    private void persistBinds() {
        for (Module m : MODULES.all()) {
            CONFIG.setExtra("bind:" + m.getName(), m.getKeyBind());
        }
    }

    /**
     * Edge-detected bind handling. Runs only while no screen is open so GUI
     * text input never triggers toggles. The GUI open key (config guiBind,
     * default Right Shift) opens the ClickGUI.
     */
    private void handleBinds(MinecraftClient client) {
        if (client.currentScreen != null) {
            guiBindHeld = false;
            return;
        }
        if (client.player == null) return;

        long handle = client.getWindow().getHandle();

        // GUI open bind
        int guiKey = CONFIG.guiBind;
        if (guiKey >= 0) {
            boolean pressed = InputUtil.isKeyPressed(handle, guiKey);
            if (pressed && !guiBindHeld) {
                client.setScreen(new BrodyScreen());
            }
            guiBindHeld = pressed;
        }

        // module binds
        for (Module m : MODULES.all()) {
            int bind = m.getKeyBind();
            if (bind < 0) {
                bindHeld.put(m.getName(), Boolean.FALSE);
                continue;
            }
            boolean pressed = InputUtil.isKeyPressed(handle, bind);
            if (pressed && !bindHeld.getOrDefault(m.getName(), Boolean.FALSE)) {
                MODULES.toggle(m.getName());
            }
            bindHeld.put(m.getName(), pressed);
        }
    }
}
