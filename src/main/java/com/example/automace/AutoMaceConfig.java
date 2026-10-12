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
    public static boolean targetPlayers = true;   // other players and fake-player bots
    public static boolean targetHostile = true;   // zombies, skeletons, creepers...
    public static boolean targetAnimals = true;   // sheep, cows, pigs...
    public static boolean stunSlam = true;        // axe a raised shield first, mace after
    public static boolean requireFall = false;    // only hit while falling (real smash hits)
    public static double reach = 3.0;             // blocks, measured to the target's hitbox

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
            targetPlayers = Boolean.parseBoolean(p.getProperty("targetPlayers", "true"));
            targetHostile = Boolean.parseBoolean(p.getProperty("targetHostile", "true"));
            targetAnimals = Boolean.parseBoolean(p.getProperty("targetAnimals", "true"));
            stunSlam = Boolean.parseBoolean(p.getProperty("stunSlam", "true"));
            requireFall = Boolean.parseBoolean(p.getProperty("requireFall", "false"));
            reach = Double.parseDouble(p.getProperty("reach", "3.0"));
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("targetPlayers", String.valueOf(targetPlayers));
        p.setProperty("targetHostile", String.valueOf(targetHostile));
        p.setProperty("targetAnimals", String.valueOf(targetAnimals));
        p.setProperty("stunSlam", String.valueOf(stunSlam));
        p.setProperty("requireFall", String.valueOf(requireFall));
        p.setProperty("reach", String.valueOf(reach));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Auto Mace settings");
        } catch (IOException ignored) {
        }
    }

    private AutoMaceConfig() {
    }
}
