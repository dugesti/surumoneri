package tr.dugecraft.tower;

import org.bukkit.configuration.ConfigurationSection;

/** sok.sk v11.62 "options:" bolumu, varsayilanlar birebir ayni. */
public final class TowerConfig {

    public final String dataDir;
    public final String backupDir;
    public final int backupDays;
    public final int flushMaxFiles;
    public final String prefix;

    private final double[] dmg = new double[7];
    private final double[] range = new double[7];
    private final double[] cd = new double[7];
    private final double[] cost = new double[7];

    public final double shockImmunity;
    public final double immunityCdRatio;
    public final double targetWindow;
    public final boolean protDouble;
    public final double protTowerP5;
    public final double protTowerStep;
    public final double protPvpP5;
    public final double protPvpStep;
    public final double protOverMax;
    public final boolean protOverPvp;
    public final double stackBonus;
    public final double stackSupport;
    public final double stackCap;
    public final double stackMaxDamage;
    public final int stackMinLevel;
    public final double stackGrowth;
    public final double stackRef;
    public final double emiciTowerMult;
    public final double emiciTowerMax;
    public final double minTowerGap;
    public final double mineMaxDistance;
    public final double shopPrice;
    public final String shopNpcName;
    public final double trueDamagePercent;
    public final int wallLevel;
    public final double mineTimeIron;
    public final double mineTimeDiamond;
    public final double mineTimeNetherite;
    public final double mineReset;
    public final double effBonusPerLevel;
    public final double effBonusMax;
    public final double hasteBonusPerLevel;
    public final double hasteBonusMax;
    public final double hpRegenSeconds;
    public final int towerHp;
    public final int maxTowersPerClaim;
    public final boolean itemNoDespawn;
    public final boolean recoverByName;
    public final double orphanGraceSeconds;
    public final double combatSeconds;
    public final boolean combatKillOnQuit;
    public final String combatProvider;
    public final String pvpmTagCommand;
    public final String pvpmUntagCommand;
    public final boolean bypassStaff;
    public final String bypassPermission;
    public final int currentTicks;
    public final int currentAmp;
    public final String beamMode;
    public final double beamLife;
    public final int maxBeams;
    public final double beamHeight;
    public final double beamX;
    public final double beamZ;
    public final boolean beamFreeze;
    public final double beamStep;
    public final double beamSpread;
    /** 0 = ust blok, 1-6 = seviyeler */
    public final String[] states = new String[7];

    public TowerConfig(ConfigurationSection c) {
        dataDir = c.getString("data_dir", "plugins/Skript/yaml/sok_kuleleri");
        backupDir = c.getString("backup_dir", "plugins/Skript/yaml/sok_kuleleri_yedek");
        backupDays = c.getInt("backup_days", 7);
        flushMaxFiles = c.getInt("flush_max_files", 40);
        prefix = c.getString("prefix", "<#5ac8fa>⚡ <#e5e7eb>Şok Kulesi <#4b5563>│<#9ca3af>");

        double[] dDmg = {0, 18, 24, 28, 32, 36, 40};
        double[] dRange = {0, 12, 20, 26, 32, 38, 44};
        double[] dCd = {0, 6, 5, 4, 3, 2, 1};
        double[] dCost = {0, 0, 3000, 9000, 27000, 81000, 243000};
        for (int i = 1; i <= 6; i++) {
            dmg[i] = c.getDouble("lvl" + i + "_dmg", dDmg[i]);
            range[i] = c.getDouble("lvl" + i + "_range", dRange[i]);
            cd[i] = c.getDouble("lvl" + i + "_cd", dCd[i]);
            cost[i] = i == 1 ? 0 : c.getDouble("lvl" + i + "_cost", dCost[i]);
        }

        shockImmunity = c.getDouble("shock_immunity", 1.2);
        immunityCdRatio = c.getDouble("immunity_cd_ratio", 0.3);
        targetWindow = c.getDouble("target_window", 2.5);
        protDouble = c.getBoolean("prot_double", true);
        protTowerP5 = c.getDouble("prot_tower_p5", 15);
        protTowerStep = c.getDouble("prot_tower_step", 10);
        protPvpP5 = c.getDouble("prot_pvp_p5", 20);
        protPvpStep = c.getDouble("prot_pvp_step", 10);
        protOverMax = c.getDouble("prot_over_max", 32);
        protOverPvp = c.getBoolean("prot_over_pvp", true);
        stackBonus = c.getDouble("stack_bonus", 0);
        stackSupport = c.getDouble("stack_support", 0.20);
        stackCap = c.getDouble("stack_cap", 1.40);
        stackMaxDamage = c.getDouble("stack_max_damage", 72);
        stackMinLevel = c.getInt("stack_min_level", 1);
        stackGrowth = c.getDouble("stack_growth", 10);
        stackRef = c.getDouble("stack_ref", 80);
        emiciTowerMult = c.getDouble("emici_tower_mult", 1);
        emiciTowerMax = c.getDouble("emici_tower_max", 0.75);
        minTowerGap = c.getDouble("min_tower_gap", 0);
        mineMaxDistance = c.getDouble("mine_max_distance", 7);
        shopPrice = c.getDouble("shop_price", 1500);
        shopNpcName = c.getString("shop_npc_name", "kule");
        trueDamagePercent = c.getDouble("true_damage_percent", 28);
        wallLevel = c.getInt("wall_level", 2);
        mineTimeIron = c.getDouble("mine_time_iron", 4.0);
        mineTimeDiamond = c.getDouble("mine_time_diamond", 3.43);
        mineTimeNetherite = c.getDouble("mine_time_netherite", 2.86);
        mineReset = c.getDouble("mine_reset", 0.6);
        effBonusPerLevel = c.getDouble("eff_bonus_per_level", 0.06);
        effBonusMax = c.getDouble("eff_bonus_max", 0.30);
        hasteBonusPerLevel = c.getDouble("haste_bonus_per_level", 0.10);
        hasteBonusMax = c.getDouble("haste_bonus_max", 0.20);
        hpRegenSeconds = c.getDouble("hp_regen_seconds", 120);
        towerHp = c.getInt("tower_hp", 1);
        maxTowersPerClaim = c.getInt("max_towers_per_claim", 0);
        itemNoDespawn = c.getBoolean("item_no_despawn", true);
        recoverByName = c.getBoolean("recover_by_name", true);
        orphanGraceSeconds = c.getDouble("orphan_grace_seconds", 60);
        combatSeconds = c.getDouble("combat_seconds", 20);
        combatKillOnQuit = c.getBoolean("combat_kill_on_quit", true);
        combatProvider = c.getString("combat_provider", "auto");
        pvpmTagCommand = c.getString("pvpm_tag_command", "pvptag <player>");
        pvpmUntagCommand = c.getString("pvpm_untag_command", "untag <player>");
        bypassStaff = c.getBoolean("bypass_staff", true);
        bypassPermission = c.getString("bypass_permission", "sokkulesi.bypass");
        currentTicks = c.getInt("current_ticks", 120);
        currentAmp = c.getInt("current_amp", 1);
        beamMode = c.getString("beam_mode", "particle");
        beamLife = c.getDouble("beam_life", 3);
        maxBeams = c.getInt("max_beams", 6);
        beamHeight = c.getDouble("beam_height", 1.75);
        beamX = c.getDouble("beam_x", 0.5);
        beamZ = c.getDouble("beam_z", 0.5);
        beamFreeze = c.getBoolean("beam_freeze", true);
        beamStep = c.getDouble("beam_step", 0.25);
        beamSpread = c.getDouble("beam_spread", 0.09);

        String[] dStates = {
                "minecraft:chorus_plant[down=false,east=true,north=true,south=true,up=false,west=false]",
                "minecraft:chorus_plant[down=false,east=true,north=true,south=true,up=true,west=true]",
                "minecraft:chorus_plant[down=false,east=true,north=true,south=true,up=true,west=false]",
                "minecraft:chorus_plant[down=false,east=true,north=true,south=false,up=true,west=true]",
                "minecraft:chorus_plant[down=false,east=false,north=true,south=true,up=true,west=true]",
                "minecraft:chorus_plant[down=false,east=true,north=false,south=true,up=true,west=true]",
                "minecraft:chorus_plant[down=false,east=true,north=true,south=true,up=false,west=true]"};
        states[0] = c.getString("state_top", dStates[0]);
        for (int i = 1; i <= 6; i++) {
            states[i] = c.getString("state_lv" + i, dStates[i]);
        }
    }

    private static int lv(int level) {
        if (level >= 6) {
            return 6;
        }
        return Math.max(1, level);
    }

    /** sok.sk towerDamage: 6 ve ustu Sv6, 1 ve alti Sv1. */
    public double damage(int level) {
        return dmg[lv(level)];
    }

    public double range(int level) {
        return range[lv(level)];
    }

    public double cooldown(int level) {
        return cd[lv(level)];
    }

    /** O seviyeye yukseltme ucreti (sadece 2-6, digerleri 0). */
    public double cost(int level) {
        if (level < 2 || level > 6) {
            return 0;
        }
        return cost[level];
    }

    public int maxHp() {
        return Math.max(1, towerHp);
    }

    /** Zirhsiz oyuncuya giden gercek hasar (kalp, 1 ondalik). */
    public double hearts(int level) {
        double d = damage(level);
        double t = trueDamagePercent / 100.0;
        double h = d * (1 - t) + (d * t) / 2;
        return Math.round(h * 10) / 10.0;
    }

    /** Bu seviyedeki kule vurduktan sonra oyuncunun bagisiklik suresi. */
    public double immunity(int level) {
        double im = shockImmunity;
        if (immunityCdRatio > 0) {
            double c = cooldown(level) * immunityCdRatio;
            if (c > im) {
                im = c;
            }
        }
        return im;
    }

    public boolean seesThroughWalls(int level) {
        return level >= wallLevel;
    }

    public String wallText(int level) {
        return seesThroughWalls(level) ? "<#4ade80>✔ Evet" : "<#f87171>✖ Hayır";
    }

    /** byrange indeks yaricapi: ceil(menzil/16), en az 1. */
    public int indexRadius(int level) {
        return Math.max(1, (int) Math.ceil(range(level) / 16.0));
    }

    public int indexRadiusMax() {
        int m = 1;
        for (int i = 1; i <= 6; i++) {
            m = Math.max(m, indexRadius(i));
        }
        return m;
    }
}
