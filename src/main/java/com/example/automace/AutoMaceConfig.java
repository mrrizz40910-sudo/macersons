package com.example.automace;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class AutoMaceConfig {

    // Combat
    public static boolean enabled = true;
    public static boolean assistOnClick = false;
    public static boolean smashOnly = true;
    public static boolean stunSlam = true;
    public static boolean smartWeaponPick = true;
    public static boolean spearReach = true;
    public static boolean windBoost = true;

    // Targets
    public static boolean targetPlayers = true;
    public static boolean targetHostiles = true;
    public static boolean targetAnimals = false;
    public static List<String> ignoreList = new ArrayList<>();

    // Timing
    public static double minFall = 1.5;
    public static int reactionDelay = 2;
    public static int hitSpacing = 3;

    // Interface
    public static boolean hud = true;
    public static boolean noMaceTip = true;

    private static final Path FILE = Path.of("config", "automace.properties");

    public static void load() {
        ignoreList = new ArrayList<>();
        Properties props = new Properties();
        if (!Files.exists(FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            props.load(reader);
        } catch (IOException e) {
            return;
        }
        enabled = getBool(props, "enabled", true);
        assistOnClick = getBool(props, "assistOnClick", false);
        smashOnly = getBool(props, "smashOnly", true);
        stunSlam = getBool(props, "stunSlam", true);
        smartWeaponPick = getBool(props, "smartWeaponPick", true);
        spearReach = getBool(props, "spearReach", true);
        windBoost = getBool(props, "windBoost", true);
        targetPlayers = getBool(props, "targetPlayers", true);
        targetHostiles = getBool(props, "targetHostiles", true);
        targetAnimals = getBool(props, "targetAnimals", false);
        hud = getBool(props, "hud", true);
        noMaceTip = getBool(props, "noMaceTip", true);
        minFall = clampDouble(getDouble(props, "minFall", 1.5), 0.5, 20.0);
        reactionDelay = (int) clampLong(getLong(props, "reactionDelay", 2), 0, 20);
        hitSpacing = (int) clampLong(getLong(props, "hitSpacing", 3), 1, 40);
        String ignore = props.getProperty("ignoreList", "");
        for (String entry : ignore.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                ignoreList.add(trimmed);
            }
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("enabled", String.valueOf(enabled));
        props.setProperty("assistOnClick", String.valueOf(assistOnClick));
        props.setProperty("smashOnly", String.valueOf(smashOnly));
        props.setProperty("stunSlam", String.valueOf(stunSlam));
        props.setProperty("smartWeaponPick", String.valueOf(smartWeaponPick));
        props.setProperty("spearReach", String.valueOf(spearReach));
        props.setProperty("windBoost", String.valueOf(windBoost));
        props.setProperty("targetPlayers", String.valueOf(targetPlayers));
        props.setProperty("targetHostiles", String.valueOf(targetHostiles));
        props.setProperty("targetAnimals", String.valueOf(targetAnimals));
        props.setProperty("hud", String.valueOf(hud));
        props.setProperty("noMaceTip", String.valueOf(noMaceTip));
        props.setProperty("minFall", String.valueOf(minFall));
        props.setProperty("reactionDelay", String.valueOf(reactionDelay));
        props.setProperty("hitSpacing", String.valueOf(hitSpacing));
        props.setProperty("ignoreList", String.join(",", ignoreList));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                props.store(writer, "Auto Mace config (Minecraft 1.21.11)");
            }
        } catch (IOException ignored) {
        }
    }

    public static void resetDefaults() {
        enabled = true;
        assistOnClick = false;
        smashOnly = true;
        stunSlam = true;
        smartWeaponPick = true;
        spearReach = true;
        windBoost = true;
        targetPlayers = true;
        targetHostiles = true;
        targetAnimals = false;
        ignoreList = new ArrayList<>();
        minFall = 1.5;
        reactionDelay = 2;
        hitSpacing = 3;
        hud = true;
        noMaceTip = true;
    }

    private static boolean getBool(Properties props, String key, boolean fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static double getDouble(Properties props, String key, double fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long getLong(Properties props, String key, long fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
