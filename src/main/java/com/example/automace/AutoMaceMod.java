package com.example.automace;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Singleplayer auto mace.
 *
 * Every client tick: find the best target inside reach, "probe" a swap to the mace
 * (or axe for a shield stun), and only attack if that weapon is at FULL charge.
 * If it is not ready the swap is undone in the same tick, so the server never sees it.
 * After a hit the original slot is restored on the very next tick.
 */
public class AutoMaceMod implements ClientModInitializer {
    private boolean shiftWasDown = false;
    private int returnSlot = -1;

    @Override
    public void onInitializeClient() {
        AutoMaceConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            returnSlot = -1;
            return;
        }

        // Right Shift opens the config GUI
        long window = GLFW.glfwGetCurrentContext();
        boolean down = window != 0 && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (down && !shiftWasDown && mc.screen == null) {
            mc.setScreen(new AutoMaceScreen());
        }
        shiftWasDown = down;

        // Switch back to the item you were holding
        if (returnSlot != -1) {
            p.getInventory().setSelectedSlot(returnSlot);
            returnSlot = -1;
            return;
        }

        // Singleplayer only. Does nothing on multiplayer servers.
        if (mc.getSingleplayerServer() == null) return;
        if (!AutoMaceConfig.enabled || mc.screen != null) return;
        if (!p.isAlive() || p.isSpectator() || p.isUsingItem()) return;

        LivingEntity target = findTarget(mc, p);
        if (target == null) return;

        if (AutoMaceConfig.requireFall && p.fallDistance <= 1.5) return;

        int original = p.getInventory().getSelectedSlot();

        // Stun slam: raised shield -> axe it first. The mace follows once it is charged.
        if (AutoMaceConfig.stunSlam && target.isBlocking()) {
            int axe = findHotbar(p, true);
            if (axe != -1) {
                tryHit(mc, p, target, axe, original);
                return; // never waste a mace hit into a raised shield
            }
        }

        int mace = findHotbar(p, false);
        if (mace != -1) tryHit(mc, p, target, mace, original);
    }

    /** Swaps to the slot, attacks only if fully charged, otherwise undoes the swap immediately. */
    private boolean tryHit(Minecraft mc, LocalPlayer p, LivingEntity target, int slot, int original) {
        Inventory inv = p.getInventory();
        boolean swapped = slot != original;
        if (swapped) inv.setSelectedSlot(slot);

        // Charge is measured against the NEW item's attack speed (what the server will use).
        if (p.getAttackStrengthScale(0.5F) < 1.0F) {
            if (swapped) inv.setSelectedSlot(original);
            return false;
        }

        mc.gameMode.attack(p, target); // syncs the slot to the server, then attacks
        p.swing(InteractionHand.MAIN_HAND);
        if (swapped) returnSlot = original;
        return true;
    }

    private LivingEntity findTarget(Minecraft mc, LocalPlayer p) {
        double reach = Math.min(AutoMaceConfig.reach, p.entityInteractionRange());
        double reachSq = reach * reach;
        Vec3 eye = p.getEyePosition();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1.0))) {
            if (e == p || !e.isAlive() || e.isSpectator()) continue;
            if (!wanted(e)) continue;

            double d2 = distSqToBox(eye, e.getBoundingBox());
            if (d2 > reachSq) continue;
            if (!p.hasLineOfSight(e)) continue;

            double score = d2 - (e instanceof Player ? 4.0 : 0.0); // players first, then nearest
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private boolean wanted(LivingEntity e) {
        if (e instanceof Player other) return AutoMaceConfig.targetPlayers && !other.isCreative();
        if (e instanceof TamableAnimal pet && pet.isTame()) return false; // never your own pets
        if (e instanceof Enemy) return AutoMaceConfig.targetHostile;
        if (e instanceof Animal) return AutoMaceConfig.targetAnimals;
        return false;
    }

    /** Distance from a point to the closest point of a hitbox (what the server checks), squared. */
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
