package tr.dugecraft.tower;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Oyuncu basina sok.sk bellek degiskenleri ({-towers::<alan>::<uuid>}).
 * NaN = "set degil". Cogu alan sadece oyuncunun kendi thread'inden yazilir.
 */
final class PState {
    static final double NONE = Double.NaN;

    static boolean set(double v) {
        return !Double.isNaN(v);
    }

    // --- kazma (mine::<uuid>::*) ---
    volatile boolean holding;
    volatile String tgt;
    volatile double prog = NONE;
    volatile double last = NONE;
    volatile double sig = NONE;

    // --- bossbar ---
    volatile BossBar bar;
    volatile double barT = NONE;

    // --- sok / akim / savas ---
    volatile double immune = NONE;
    volatile double immdur = NONE;
    volatile double current = NONE;
    volatile double combat = NONE;
    volatile double check = NONE;

    volatile boolean debug;
    volatile boolean kdmg;
    volatile double menucd = NONE;
    volatile double upclick = NONE;
    volatile double rc = NONE;
    volatile double beamtime = NONE;
    volatile double shopPending = NONE;
    volatile double shopclick = NONE;
    volatile double bypasslog = NONE;

    /** Gardiyan isinlari (sadece beam_mode guardian) */
    final List<Entity> beams = new ArrayList<>();

    // --- menuler (Skript metadata "tower_menu" / "tower_loc" / "sok_shop") ---
    volatile Inventory towerMenu;
    volatile String towerLoc;
    volatile Inventory shop;

    void clearMine() {
        holding = false;
        tgt = null;
        prog = NONE;
        last = NONE;
        sig = NONE;
    }
}
