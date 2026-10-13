package com.example.automace;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public class AutoMaceMod implements ClientModInitializer {

    public static final String MOD_ID = "automace";

    private static final ResourceKey<Enchantment> BREACH =
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("minecraft", "breach"));
    private static final ResourceKey<Enchantment> DENSITY =
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("minecraft", "density"));

    public static KeyMapping screenKey;
    public static KeyMapping toggleKey;
    public static KeyMapping windBoostKey;

    private static boolean screenKeyWasDown = false;
    private static boolean toggleKeyWasDown = false;
    private static boolean windKeyWasDown = false;

    private static int ticksSinceLastAttack = 1000;
    private static int targetSeenTicks = 0;
    private static int lastTargetId = -1;
    private static boolean smashUsed = false;

    private static int pendingStunSlot = -1;
    private static int pendingReturnSlot = -1;
    private static Entity queuedTarget = null;

    private static int windCooldownTicks = 0;
    private static int noMaceTipCooldown = 0;

    @Override
    public void onInitializeClient() {
        AutoMaceConfig.load();

        screenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Auto Mace: Open Settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Auto Mace: Toggle On/Off",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC));
        windBoostKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Auto Mace: Wind Charge Boost",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC));

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (graphics, tickCounter) -> renderHud(graphics, Minecraft.getInstance()));

        ClientTickEvents.END_CLIENT_TICK.register(AutoMaceMod::tick);
    }

    // ------------------------------------------------------------ main tick

    private static void tick(Minecraft mc) {
        ticksSinceLastAttack++;
        if (windCooldownTicks > 0) {
            windCooldownTicks--;
        }
        if (noMaceTipCooldown > 0) {
            noMaceTipCooldown--;
        }

        handleScreenKey(mc);
        handleToggleKey(mc);

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            clearTargetingState();
            return;
        }

        LocalPlayer player = mc.player;
        if (player.onGround()) {
            smashUsed = false;
        }

        // Finish a pending stun slam (axe tick N, mace tick N+1) or swap back BEFORE anything else.
        if (pendingStunSlot >= 0) {
            player.getInventory().setSelectedSlot(pendingStunSlot);
            pendingStunSlot = -1;
            if (queuedTarget != null && queuedTarget.isAlive()) {
                mc.gameMode.attack(player, queuedTarget);
                player.swing(InteractionHand.MAIN_HAND);
            }
            queuedTarget = null;
        } else if (pendingReturnSlot >= 0) {
            player.getInventory().setSelectedSlot(pendingReturnSlot);
            pendingReturnSlot = -1;
        }

        if (pendingStunSlot >= 0 || pendingReturnSlot >= 0) {
            return;
        }

        if (mc.screen != null) {
            return;
        }

        boolean singleplayer = mc.getSingleplayerServer() != null;
        if (!AutoMaceConfig.enabled || !singleplayer) {
            return;
        }

        if (player.isSpectator()) {
            return;
        }

        handleWindBoost(mc, player);

        // Crosshair-only targeting; the entity must be what vanilla puts under the crosshair.
        if (!(mc.hitResult instanceof EntityHitResult hit)) {
            lastTargetId = -1;
            targetSeenTicks = 0;
            return;
        }
        Entity target = hit.getEntity();
        if (target == null || !target.isAlive() || target == player) {
            lastTargetId = -1;
            targetSeenTicks = 0;
            return;
        }

        // Vanilla reach, measured from the eyes to the target's hitbox.
        // While a spear is held, vanilla pick already applies the spear's attack range,
        // so we trust the crosshair instead of re-checking against the base range.
        boolean inRange = true;
        if (!isSpearHeld(player)) {
            double range = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            inRange = target.getBoundingBox().distanceToSqr(player.getEyePosition()) <= range * range;
        }
        boolean valid = inRange && isValidTarget(player, target);
        if (!valid) {
            lastTargetId = -1;
            targetSeenTicks = 0;
            return;
        }

        // Reaction delay only applies when a NEW target appears in the crosshair.
        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            targetSeenTicks = 0;
        }
        targetSeenTicks++;
        if (targetSeenTicks < AutoMaceConfig.reactionDelay) {
            return;
        }

        // Smash gating: real smash conditions (vanilla mace smash ignores attack cooldown, MC-274789).
        boolean smashReady = canSmashNow(player, AutoMaceConfig.minFall);
        if (AutoMaceConfig.smashOnly && !smashReady) {
            return;
        }
        if (!AutoMaceConfig.smashOnly && !smashReady
                && player.getAttackStrengthScale(0.5F) < 1.0F) {
            return;
        }

        if (ticksSinceLastAttack < AutoMaceConfig.hitSpacing) {
            return;
        }

        if (AutoMaceConfig.assistOnClick && !mc.options.keyAttack.isDown()) {
            return;
        }

        int maceSlot = findMaceSlot(player, target);
        if (maceSlot < 0) {
            if (AutoMaceConfig.noMaceTip && noMaceTipCooldown <= 0) {
                player.displayClientMessage(Component.literal("[AutoMace] No mace in your hotbar"), true);
                noMaceTipCooldown = 100;
            }
            return;
        }

        // Stun slam vs blockers: axe this tick, mace next tick, swap back the tick after.
        int axeSlot = -1;
        boolean stun = false;
        if (AutoMaceConfig.stunSlam && target instanceof LivingEntity living && living.isBlocking()) {
            axeSlot = findAxeSlot(player);
            stun = axeSlot >= 0;
        }

        int originalSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(stun ? axeSlot : maceSlot);
        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        ticksSinceLastAttack = 0;
        if (smashReady) {
            smashUsed = true;
        }

        if (stun) {
            pendingStunSlot = maceSlot;
            queuedTarget = target;
            pendingReturnSlot = originalSlot;
        } else {
            pendingReturnSlot = originalSlot;
        }
    }

    // ------------------------------------------------------------ keys

    private static void handleScreenKey(Minecraft mc) {
        boolean down = screenKey.isDown();
        if (down && !screenKeyWasDown && mc.screen == null) {
            mc.setScreen(new AutoMaceScreen());
        }
        screenKeyWasDown = down;
    }

    private static void handleToggleKey(Minecraft mc) {
        boolean down = toggleKey.isDown();
        if (down && !toggleKeyWasDown && mc.player != null) {
            boolean singleplayer = mc.getSingleplayerServer() != null;
            if (singleplayer) {
                AutoMaceConfig.enabled = !AutoMaceConfig.enabled;
                AutoMaceConfig.save();
            }
            String suffix = singleplayer ? "" : " (singleplayer only)";
            mc.player.displayClientMessage(
                    Component.literal("[AutoMace] " + (AutoMaceConfig.enabled ? "ON" : "OFF") + suffix), true);
        }
        toggleKeyWasDown = down;
    }

    private static void handleWindBoost(Minecraft mc, LocalPlayer player) {
        boolean down = windBoostKey.isDown();
        boolean wasDown = windKeyWasDown;
        windKeyWasDown = down;
        if (!down || wasDown) {
            return;
        }
        if (!AutoMaceConfig.windBoost || windCooldownTicks > 0) {
            return;
        }
        if (!player.getMainHandItem().is(Items.WIND_CHARGE)) {
            player.displayClientMessage(Component.literal("[AutoMace] Hold a wind charge to boost"), true);
            return;
        }
        if (player.getXRot() < 20.0F) {
            player.displayClientMessage(Component.literal("[AutoMace] Aim down at your feet to boost"), true);
            return;
        }
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        windCooldownTicks = 10;
    }

    // ------------------------------------------------------------ helpers

    private static boolean canSmashNow(LocalPlayer player, double minFall) {
        return !player.onGround()
                && player.fallDistance > minFall
                && !player.isFallFlying()
                && player.getDeltaMovement().y < 0.0;
    }

    private static boolean isValidTarget(LocalPlayer self, Entity target) {
        if (!target.isAlive() || target.isSpectator() || target == self) {
            return false;
        }
        if (target instanceof Player candidate) {
            if (candidate.isCreative()) {
                return false;
            }
            if (!AutoMaceConfig.targetPlayers) {
                return false;
            }
            return !isIgnored(candidate.getGameProfile().getName());
        }
        if (AutoMaceConfig.targetHostiles && target instanceof Enemy) {
            return true;
        }
        if (AutoMaceConfig.targetAnimals && target instanceof Animal) {
            if (target instanceof TamableAnimal tame && tame.isTame()) {
                return false; // never tamed pets
            }
            return true;
        }
        return false;
    }

    private static boolean isIgnored(String name) {
        String lowered = name.toLowerCase();
        for (String entry : AutoMaceConfig.ignoreList) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty() && lowered.equals(trimmed.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSpearHeld(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            return false;
        }
        return BuiltInRegistries.ITEM.getKey(main.getItem()).getPath().endsWith("_spear");
    }

    private static int findMaceSlot(LocalPlayer player, Entity target) {
        Inventory inventory = player.getInventory();
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        // Simple armor heuristic: players and monsters can wear armor, so Breach matters most there.
        boolean armoredTarget = target instanceof Player || target instanceof Enemy;
        boolean bigFall = player.fallDistance > 4.0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(Items.MACE)) {
                continue;
            }
            int score = 0;
            if (AutoMaceConfig.smartWeaponPick) {
                int breach = 0;
                int density = 0;
                for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.is(BREACH)) {
                        breach = entry.getIntValue();
                    } else if (holder.is(DENSITY)) {
                        density = entry.getIntValue();
                    }
                }
                if (armoredTarget) {
                    score += breach * 3;
                }
                if (bigFall) {
                    score += density * 2;
                }
            }
            if (bestSlot < 0 || score > bestScore) {
                bestSlot = slot;
                bestScore = score;
            }
        }
        return bestSlot;
    }

    private static int findAxeSlot(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ItemTags.AXES)) {
                return slot;
            }
        }
        return -1;
    }

    private static void clearTargetingState() {
        lastTargetId = -1;
        targetSeenTicks = 0;
        smashUsed = false;
    }

    // ------------------------------------------------------------ HUD

    private static void renderHUD(GuiGraphics graphics, Minecraft mc) {
        if (!AutoMaceConfig.hud) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            return;
        }
        boolean on = AutoMaceConfig.enabled;
        String mode = AutoMaceConfig.assistOnClick ? "assist" : "auto";
        String smashState;
        if (!on) {
            smashState = "";
        } else if (canSmashNow(mc.player, AutoMaceConfig.minFall) && !smashUsed) {
            smashState = "smash ready";
        } else if (canSmashNow(mc.player, AutoMaceConfig.minFall)) {
            smashState = "smash used";
        } else {
            smashState = "no smash";
        }
        int color = on ? 0xFF6FD67F : 0xFF9AA0A6;
        graphics.drawString(mc.font, "AutoMace: " + (on ? "ON" : "OFF"), 5, 5, color);
        if (!smashState.isEmpty()) {
            graphics.drawString(mc.font, "mode " + mode + "  " + smashState, 5, 15, 0xFFB6BEC6);
        }
    }
}
