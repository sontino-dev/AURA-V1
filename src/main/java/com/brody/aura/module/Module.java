package com.brody.aura.module;

import net.minecraft.client.MinecraftClient;

/**
 * Base module. Every feature hangs off this.
 */
public abstract class Module {

    private final String name;
    private final String description;
    private boolean enabled;
    private int keyBind = -1;

    protected final MinecraftClient mc = MinecraftClient.getInstance();

    public Module(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Module(String name, String description, int keyBind) {
        this.name = name;
        this.description = description;
        this.keyBind = keyBind;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public abstract void onTick(MinecraftClient client);

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }
}
