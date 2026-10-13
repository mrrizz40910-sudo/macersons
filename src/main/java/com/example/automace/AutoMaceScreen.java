package com.example.automace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Dark sidebar + card style settings screen. */
public class AutoMaceScreen extends Screen {
    private static final int PANEL = 0xFF1B1E26, SIDEBAR = 0xFF15181F, BORDER = 0xFF2A2E39;
    private static final int CARD = 0xFF272B35, CARD_HOVER = 0xFF2F3440;
    private static final int ACCENT = 0xFF2EE59D, ACCENT_BG = 0xFF1D3A33;
    private static final int TEXT = 0xFFE8EAF0, SUB = 0xFF8A90A0, OFF = 0xFF3A3F4C, WHITE = 0xFFFFFFFF;
    private static final int W = 400, H = 240, SIDE = 104;

    private enum Tab {
        COMBAT("Combat"), TARGETS("Targets"), TIMING("Timing");
        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static Tab tab = Tab.COMBAT;
    private int left, top;

    public AutoMaceScreen() {
        super(Component.literal("Auto Mace"));
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        build();
    }

    private void build() {
        int tx = left + 12, ty = top + 62;
        for (Tab t : Tab.values()) {
            this.addRenderableWidget(new TabButton(tx, ty, SIDE - 24, 22, t));
            ty += 26;
        }
        this.addRenderableWidget(new FlatButton(left + 12, top + H - 60, SIDE - 24, 22, "Reset", SUB, () -> {
            resetDefaults();
            this.clearWidgets();
            this.build();
        }));
        this.addRenderableWidget(new FlatButton(left + 12, top + H - 34, SIDE - 24, 22, "Close", 0xFFFF6B6B, this::onClose));

        int x = left + SIDE + 12, y = top + 46, w = W - SIDE - 24, step = 32;
        switch (tab) {
            case COMBAT -> {
                toggle(x, y, w, "Enabled", "Master switch", () -> AutoMaceConfig.enabled, v -> AutoMaceConfig.enabled = v);
                toggle(x, y + step, w, "Smash only", "Only hit when falling (real mace smash)", () -> AutoMaceConfig.smashOnly, v -> AutoMaceConfig.smashOnly = v);
                toggle(x, y + step * 2, w, "Stun slam", "Axe a raised shield, then mace next tick", () -> AutoMaceConfig.stunSlam, v -> AutoMaceConfig.stunSlam = v);
                cycle(x, y + step * 3, w, "Min fall height", "Vanilla smash starts at 1.5 blocks",
                        () -> AutoMaceConfig.minFall + " blocks",
                        () -> AutoMaceConfig.minFall = next(new double[]{1.5, 2.5, 3.5, 5.0, 8.0}, AutoMaceConfig.minFall));
            }
            case TARGETS -> {
                toggle(x, y, w, "Players / bots", "Other players and fake players", () -> AutoMaceConfig.targetPlayers, v -> AutoMaceConfig.targetPlayers = v);
                toggle(x, y + step, w, "Hostile mobs", "Zombies, skeletons, creepers...", () -> AutoMaceConfig.targetHostile, v -> AutoMaceConfig.targetHostile = v);
                toggle(x, y + step * 2, w, "Animals", "Sheep, cows, pigs... (never your pets)", () -> AutoMaceConfig.targetAnimals, v -> AutoMaceConfig.targetAnimals = v);
            }
            case TIMING -> {
                cycle(x, y, w, "Reaction delay", "Wait after your crosshair lands on a target",
                        () -> AutoMaceConfig.reactionTicks + " ticks",
                        () -> AutoMaceConfig.reactionTicks = (int) next(new double[]{0, 1, 2, 3, 4}, AutoMaceConfig.reactionTicks));
                cycle(x, y + step, w, "Hit spacing", "Minimum time between auto attacks",
                        () -> AutoMaceConfig.hitSpacing + " ticks",
                        () -> AutoMaceConfig.hitSpacing = (int) next(new double[]{4, 6, 8, 10, 15}, AutoMaceConfig.hitSpacing));
            }
        }
    }

    private static void resetDefaults() {
        AutoMaceConfig.enabled = true;
        AutoMaceConfig.smashOnly = true;
        AutoMaceConfig.stunSlam = true;
        AutoMaceConfig.minFall = 1.5;
        AutoMaceConfig.targetPlayers = true;
        AutoMaceConfig.targetHostile = true;
        AutoMaceConfig.targetAnimals = true;
        AutoMaceConfig.reactionTicks = 0;
        AutoMaceConfig.hitSpacing = 6;
    }

    private static double next(double[] values, double current) {
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 1e-6) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private void toggle(int x, int y, int w, String label, String sub, BooleanSupplier get, Consumer<Boolean> set) {
        this.addRenderableWidget(new SwitchRow(x, y, w, label, sub, get, set));
    }

    private void cycle(int x, int y, int w, String label, String sub, Supplier<String> value, Runnable step) {
        this.addRenderableWidget(new CycleRow(x, y, w, label, sub, value, step));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;

        rrect(g, left - 1, top - 1, W + 2, H + 2, BORDER);
        rrect(g, left, top, W, H, PANEL);
        g.fill(left + 2, top + 2, left + SIDE, top + H - 2, SIDEBAR);

        g.drawString(font, "Auto Mace", left + 14, top + 16, ACCENT);
        g.drawString(font, AutoMaceConfig.enabled ? "ON" : "OFF", left + SIDE - 26, top + 16,
                AutoMaceConfig.enabled ? ACCENT : 0xFFFF6B6B);
        g.drawString(font, "singleplayer", left + 14, top + 28, SUB);
        g.drawString(font, "MODULES", left + 14, top + 50, SUB);

        g.drawString(font, tab.label, left + SIDE + 14, top + 16, TEXT);
        g.drawString(font, "Changes save when you close this menu", left + SIDE + 14, top + 28, SUB);

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        AutoMaceConfig.save();
        super.onClose();
    }

    /** Small rounded rectangle made from three overlapping fills. */
    private static void rrect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    private class TabButton extends Button {
        private final Tab t;

        TabButton(int x, int y, int w, int h, Tab t) {
            super(x, y, w, h, Component.literal(t.label), b -> {
                tab = t;
                AutoMaceScreen.this.clearWidgets();
                AutoMaceScreen.this.build();
            }, Button.DEFAULT_NARRATION);
            this.t = t;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            Font font = Minecraft.getInstance().font;
            boolean selected = tab == t;
            if (selected) {
                rrect(g, getX(), getY(), width, height, ACCENT_BG);
                g.fill(getX(), getY() + 4, getX() + 2, getY() + height - 4, ACCENT);
            } else if (isHovered()) {
                rrect(g, getX(), getY(), width, height, CARD);
            }
            g.drawString(font, t.label, getX() + 10, getY() + (height - 8) / 2, selected ? ACCENT : TEXT);
        }
    }

    private static class FlatButton extends Button {
        private final String label;
        private final int color;

        FlatButton(int x, int y, int w, int h, String label, int color, Runnable action) {
            super(x, y, w, h, Component.literal(label), b -> action.run(), Button.DEFAULT_NARRATION);
            this.label = label;
            this.color = color;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            Font font = Minecraft.getInstance().font;
            rrect(g, getX(), getY(), width, height, isHovered() ? CARD_HOVER : CARD);
            int tw = font.width(label);
            g.drawString(font, label, getX() + (width - tw) / 2, getY() + (height - 8) / 2, color);
        }
    }

    private static class SwitchRow extends Button {
        private final String label, sub;
        private final BooleanSupplier get;

        SwitchRow(int x, int y, int w, String label, String sub, BooleanSupplier get, Consumer<Boolean> set) {
            super(x, y, w, 28, Component.literal(label), b -> set.accept(!get.getAsBoolean()), Button.DEFAULT_NARRATION);
            this.label = label;
            this.sub = sub;
            this.get = get;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            Font font = Minecraft.getInstance().font;
            rrect(g, getX(), getY(), width, height, isHovered() ? CARD_HOVER : CARD);
            g.drawString(font, label, getX() + 10, getY() + 5, TEXT);
            g.drawString(font, sub, getX() + 10, getY() + 16, SUB);

            boolean on = get.getAsBoolean();
            int sx = getX() + width - 38, sy = getY() + 8;
            rrect(g, sx, sy, 28, 12, on ? ACCENT : OFF);
            rrect(g, on ? sx + 18 : sx + 2, sy + 2, 8, 8, WHITE);
        }
    }

    private static class CycleRow extends Button {
        private final String label, sub;
        private final Supplier<String> value;

        CycleRow(int x, int y, int w, String label, String sub, Supplier<String> value, Runnable step) {
            super(x, y, w, 28, Component.literal(label), b -> step.run(), Button.DEFAULT_NARRATION);
            this.label = label;
            this.sub = sub;
            this.value = value;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            Font font = Minecraft.getInstance().font;
            rrect(g, getX(), getY(), width, height, isHovered() ? CARD_HOVER : CARD);
            g.drawString(font, label, getX() + 10, getY() + 5, TEXT);
            g.drawString(font, sub, getX() + 10, getY() + 16, SUB);

            String v = "< " + value.get() + " >";
            g.drawString(font, v, getX() + width - 10 - font.width(v), getY() + 10, ACCENT);
        }
    }
}
