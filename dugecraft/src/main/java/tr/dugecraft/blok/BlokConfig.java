package tr.dugecraft.blok;

import org.bukkit.configuration.ConfigurationSection;

/**
 * bloksinir.sk "options:" bolumu. Varsayilanlar script v4.0 ile birebir ayni;
 * config.yml'deki "blok-limit" bolumunden degistirilebilir.
 */
public final class BlokConfig {

    public final String prefix;

    public final int limitPiston;
    public final int limitCactus;
    public final int limitRepeater;
    public final int limitComparator;
    public final int limitBamboo;
    public final int limitKelp;

    public final int cactusDropChance;
    public final int bambooDropChance;
    public final int kelpDropChance;
    public final int melonDropChance;
    public final int pumpkinDropChance;
    public final int pressDistance;
    public final int cropCancel;

    public final int scanCooldown;
    public final int saveMinutes;
    public final String dataDir;

    public final String warnPerm;
    public final long warnScriptMs;
    public final long warnSpikeMs;
    public final long warnSaveMs;
    public final long warnDrops;
    public final double warnTps;
    public final double warnCpu;
    public final int warnRepeatMinutes;

    public BlokConfig(ConfigurationSection c) {
        prefix = c.getString("prefix", "&6DugeCraft &8»");

        limitPiston = c.getInt("limit_piston", 384);
        limitCactus = c.getInt("limit_cactus", 384);
        limitRepeater = c.getInt("limit_repeater", 20);
        limitComparator = c.getInt("limit_comparator", 20);
        limitBamboo = c.getInt("limit_bamboo", 768);
        limitKelp = c.getInt("limit_kelp", 768);

        cactusDropChance = c.getInt("cactus_drop_chance", 100);
        bambooDropChance = c.getInt("bamboo_drop_chance", 100);
        kelpDropChance = c.getInt("kelp_drop_chance", 100);
        melonDropChance = c.getInt("melon_drop_chance", 100);
        pumpkinDropChance = c.getInt("pumpkin_drop_chance", 100);
        pressDistance = c.getInt("press_distance", 2);
        cropCancel = c.getInt("crop_cancel", 0);

        scanCooldown = c.getInt("scan_cooldown", 30);
        saveMinutes = Math.max(1, c.getInt("save_minutes", 2));
        dataDir = c.getString("data_dir", "plugins/DugeCraft");

        warnPerm = c.getString("warn_perm", "dugecraft.lagwarn");
        warnScriptMs = c.getLong("warn_script_ms", 1000);
        warnSpikeMs = c.getLong("warn_spike_ms", 50);
        warnSaveMs = c.getLong("warn_save_ms", 1000);
        warnDrops = c.getLong("warn_drops", 5000);
        warnTps = c.getDouble("warn_tps", 15);
        warnCpu = c.getDouble("warn_cpu", 85);
        warnRepeatMinutes = c.getInt("warn_repeat_minutes", 5);
    }

    public int limit(String t) {
        return switch (t) {
            case "piston" -> limitPiston;
            case "cactus" -> limitCactus;
            case "repeater" -> limitRepeater;
            case "comparator" -> limitComparator;
            case "bamboo" -> limitBamboo;
            case "kelp" -> limitKelp;
            default -> 999999;
        };
    }

    public static String name(String t) {
        return switch (t) {
            case "piston" -> "piston";
            case "cactus" -> "kaktüs";
            case "repeater" -> "yineleyici";
            case "comparator" -> "karşılaştırıcı";
            case "bamboo" -> "bambu";
            case "kelp" -> "su yosunu";
            default -> t;
        };
    }
}
