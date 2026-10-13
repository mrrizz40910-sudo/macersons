package com.example.automace;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutoMaceScreen extends Screen {

    private static final int COL_PANEL = 0xF0101216;
    private static final int COL_SIDEBAR = 0xFF16181E;
    private static final int COL_CARD = 0xFF1D2026;
    private static final int COL_CARD_HOVER = 0xFF262B36;
    private static final int COL_TEXT = 0xFFE8ECF1;
    private static final int COL_TEXT_DIM = 0xFF8A929C;
    private static final int COL_GREEN = 0xFF4ADE80;
    private static final int COL_GREEN_DARK = 0xFF1F4D33;
    private static final int COL_SPLIT = 0xFF2A2E38;

    private static final String[] TABS = {"Combat", "Targets", "Timing", "Interface"};

    private int tab = 0;
    private EditBox ignoreBox;
    private String lastIgnoreText = "";

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int sideW;
    private int contentX;
    private int rowW;

    public AutoMaceScreen() {
        super(Component.literal("Auto Mace"));
    }

    @Override
    protected void init() {
        panelW = Math.min(this.width - 24, 364);
        panelH = Math.min(this.height - 24, 224);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        sideW = 84;
        contentX = panelX + sideW + 10;
        rowW = panelW - sideW - 24;
        int rowY = panelY + 38;

        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            this.addRenderableWidget(new OrchardButton(
                    panelX + 8, panelY + 34 + i * 26, sideW - 16, 20, TABS[i],
                    b -> {
                        this.tab = index;
                        this.rebuild();
                    },
                    i == this.tab));
        }

        this.addRenderableWidget(new OrchardButton(
                contentX, panelY + panelH - 26, 60, 18, "Reset",
                b -> {
                    AutoMaceConfig.resetDefaults();
                    AutoMaceConfig.save();
                    this.rebuild();
                },
                false));
        this.addRenderableWidget(new OrchardButton(
                panelX + panelW - 70, panelY + panelH - 26, 60, 18, "Close",
                b -> this.onClose(),
                false));

        int rowH = 24;
        switch (this.tab) {
            case 0 -> { // Combat
                addSwitch(contentX, rowY + 0 * rowH, rowW, "Assist on click",
                        "Only act when you press attack",
                        () -> AutoMaceConfig.assistOnClick, v -> AutoMaceConfig.assistOnClick = v);
                addSwitch(contentX, rowY + 1 * rowH, rowW, "Smash only",
                        "Fire only on real mace smashes",
                        () -> AutoMaceConfig.smashOnly, v -> AutoMaceConfig.smashOnly = v);
                addSwitch(contentX, rowY + 2 * rowH, rowW, "Stun slam",
                        "Axe first against shield blockers",
                        () -> AutoMaceConfig.stunSlam, v -> AutoMaceConfig.stunSlam = v);
                addSwitch(contentX, rowY + 3 * rowH, rowW, "Smart weapon pick",
                        "Breach vs armored, Density on big falls",
                        () -> AutoMaceConfig.smartWeaponPick, v -> AutoMaceConfig.smartWeaponPick = v);
                addSwitch(contentX, rowY + 4 * rowH, rowW, "Spear reach targeting",
                        "Trust vanilla spear range",
                        () -> AutoMaceConfig.spearReach, v -> AutoMaceConfig.spearReach = v);
                addSwitch(contentX, rowY + 5 * rowH, rowW, "Wind charge boost key",
                        "Rebind in Controls",
                        () -> AutoMaceConfig.windBoost, v -> AutoMaceConfig.windBoost = v);
            }
            case 1 -> { // Targets
                addSwitch(contentX, rowY + 0 * rowH, rowW, "Players & bots",
                        "Not creative players",
                        () -> AutoMaceConfig.targetPlayers, v -> AutoMaceConfig.targetPlayers = v);
                addSwitch(contentX, rowY + 1 * rowH, rowW, "Hostile mobs",
                        "Enemy mobs",
                        () -> AutoMaceConfig.targetHostiles, v -> AutoMaceConfig.targetHostiles = v);
                addSwitch(contentX, rowY + 2 * rowH, rowW, "Animals",
                        "Never tamed pets",
                        () -> AutoMaceConfig.targetAnimals, v -> AutoMaceConfig.targetAnimals = v);
                buildIgnoreList(rowY + 3 * rowH + 6);
            }
            case 2 -> { // Timing
                addNumberBar(contentX, rowY + 0 * rowH, rowW, "Min fall height",
                        () -> String.format(Locale.ROOT, "%.1f blocks", AutoMaceConfig.minFall),
                        v -> AutoMaceConfig.minFall = AutoMaceConfig.clampDouble(v, 0.5, 20.0),
                        0.5, 20.0, 0.5);
                addNumberBar(contentX, rowY + 1 * rowH, rowW, "Reaction delay",
                        () -> AutoMaceConfig.reactionDelay + " ticks",
                        v -> AutoMaceConfig.reactionDelay = (int) AutoMaceConfig.clampDouble(v, 0.0, 20.0),
                        0.0, 20.0, 1.0);
                addNumberBar(contentX, rowY + 2 * rowH, rowW, "Hit spacing",
                        () -> AutoMaceConfig.hitSpacing + " ticks",
                        v -> AutoMaceConfig.hitSpacing = (int) AutoMaceConfig.clampDouble(v, 1.0, 40.0),
                        1.0, 40.0, 1.0);
                addDescription(contentX, rowY + 3 * rowH, rowW,
                        "Lower reaction delay = faster reaction");
            }
            default -> { // Interface
                addSwitch(contentX, rowY + 0 * rowH, rowW, "HUD indicator",
                        "Status text top-left",
                        () -> AutoMaceConfig.hud, v -> AutoMaceConfig.hud = v);
                addSwitch(contentX, rowY + 1 * rowH, rowW, "No-mace tip",
                        "Action bar hint when no mace in hotbar",
                        () -> AutoMaceConfig.noMaceTip, v -> AutoMaceConfig.noMaceTip = v);
                addDescription(contentX, rowY + 3 * rowH, rowW,
                        "Keys: Options > Controls > Auto Mace");
            }
        }
    }

    // ------------------------------------------------------------ ignore list

    private void buildIgnoreList(int startY) {
        int boxY = startY;
        this.ignoreBox = new EditBox(Minecraft.getInstance().font, contentX, boxY, rowW - 68, 18,
                Component.literal("Name"));
        this.ignoreBox.setMaxLength(32);
        this.ignoreBox.setValue(lastIgnoreText);
        this.addRenderableWidget(this.ignoreBox);
        this.addRenderableWidget(new OrchardButton(
                contentX + rowW - 64, boxY, 64, 18, "Add",
                b -> addIgnore(),
                false));

        int entryY = boxY + 22;
        int maxEntries = Math.min(AutoMaceConfig.ignoreList.size(), 5);
        int shown = 0;
        for (int i = 0; i < maxEntries; i++) {
            if (entryY + 16 > panelY + panelH - 30) {
                break;
            }
            final String name = AutoMaceConfig.ignoreList.get(i);
            this.addRenderableWidget(new OrchardButton(
                    contentX, entryY, rowW, 16, "[x] " + name,
                    b -> {
                        AutoMaceConfig.ignoreList.remove(name);
                        AutoMaceConfig.save();
                        this.rebuild();
                    },
                    false));
            entryY += 18;
            shown++;
        }
        int remaining = AutoMaceConfig.ignoreList.size() - shown;
        if (remaining > 0 && entryY + 10 <= panelY + panelH - 30) {
            addDescription(contentX, entryY, rowW, "...and " + remaining + " more (Reset clears all)");
        }
    }

    private void addIgnore() {
        String name = this.ignoreBox != null ? this.ignoreBox.getValue().trim() : "";
        if (!name.isEmpty()) {
            boolean exists = false;
            for (String entry : AutoMaceConfig.ignoreList) {
                if (entry.equalsIgnoreCase(name)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                AutoMaceConfig.ignoreList.add(name);
                AutoMaceConfig.save();
            }
        }
        lastIgnoreText = "";
        this.rebuild();
    }

    // ------------------------------------------------------------ row builders

    private void addSwitch(int x, int y, int w, String title, String subtitle,
                           BooleanSupplier getter, Consumer<Boolean> setter) {
        this.addRenderableWidget(new SwitchCard(x, y, w, title, subtitle, getter, setter));
    }

    private void addNumberBar(int x, int y, int w, String title,
                              Supplier<String> value, Consumer<Double> adjust,
                              double min, double max, double step) {
        this.addRenderableWidget(new ValueCard(x, y, w - 42, title, value));
        this.addRenderableWidget(new OrchardButton(
                x + w - 38, y + 1, 18, 18, "-",
                b -> {
                    adjust.accept(numberFrom(value) - step);
                },
                false));
        this.addRenderableWidget(new OrchardButton(
                x + w - 19, y + 1, 19, 18, "+",
                b -> {
                    adjust.accept(numberFrom(value) + step);
                },
                false));
    }

    private double numberFrom(Supplier<String> value) {
        String text = value.get();
        int space = text.indexOf(' ');
        String number = space > 0 ? text.substring(0, space) : text;
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void addDescription(int x, int y, int w, String text) {
        this.addRenderableWidget(new ValueCard(x, y, w, text, null));
    }

    // ------------------------------------------------------------ rebuild + close + render

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Close with the same key that opened the screen (Right Shift by default),
        // including when the user rebinds it later.
        try {
            if (AutoMaceMod.screenKey != null
                    && event.key() == AutoMaceMod.screenKey.getDefaultKey().getValue()) {
                this.onClose();
                return true;
            }
        } catch (Throwable ignored) {
            // Fall back to ESC below if the accessor differs.
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // NOTE: no renderBackground() call here. Since 1.21.9 the screen background
        // (blur) is rendered automatically before Screen.render(), and calling it
        // again crashes with "Can only blur once per frame".

        // Dark rounded panel (layered fills fake the rounded corners).
        g.fill(panelX + 2, panelY, panelX + panelW - 2, panelY + panelH, COL_PANEL);
        g.fill(panelX, panelY + 2, panelX + panelW, panelY + panelH - 2, COL_PANEL);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, COL_PANEL);

        // Sidebar strip.
        g.fill(panelX + 4, panelY + 4, panelX + sideW, panelY + panelH - 4, COL_SIDEBAR);

        // Title + ON/OFF indicator.
        Font font = Minecraft.getInstance().font;
        g.drawString(font, "Auto Mace", panelX + 12, panelY + 14, COL_TEXT);
        boolean on = AutoMaceConfig.enabled;
        String state = on ? "ON" : "OFF";
        int stateColor = on ? COL_GREEN : COL_TEXT_DIM;
        int stateW = font.width(state);
        g.drawString(font, state, panelX + panelW - stateW - 10, panelY + 14, stateColor);

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------ Orchard widgets

    /** Rounded dark button used for tabs, +/-, Add, Reset, Close and ignore-list rows. */
    private class OrchardButton extends Button {

        private final String label;
        private final boolean selected;

        OrchardButton(int x, int y, int w, int h, String label, Button.OnPress onPress, boolean selected) {
            super(x, y, w, h, Component.literal(label), onPress, b -> b.getMessage());
            this.label = label;
            this.selected = selected;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = isHovered() ? COL_CARD_HOVER : (selected ? COL_GREEN_DARK : COL_CARD);
            g.fill(getX(), getY(), getX() + this.width, getY() + this.height, bg);
            g.fill(getX(), getY() + this.height - 2, getX() + this.width, getY() + this.height,
                    selected ? COL_GREEN : COL_SPLIT);
            int textW = Minecraft.getInstance().font.width(this.label);
            int textX = getX() + (this.width - textW) / 2;
            if (textX < getX() + 3) {
                textX = getX() + 3;
            }
            int color = this.selected ? COL_GREEN : (isHovered() ? COL_TEXT : COL_TEXT_DIM);
            g.drawString(Minecraft.getInstance().font, this.label, textX, getY() + (this.height - 8) / 2, color);
        }
    }

    /** A card row with title, subtitle, and a green toggle switch. Clicking anywhere toggles. */
    private class SwitchCard extends Button {

        private final String title;
        private final String subtitle;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        SwitchCard(int x, int y, int w, String title, String subtitle,
                   BooleanSupplier getter, Consumer<Boolean> setter) {
            super(x, y, w, 20, Component.literal(title), b -> { }, b -> b.getMessage());
            this.title = title;
            this.subtitle = subtitle;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void onPress() {
            this.setter.accept(!this.getter.getAsBoolean());
            AutoMaceConfig.save();
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean value = this.getter.getAsBoolean();
            int bg = isHovered() ? COL_CARD_HOVER : COL_CARD;
            g.fill(getX(), getY(), getX() + this.width, getY() + this.height, bg);
            g.drawString(Minecraft.getInstance().font, this.title, getX() + 8, getY() + 3,
                    value ? COL_TEXT : COL_TEXT_DIM);
            if (!this.subtitle.isEmpty()) {
                int subY = getY() + 12;
                if (subY + 8 <= getY() + this.height) {
                    g.drawString(Minecraft.getInstance().font, this.subtitle, getX() + 8, subY, COL_TEXT_DIM);
                }
            }
            // Toggle switch (right side): track + sliding knob.
            int trackW = 26;
            int trackH = 12;
            int trackX = getX() + this.width - trackW - 8;
            int trackY = getY() + (this.height - trackH) / 2;
            g.fill(trackX, trackY, trackX + trackW, trackY + trackH,
                    value ? COL_GREEN : COL_SPLIT);
            int knobW = 8;
            int knobH = trackH - 4;
            int knobX = value ? trackX + trackW - knobW - 2 : trackX + 2;
            int knobY = trackY + 2;
            g.fill(knobX, knobY, knobX + knobW, knobY + knobH, 0xFFF2F5F7);
            String tag = value ? "ON" : "OFF";
            int tagColor = value ? COL_GREEN : COL_TEXT_DIM;
            int tagW = Minecraft.getInstance().font.width(tag);
            g.drawString(Minecraft.getInstance().font, tag, trackX - tagW - 6, trackY, tagColor);
        }
    }

    /** Read-only card showing a live value (numbers, descriptions). */
    private class ValueCard extends Button {

        private final String title;
        private final Supplier<String> value;

        ValueCard(int x, int y, int w, String title, Supplier<String> value) {
            super(x, y, w, 20, Component.literal(title), b -> { }, b -> b.getMessage());
            this.title = title;
            this.value = value;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = isHovered() ? COL_CARD_HOVER : COL_CARD;
            g.fill(getX(), getY(), getX() + this.width, getY() + this.height, bg);
            g.drawString(Minecraft.getInstance().font, this.title, getX() + 8, getY() + 3, COL_TEXT);
            if (this.value != null) {
                String text = this.value.get();
                int textW = Minecraft.getInstance().font.width(text);
                g.drawString(Minecraft.getInstance().font, text,
                        getX() + this.width - textW - 8, getY() + 3, COL_TEXT_DIM);
            }
        }
    }
}
