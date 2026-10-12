package com.example.automace;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class AutoMaceScreen extends Screen {
    public AutoMaceScreen() {
        super(Component.literal("Auto Mace"));
    }

    @Override
    protected void init() {
        int w = 220, h = 20, gap = 24;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 112;

        Button header = Button.builder(Component.literal("Auto Mace  (Right Shift)"), b -> {
        }).bounds(x, y, w, h).build();
        header.active = false;
        this.addRenderableWidget(header);

        toggle(x, y + gap, w, h, "Enabled", () -> AutoMaceConfig.enabled, v -> AutoMaceConfig.enabled = v);
        toggle(x, y + gap * 2, w, h, "Players / bots", () -> AutoMaceConfig.targetPlayers, v -> AutoMaceConfig.targetPlayers = v);
        toggle(x, y + gap * 3, w, h, "Hostile mobs", () -> AutoMaceConfig.targetHostile, v -> AutoMaceConfig.targetHostile = v);
        toggle(x, y + gap * 4, w, h, "Animals (sheep etc.)", () -> AutoMaceConfig.targetAnimals, v -> AutoMaceConfig.targetAnimals = v);
        toggle(x, y + gap * 5, w, h, "Stun slam (axe, then mace)", () -> AutoMaceConfig.stunSlam, v -> AutoMaceConfig.stunSlam = v);
        toggle(x, y + gap * 6, w, h, "Only while falling", () -> AutoMaceConfig.requireFall, v -> AutoMaceConfig.requireFall = v);

        // Reach cycles 2.0 -> 2.5 -> 3.0 (3.0 is the vanilla attack reach)
        this.addRenderableWidget(Button.builder(reachLabel(), b -> {
            AutoMaceConfig.reach = AutoMaceConfig.reach >= 3.0 ? 2.0 : AutoMaceConfig.reach + 0.5;
            b.setMessage(reachLabel());
        }).bounds(x, y + gap * 7, w, h).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(x, y + gap * 8 + 6, w, h).build());
    }

    private void toggle(int x, int y, int w, int h, String name, BooleanSupplier get, Consumer<Boolean> set) {
        this.addRenderableWidget(Button.builder(label(name, get.getAsBoolean()), b -> {
            boolean now = !get.getAsBoolean();
            set.accept(now);
            b.setMessage(label(name, now));
        }).bounds(x, y, w, h).build());
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private static Component reachLabel() {
        return Component.literal("Reach: " + AutoMaceConfig.reach + " blocks");
    }

    @Override
    public void onClose() {
        AutoMaceConfig.save();
        super.onClose();
    }
}
