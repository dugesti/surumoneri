package tr.dugecraft.claim;

import org.bukkit.configuration.ConfigurationSection;

/** claim.sk v15.16 "options:" bolumu, varsayilanlar birebir ayni. */
public final class ClaimConfig {

    public final String prefix;
    public final int saveBatch;
    public final double upgradePrice;
    public final double upgradeEnergyMult;
    public final double upgradeMinHours;
    public final double upgradeReopenHours;
    public final int nightStartHour;
    public final int nightEndHour;
    public final String nightZone;
    public final String claimWorld;
    public final int borderMin;
    public final int borderMax;
    public final int mapView;
    public final double initialClaimPrice;
    public final double expansionBasePrice;
    public final double priceIncreaseRate;
    public final int maxMainClaims;
    public final int maxMainClaimsVip;
    public final int maxMainClaimsDvip;
    public final int maxMainClaimsDvipPlus;
    public final int memberLimitDefault;
    public final double newClaimProtectHours;
    public final double newClaimProtectCooldownDays;
    public final double energyWarnDays;
    public final double deletedBackupDays;
    public final int memberLimitVip;
    public final int memberLimitDvip;
    public final int memberLimitDvipPlus;
    public final double dayChunkPriceMultiplier;
    public final double maxDays;
    public final double energyStart;
    public final double energyPerChunkHour;
    public final double energyPrice;
    public final double energyMaxDays;
    public final boolean farmerPriceFallback;
    public final double farmerBaseCap;
    public final long sellMinMs;
    public final int sellCheckCount;
    public final double sellCheckMinutes;
    public final double sellSessionGapMin;
    public final long sellRhythmMinMs;
    public final double sellRhythmCv;
    public final int sellAfkFails;
    public final double sellAfkLockMin;

    public ClaimConfig(ConfigurationSection c) {
        prefix = c.getString("prefix", "<#f6d365>DugeCraft &8› ");
        saveBatch = c.getInt("save_batch", 30);
        upgradePrice = c.getDouble("upgrade_price", 1500000);
        upgradeEnergyMult = c.getDouble("upgrade_energy_mult", 3);
        upgradeMinHours = c.getDouble("upgrade_min_hours", 24);
        upgradeReopenHours = c.getDouble("upgrade_reopen_hours", 24);
        nightStartHour = c.getInt("night_start_hour", 0);
        nightEndHour = c.getInt("night_end_hour", 10);
        nightZone = c.getString("night_zone", "Europe/Istanbul");
        claimWorld = c.getString("claim_world", "world");
        borderMin = c.getInt("border_min", -10000);
        borderMax = c.getInt("border_max", 10000);
        mapView = c.getInt("map_view", 9);
        initialClaimPrice = c.getDouble("initial_claim_price", 2500);
        expansionBasePrice = c.getDouble("expansion_base_price", 1000);
        priceIncreaseRate = c.getDouble("price_increase_rate", 1.10);
        maxMainClaims = c.getInt("max_main_claims", 2);
        maxMainClaimsVip = c.getInt("max_main_claims_vip", 3);
        maxMainClaimsDvip = c.getInt("max_main_claims_dvip", 4);
        maxMainClaimsDvipPlus = c.getInt("max_main_claims_dvipplus", 5);
        memberLimitDefault = c.getInt("member_limit_default", 6);
        newClaimProtectHours = c.getDouble("new_claim_protect_hours", 12);
        newClaimProtectCooldownDays = c.getDouble("new_claim_protect_cooldown_days", 7);
        energyWarnDays = c.getDouble("energy_warn_days", 7);
        deletedBackupDays = c.getDouble("deleted_backup_days", 30);
        memberLimitVip = c.getInt("member_limit_vip", 9);
        memberLimitDvip = c.getInt("member_limit_dvip", 13);
        memberLimitDvipPlus = c.getInt("member_limit_dvipplus", 20);
        dayChunkPriceMultiplier = c.getDouble("day_chunk_price_multiplier", 10);
        maxDays = c.getDouble("max_days", 60);
        energyStart = c.getDouble("energy_start", 10000);
        energyPerChunkHour = c.getDouble("energy_per_chunk_hour", 15);
        energyPrice = c.getDouble("energy_price", 3);
        energyMaxDays = c.getDouble("energy_max_days", 60);
        farmerPriceFallback = c.getBoolean("farmer_price_fallback", true);
        farmerBaseCap = c.getDouble("farmer_base_cap", 20000);
        sellMinMs = c.getLong("sell_min_ms", 300);
        sellCheckCount = c.getInt("sell_check_count", 60);
        sellCheckMinutes = c.getDouble("sell_check_minutes", 30);
        sellSessionGapMin = c.getDouble("sell_session_gap_min", 10);
        sellRhythmMinMs = c.getLong("sell_rhythm_min_ms", 1000);
        sellRhythmCv = c.getDouble("sell_rhythm_cv", 0.04);
        sellAfkFails = c.getInt("sell_afk_fails", 3);
        sellAfkLockMin = c.getDouble("sell_afk_lock_min", 5);
    }
}
