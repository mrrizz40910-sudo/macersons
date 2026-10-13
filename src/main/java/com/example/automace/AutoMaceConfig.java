package com.example.automace;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings, saved to config/automace.properties. */
public final class AutoMaceConfig {
    public static boolean enabled = true;
    public static boolean smashOnly = true;       // only hit when a real smash is possible (falling)
    public static boolean stunSlam = true;        // axe a raised shield, mace on the next tick
    public static double minFall = 1.5;           // blocks of fall before it fires (vanilla smash starts at 1.5)
    public static boolean targetPlayers = true;   // other players and fake-player bots
    public static boolean targetHostile = true;
    public static boolean targetAnimals = true;
    public static int reactionTicks = 0;          // small human-like delay once your crosshair is on a target
    public static int hitSpacing = 6;             // minimum ticks between our own attacks

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("automace.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            Properties p = new Properties();
            p.load(r);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            smashOnly = Boolean.parseBoolean(p.getProperty("smashOnly", "true"));
            stunSlam = Boolean.parseBoolean(p.getProperty("stunSlam", "true"));
            minFall = Double.parseDouble(p.getProperty("minFall", "1.5"));
            targetPlayers = Boolean.parseBoolean(p.getProperty("targetPlayers", "true"));
            targetHostile = Boolean.parseBoolean(p.getProperty("targetHostile", "true"));
            targetAnimals = Boolean.parseBoolean(p.getProperty("targetAnimals", "true"));
            reactionTicks = Integer.parseInt(p.getProperty("reactionTicks", "0"));
            hitSpacing = Integer.parseInt(p.getProperty("hitSpacing", "6"));
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("smashOnly", String.valueOf(smashOnly));
        p.setProperty("stunSlam", String.valueOf(stunSlam));
        p.setProperty("minFall", String.valueOf(minFall));
        p.setProperty("targetPlayers", String.valueOf(targetPlayers));
        p.setProperty("targetHostile", String.valueOf(targetHostile));
        p.setProperty("targetAnimals", String.valueOf(targetAnimals));
        p.setProperty("reactionTicks", String.valueOf(reactionTicks));
        p.setProperty("hitSpacing", String.valueOf(hitSpacing));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Auto Mace settings");
        } catch (IOException ignored) {
        }
    }

    private AutoMaceConfig() {
    }
}
