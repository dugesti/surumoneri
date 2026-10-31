package tr.dugecraft.blok;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import tr.dugecraft.DugeCraft;
import tr.dugecraft.farm.FarmClaims;
import tr.dugecraft.util.SkriptBridge;
import tr.dugecraft.util.Text;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * bloksinir.sk v4.0'in plugin karsiligi: chunk blok limitleri + buyume kontrolu
 * (kaktus/bambu/yosun kirma, karpuz/balkabagi presi, 2x farm, kemik tozu
 * dupe korumalari) + lag uyari sistemi.
 */
public final class BlokModule {

    static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    final DugeCraft plugin;
    final BlokConfig cfg;
    final LagMonitor lag;
    final LimitStore store;
    final FarmClaims claims;

    /** Tur tablosu (Material -> sayac turu) */
    final Map<Material, String> types = new EnumMap<>(Material.class);
    /** Kemik tozu izleri: karpuz/balkabagi sapi "w;x;y;z" ve bambu "bam;w;x;z" -> nanoTime */
    final ConcurrentHashMap<String, Long> marks = new ConcurrentHashMap<>();
    /** Limit mesaji spam korumasi: oyuncu -> unix saniye */
    final ConcurrentHashMap<UUID, Double> msgCool = new ConcurrentHashMap<>();
    /** /chunkhesapla bekleme: oyuncu -> ms */
    final ConcurrentHashMap<UUID, Long> fullScanCool = new ConcurrentHashMap<>();

    private BlokListener listener;
    private ScheduledTask saveTask;
    private ScheduledTask lagTask;
    private volatile boolean enabled;

    public BlokModule(DugeCraft plugin, BlokConfig cfg, FarmClaims claims) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.claims = claims;
        this.lag = new LagMonitor(cfg);
        this.store = new LimitStore(new File(cfg.dataDir), cfg.prefix, lag);
        types.put(Material.PISTON, "piston");
        types.put(Material.STICKY_PISTON, "piston");
        types.put(Material.CACTUS, "cactus");
        types.put(Material.REPEATER, "repeater");
        types.put(Material.COMPARATOR, "comparator");
        types.put(Material.BAMBOO, "bamboo");
        types.put(Material.BAMBOO_SAPLING, "bamboo");
        types.put(Material.KELP, "kelp");
        types.put(Material.KELP_PLANT, "kelp");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        store.load();
        listener = new BlokListener(this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        long m = cfg.saveMinutes;
        // Skript "every X minutes": ilk calisma bir periyot sonra. Disk isi async.
        saveTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            store.save();
            // Tarama bekleme kayitlari sadece 30 sn icin anlamli; birikmesin
            store.scan.clear();
            // Karpuz izleri sadece birkac ms icin anlamli; birikmesin
            marks.clear();
        }, m, m, TimeUnit.MINUTES);
        lagTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> lag.check(), 20L * 60, 20L * 60);
        publishBilgiApi();
        // Skript scriptleri plugin'lerden SONRA yuklenir: bilgi.sk degerleri gorsun diye tekrar
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> publishBilgiApi(), 20L * 15);
        enabled = true;
    }

    public void disable() {
        if (!enabled) {
            return;
        }
        enabled = false;
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (lagTask != null) {
            lagTask.cancel();
        }
        store.saveFinal();
    }

    /** bilgi.sk'nin farm kitabi icin canli degerler (script blBilgiApi). */
    void publishBilgiApi() {
        if (!SkriptBridge.available()) {
            return;
        }
        SkriptBridge.set("-bilgi::api::farm_piston", String.valueOf(cfg.limitPiston));
        SkriptBridge.set("-bilgi::api::farm_kaktus", String.valueOf(cfg.limitCactus));
        SkriptBridge.set("-bilgi::api::farm_yineleyici", String.valueOf(cfg.limitRepeater));
        SkriptBridge.set("-bilgi::api::farm_karsilastirici", String.valueOf(cfg.limitComparator));
        SkriptBridge.set("-bilgi::api::farm_bambu", String.valueOf(cfg.limitBamboo));
        SkriptBridge.set("-bilgi::api::farm_yosun", String.valueOf(cfg.limitKelp));
        SkriptBridge.set("-bilgi::api::farm_kaktus_sans", String.valueOf(cfg.cactusDropChance));
        SkriptBridge.set("-bilgi::api::farm_bambu_sans", String.valueOf(cfg.bambooDropChance));
        SkriptBridge.set("-bilgi::api::farm_yosun_sans", String.valueOf(cfg.kelpDropChance));
        SkriptBridge.set("-bilgi::api::farm_karpuz_sans", String.valueOf(cfg.melonDropChance));
        SkriptBridge.set("-bilgi::api::farm_kabak_sans", String.valueOf(cfg.pumpkinDropChance));
        SkriptBridge.set("-bilgi::api::farm_pres_mesafe", String.valueOf(cfg.pressDistance));
        SkriptBridge.set("-bilgi::api::farm_urun_hiz", String.valueOf(100 - cfg.cropCancel));
        SkriptBridge.set("-bilgi::cache::*", null);
    }

    // ------------------------------------------------------------------ //
    //  YARDIMCILAR (saf matematik, getChunk() cagirmaz)
    // ------------------------------------------------------------------ //

    static String cidOf(World w, int x, int z) {
        return w.getName() + ";" + Math.floorDiv(x, 16) + ";" + Math.floorDiv(z, 16);
    }

    static String cidOf(Block b) {
        return cidOf(b.getWorld(), b.getX(), b.getZ());
    }

    static int syOf(int y) {
        return Math.floorDiv(y, 16);
    }

    static int roll() {
        return ThreadLocalRandom.current().nextInt(1, 101);
    }

    /** Limit mesaji: oyuncu basina saniyede en fazla bir (sag tik spam'i). */
    void denyMsg(Player p, String msg) {
        double now = LagMonitor.unixNow();
        Double last = msgCool.get(p.getUniqueId());
        if (last != null && now - last < 1) {
            return;
        }
        msgCool.put(p.getUniqueId(), now);
        Text.send(p, msg);
        Location l = p.getLocation();
        l.getWorld().playSound(l, Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
    }

    // ------------------------------------------------------------------ //
    //  SAYIM: tek dilim (16x16x16), 6 tur tek geciste sayilir.
    //  excl: yeni konan blogun turu (BlockPlaceEvent sirasinda blok zaten
    //  dunyada; iki kez sayilmasin diye dusulur, +1 monitor'da eklenir).
    // ------------------------------------------------------------------ //
    void scanSection(World w, int cx, int cz, int sy, String excl) {
        long t0 = System.nanoTime();
        int x0 = cx * 16;
        int y0 = sy * 16;
        int z0 = cz * 16;
        Map<String, Integer> c = new java.util.HashMap<>();
        for (String t : LimitStore.TYPES) {
            c.put(t, 0);
        }
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight() - 1;
        for (int y = y0; y <= y0 + 15; y++) {
            if (y < minY || y > maxY) {
                continue;
            }
            for (int x = x0; x <= x0 + 15; x++) {
                for (int z = z0; z <= z0 + 15; z++) {
                    String t = types.get(w.getType(x, y, z));
                    if (t != null) {
                        c.merge(t, 1, Integer::sum);
                    }
                }
            }
        }
        if (excl != null && !excl.isEmpty() && c.getOrDefault(excl, 0) > 0) {
            c.merge(excl, -1, Integer::sum);
        }
        String cid = w.getName() + ";" + cx + ";" + cz;
        String sys = Integer.toString(sy);
        for (String t : LimitStore.TYPES) {
            int old = store.blGet(cid + ";" + sys + ";" + t);
            store.addCount(cid, sys, t, c.get(t) - old);
        }
        store.seen.add(cid + ";" + sys);
        lag.scanNs.add(System.nanoTime() - t0);
        lag.scans.increment();
    }

    /**
     * Limite dayaninca: bulunulan dilim + bu turun sayili oldugu dilimler taranir.
     * Eski surumden kalan "L" verisi varsa chunk bir kez tamamen taranir.
     */
    void rescanChunk(Block b, String t, boolean skipCur) {
        World w = b.getWorld();
        int cx = Math.floorDiv(b.getX(), 16);
        int cz = Math.floorDiv(b.getZ(), 16);
        int cur = syOf(b.getY());
        String cid = cidOf(b);
        int min = Math.floorDiv(w.getMinHeight(), 16);
        int max = Math.floorDiv(w.getMaxHeight() - 1, 16);

        boolean full = false;
        for (String lt : LimitStore.LEGACY_TYPES) {
            if (store.blGet(cid + ";L;" + lt) > 0) {
                full = true;
            }
        }
        for (int s = min; s <= max; s++) {
            if (s == cur) {
                if (!skipCur) {
                    scanSection(w, cx, cz, s, t);
                }
            } else if (full) {
                scanSection(w, cx, cz, s, "");
            } else if (store.blGet(cid + ";" + s + ";" + t) > 0) {
                scanSection(w, cx, cz, s, "");
            }
        }
        if (full) {
            for (String lt : LimitStore.LEGACY_TYPES) {
                int lv = store.blGet(cid + ";L;" + lt);
                store.addCount(cid, "L", lt, -lv);
            }
        }
    }

    /** YERLESTIRME KONTROLU (true -> iptal et). Sadece karar verir; +1 monitor'da. */
    boolean tryPlace(Player p, Block b, String t) {
        String cid = cidOf(b);
        int sy = syOf(b.getY());

        // Bu dilim bu acilista hic sayilmadiysa bir kez say (4096 blok)
        boolean fresh = false;
        if (!store.seen.contains(cid + ";" + sy)) {
            scanSection(b.getWorld(), Math.floorDiv(b.getX(), 16), Math.floorDiv(b.getZ(), 16), sy, t);
            fresh = true;
        }

        int limit = cfg.limit(t);
        String tk = cid + ";" + t;
        if (store.totGet(tk) < limit) {
            return false;
        }

        // Sayac kaymis olabilir (patlama, fiziksel kirilma vs.) -> dogrula
        double now = LagMonitor.unixNow();
        Double last = store.scan.get(tk);
        boolean doScan = last == null || now - last >= cfg.scanCooldown;
        if (doScan) {
            store.scan.put(tk, now);
            rescanChunk(b, t, fresh);
        }

        int cur = store.totGet(tk);
        if (cur >= limit) {
            denyMsg(p, cfg.prefix + " &cBu chunkta en fazla &e" + limit + " &cadet " + BlokConfig.name(t)
                    + " olabilir! &8(" + cur + "/" + limit + ")");
            return true;
        }
        return false;
    }

    /**
     * Kaktus/bambu/yosun alttan kirilinca ustundekiler fizikle kirilir ve onlar
     * icin BlockBreakEvent gelmez. Ayni x/z -> ayni chunk -> ayni bolge thread'i.
     */
    void breakColumn(Block b, String t) {
        String cid = cidOf(b);
        Map<Integer, Integer> n = new java.util.TreeMap<>();
        n.merge(syOf(b.getY()), 1, Integer::sum);
        if (t.equals("cactus") || t.equals("bamboo") || t.equals("kelp")) {
            Block up = b;
            for (int i = 0; i < 64; i++) {
                up = up.getRelative(BlockFace.UP);
                if (t.equals(types.get(up.getType()))) {
                    n.merge(syOf(up.getY()), 1, Integer::sum);
                } else {
                    break;
                }
            }
        }
        for (Map.Entry<Integer, Integer> e : n.entrySet()) {
            store.addCount(cid, Integer.toString(e.getKey()), t, -e.getValue());
        }
    }

    // ------------------------------------------------------------------ //
    //  BUYUME / URUN
    // ------------------------------------------------------------------ //

    static String melonKey(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    static String bambooKey(Block b) {
        return "bam;" + b.getWorld().getName() + ";" + b.getX() + ";" + b.getZ();
    }

    /**
     * Meyvenin dogacagi blogun en fazla press_distance ustunde asagi bakan
     * piston var mi? Aradaki bloklar bos olmali. Sadece ayni sutun okunur.
     */
    boolean melonPress(Block b) {
        Block cur = b;
        for (int i = 1; i <= cfg.pressDistance; i++) {
            cur = cur.getRelative(BlockFace.UP);
            if ("piston".equals(types.get(cur.getType()))) {
                BlockData bd = cur.getBlockData();
                return bd instanceof Directional d && d.getFacing() == BlockFace.DOWN;
            }
            if (!cur.isEmpty()) {
                return false;
            }
        }
        return false;
    }

    /** true -> bu deneme kemik tozundan geldi (yandaki sap az once 7. yasa geldi). */
    boolean melonBoneMeal(Block b) {
        long now = System.nanoTime();
        boolean hit = false;
        for (BlockFace f : SIDES) {
            Long t = marks.remove(melonKey(b.getRelative(f)));
            if (t != null && now - t < 20_000_000L) {
                hit = true;
            }
        }
        return hit;
    }

    boolean bambooBoneMeal(Block src) {
        Long t = marks.remove(bambooKey(src));
        if (t == null) {
            return false;
        }
        return System.nanoTime() - t < 50_000_000L;
    }

    int farmMult(Block b) {
        return claims.farmMult(b.getWorld(), b.getX(), b.getZ());
    }

    /**
     * Urunun dusecegi yer: once buyume noktasinin yanlari, sonra bitki tepesinin
     * yanlari; sadece bitkiyle ayni claim bolgesindeyse. Hicbiri bos degilse
     * buyume noktasi. En fazla 1 blok yan.
     */
    Block dropSpot(Block b) {
        String zone = claims.dropZone(b.getWorld(), b.getX(), b.getZ());
        for (BlockFace f : SIDES) {
            Block nb = b.getRelative(f);
            if (nb.isEmpty() && claims.dropZone(nb.getWorld(), nb.getX(), nb.getZ()).equals(zone)) {
                return nb;
            }
        }
        Block below = b.getRelative(BlockFace.DOWN);
        for (BlockFace f : SIDES) {
            Block nb = below.getRelative(f);
            if (nb.isEmpty() && claims.dropZone(nb.getWorld(), nb.getX(), nb.getZ()).equals(zone)) {
                return nb;
            }
        }
        return b;
    }

    /** b = buyume noktasi, side = true ise urun yana duser (kaktus, bambu). */
    void growPop(Block b, Material m, int ch, boolean side, int mult) {
        if (roll() > ch) {
            return;
        }
        Block spot = side ? dropSpot(b) : b;
        farmOut(spot, m, mult);
    }

    /** Vanilla karpuz blogu: 3-7 dilim (esit ihtimal) x mult. */
    void melonPop(Block b, int mult) {
        if (roll() > cfg.melonDropChance) {
            return;
        }
        int amt = ThreadLocalRandom.current().nextInt(3, 8) * mult;
        farmOut(b, Material.MELON_SLICE, amt);
    }

    void pumpkinPop(Block b, int mult) {
        if (roll() > cfg.pumpkinDropChance) {
            return;
        }
        farmOut(b, Material.PUMPKIN, mult);
    }

    /**
     * Urunu cikarir. Ciftci alirsa yere hicbir sey dusmez; almazsa ayni
     * noktaya, savrulmadan duser. "Ver" ve "dusur" ayni if'in iki kolu:
     * bir urun ikisine birden gidemez.
     */
    void farmOut(Block spot, Material m, int n) {
        Location loc = spot.getLocation().add(0.5, 0.2, 0.5);
        if (claims.farmerTake(loc, m, n)) {
            lag.direct.increment();
        } else {
            Item item = spot.getWorld().dropItem(loc, new ItemStack(m, n));
            // Rastgele savrulma olmasin, dumduz asagi dussun / yukari ciksin
            item.setVelocity(new Vector(0, 0, 0));
            lag.drops.increment();
        }
    }
}
