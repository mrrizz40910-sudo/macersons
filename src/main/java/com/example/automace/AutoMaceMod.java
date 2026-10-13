package com.example.automace;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Singleplayer auto mace, crosshair based.
 *
 * It only ever acts on the entity your CROSSHAIR is on (vanilla targeting, vanilla line of sight),
 * and the target must also be inside the mace's normal reach. It fires at the moment a real smash
 * is possible (falling past the minimum fall height). The smash bonus damage is not reduced by
 * attack cooldown, so it does not wait for a charge. Stun slam: axe on one tick, mace on the next.
 */
public class AutoMaceMod implements ClientModInitializer {
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleKey;

    private int returnSlot = -1;      // slot to go back to
    private boolean followUp = false; // axe just landed -> mace on the very next tick
    private int cooldown = 0;
    private int reaction = 0;
    private int lastTargetId = -1;
    private boolean smashUsed = false; // one smash per fall

    @Override
    public void onInitializeClient() {
        AutoMaceConfig.load();
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.automace.menu",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.automace.toggle",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            returnSlot = -1;
            followUp = false;
            return;
        }

        while (openMenuKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new AutoMaceScreen());
        }
        while (toggleKey.consumeClick()) {
            AutoMaceConfig.enabled = !AutoMaceConfig.enabled;
            AutoMaceConfig.save();
            p.displayClientMessage(Component.literal("Auto Mace: " + (AutoMaceConfig.enabled ? "ON" : "OFF")), true);
        }

        if (cooldown > 0) cooldown--;
        if (p.onGround()) smashUsed = false;

        // Stun slam follow-up / switch back
        if (returnSlot != -1) {
            if (followUp) {
                followUp = false;
                LivingEntity t = crosshairTarget(mc);
                int mace = findHotbar(p, false);
                if (t != null && mace != -1 && inMaceReach(p, t)) {
                    p.getInventory().setSelectedSlot(mace);
                    attack(mc, p, t);
                    smashUsed = true;
                    cooldown = AutoMaceConfig.hitSpacing;
                    return;
                }
            }
            p.getInventory().setSelectedSlot(returnSlot);
            returnSlot = -1;
            return;
        }

        // Singleplayer only. Does nothing on multiplayer servers.
        if (mc.getSingleplayerServer() == null) return;
        if (!AutoMaceConfig.enabled || mc.screen != null || cooldown > 0) return;
        if (!p.isAlive() || p.isSpectator() || p.isUsingItem()) return;

        LivingEntity target = crosshairTarget(mc);
        if (target == null || !inMaceReach(p, target)) {
            reaction = 0;
            lastTargetId = -1;
            return;
        }

        boolean smash = p.fallDistance > AutoMaceConfig.minFall
                && !p.isFallFlying() && !p.onGround()
                && p.getDeltaMovement().y < 0.0 && !smashUsed;
        if (AutoMaceConfig.smashOnly && !smash) {
            reaction = 0;
            return;
        }

        // Small reaction delay, counted from when the crosshair landed on this target
        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            reaction = 0;
        }
        if (reaction < AutoMaceConfig.reactionTicks) {
            reaction++;
            return;
        }

        Inventory inv = p.getInventory();
        int original = inv.getSelectedSlot();

        // Stun slam: raised shield -> axe now, mace on the next tick (no switch back in between)
        if (AutoMaceConfig.stunSlam && target.isBlocking()) {
            int axe = findHotbar(p, true);
            if (axe != -1) {
                if (axe != original) inv.setSelectedSlot(axe);
                attack(mc, p, target);
                returnSlot = original;
                followUp = true;
                return;
            }
        }

        int mace = findHotbar(p, false);
        if (mace == -1) return;
        boolean swapped = mace != original;
        if (swapped) inv.setSelectedSlot(mace);

        // Without a smash a normal hit needs a full charge; a smash does not (bonus ignores cooldown)
        if (!smash && p.getAttackStrengthScale(0.5F) < 1.0F) {
            if (swapped) inv.setSelectedSlot(original);
            return;
        }

        attack(mc, p, target);
        if (smash) smashUsed = true;
        if (swapped) returnSlot = original;
        cooldown = AutoMaceConfig.hitSpacing;
        reaction = 0;
    }

    private void attack(Minecraft mc, LocalPlayer p, LivingEntity target) {
        mc.gameMode.attack(p, target); // syncs the held slot to the server, then attacks
        p.swing(InteractionHand.MAIN_HAND);
    }

    /** The living entity under the crosshair (vanilla pick), if it is a target we want. */
    private LivingEntity crosshairTarget(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity le
                && le.isAlive() && wanted(le)) {
            return le;
        }
        return null;
    }

    /** The mace has no special reach, so use the player's normal interaction range, to the hitbox. */
    private boolean inMaceReach(LocalPlayer p, LivingEntity t) {
        double r = p.entityInteractionRange();
        return distSqToBox(p.getEyePosition(), t.getBoundingBox()) <= r * r;
    }

    private boolean wanted(LivingEntity e) {
        if (e instanceof Player other) return AutoMaceConfig.targetPlayers && !other.isCreative();
        if (e instanceof TamableAnimal pet && pet.isTame()) return false; // never your own pets
        if (e instanceof Enemy) return AutoMaceConfig.targetHostile;
        if (e instanceof Animal) return AutoMaceConfig.targetAnimals;
        return false;
    }

    private static double distSqToBox(Vec3 v, AABB b) {
        double dx = Math.max(Math.max(b.minX - v.x, 0.0), v.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - v.y, 0.0), v.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - v.z, 0.0), v.z - b.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private int findHotbar(LocalPlayer p, boolean axe) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (axe ? s.is(ItemTags.AXES) : s.is(Items.MACE)) return i;
        }
        return -1;
    }
}
