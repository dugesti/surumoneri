package tr.dugecraft.tower;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import tr.dugecraft.DugeCraft;
import tr.dugecraft.claim.ClaimApi;
import tr.dugecraft.util.DynCommands;
import tr.dugecraft.util.SkriptBridge;
import tr.dugecraft.util.Text;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** sok.sk v11.62'nin plugin karsiligi: sok kulesi sistemi. */
public final class TowerModule {

    public static final String VERSION = "v11.62";

    final DugeCraft plugin;
    final TowerConfig cfg;
    final ClaimApi claims;
    final TowerStore store;
    final TowerItems items;
    final EmiciBridge emici;
    final TowerUi ui;

    final ConcurrentHashMap<UUID, PState> players = new ConcurrentHashMap<>();
    /** tgt::<kule>::<uuid> = oyuncunun bu kulenin menzilinde son goruldugu an */
    final ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> tgt = new ConcurrentHashMap<>();
    /** thit::<kule>::<uuid> = bu kulenin bu oyuncuya son vurdugu an */
    final ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> thit = new ConcurrentHashMap<>();
    /** ptgt::<uuid>::<kule> = oyuncunun aday oldugu kuleler */
    final ConcurrentHashMap<String, Set<String>> ptgt = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Double> restoreLog = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Integer> restoreCnt = new ConcurrentHashMap<>();
    /** PvPManager komut kuyrugu (API yoksa): uuid -> oyuncu adi */
    final ConcurrentHashMap<String, String> cmdQueue = new ConcurrentHashMap<>();

    /** Kule durumlarinin kanonik metni (kaydi kaybolan kuleyi tanimak icin) */
    final String[] stateStr = new String[7];
    final BlockData[] stateData = new BlockData[7];

    volatile boolean claimsReady;
    volatile boolean pvpm;
    private volatile String pvpmApi;
    volatile boolean dmgTest;
    volatile double dmgTestAt = PState.NONE;
    volatile boolean kdmgAny;
    final Set<String> cmdAllow = Set.of("msg", "r", "w", "tell", "mesaj", "yardim", "help");

    private TowerListener listener;
    private final List<ScheduledTask> tasks = new ArrayList<>();
    private volatile boolean enabled;

    public TowerModule(DugeCraft plugin, TowerConfig cfg, ClaimApi claims) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.claims = claims;
        this.store = new TowerStore(cfg);
        this.items = new TowerItems(cfg);
        this.emici = new EmiciBridge(plugin.getLogger());
        this.ui = new TowerUi(this);
        for (int i = 0; i <= 6; i++) {
            stateData[i] = Bukkit.createBlockData(cfg.states[i]);
            stateStr[i] = stateData[i].getAsString();
        }
    }

    static double now() {
        return System.currentTimeMillis() / 1000.0;
    }

    PState ps(Player p) {
        return players.computeIfAbsent(p.getUniqueId(), u -> new PState());
    }

    PState ps(UUID u) {
        return players.get(u);
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ------------------------------------------------------------------ //
    //  ACILIS / KAPANIS
    // ------------------------------------------------------------------ //

    public void enable() {
        // PvPManager kurulu mu?
        pvpm = false;
        if (!cfg.combatProvider.equals("self")) {
            if (Bukkit.getPluginManager().isPluginEnabled("PvPManager")) {
                pvpm = true;
                Text.console(cfg.prefix + " <#4ade80>PvPManager bulundu, savaş süresi ona devredildi.");
            } else if (cfg.combatProvider.equals("pvpm")) {
                Text.console(cfg.prefix + " <#f87171>PvPManager bulunamadı! Dahili savaş sistemi kullanılacak.");
            }
        }
        pvpmApi = null;
        // Bypass izni "varsayilan: kimsede yok" (kayitsiz izin OP'lere otomatik verilirdi)
        if (Bukkit.getPluginManager().getPermission(cfg.bypassPermission) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(cfg.bypassPermission, PermissionDefault.FALSE));
        }
        store.backup();
        claimsReady = claims.dataReady();
        store.loadAll();
        Text.console(cfg.prefix + " <#4ade80>Şok Kuleleri yüklendi! <#9ca3af>(" + store.size() + " kule) <#4b5563>[" + VERSION + "]");

        listener = new TowerListener(this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // Kirli dosyalar 2 sn'de bir yazilir (disk isi async, tek yazici kilitli)
        tasks.add(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> store.flush(cfg.flushMaxFiles),
                2, 2, TimeUnit.SECONDS));
        // PvPManager komut yolu: konsol komutlari global thread'de
        tasks.add(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runCmdQueue(), 5, 5));
        // Bakim: sahipsiz kuleler, bekleyen dunyalar (sadece degisken)
        tasks.add(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> maintenance(), 200, 200));
        publishInfo();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> publishInfo(), 20L * 15);
        TowerCommands cmds = new TowerCommands(this);
        DynCommands.register("sokkulesi", "Şok kulesi yönetimi", cmds);
        DynCommands.register("sokdebug", "Şok kulesi debug modu", cmds);
        DynCommands.register("sokmagaza", "Şok Kulesi mağazasını açar (sadece NPC/konsol)", cmds);
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
        for (ScheduledTask t : tasks) {
            t.cancel();
        }
        tasks.clear();
        store.flush(0);
        // Acik kazma bossbar'lari ekranda asili kalmasin
        for (PState s : players.values()) {
            BossBar b = s.bar;
            if (b != null) {
                b.removeAll();
            }
            s.bar = null;
        }
    }

    // ------------------------------------------------------------------ //
    //  GORUNUM / BLOK (sadece kulenin bolge thread'inde cagrilir)
    // ------------------------------------------------------------------ //

    static Location above(Location l) {
        return new Location(l.getWorld(), l.getX(), l.getY() + 1, l.getZ());
    }

    static Location below(Location l) {
        return new Location(l.getWorld(), l.getX(), l.getY() - 1, l.getZ());
    }

    static String mat(Block b) {
        return b == null ? "" : b.getType().name();
    }

    static boolean isTowerMaterial(Block b) {
        Material m = b.getType();
        return m == Material.CHORUS_PLANT || m == Material.NOTE_BLOCK;
    }

    void setTowerBlock(Location loc, int state) {
        loc.getBlock().setBlockData(stateData[Math.max(0, Math.min(6, state))].clone(), false);
    }

    int levelOf(String key) {
        Tower t = store.get(key);
        return t == null ? 1 : t.level;
    }

    /** Kayitli kulenin blogu yerinde mi? Eski (note block) kuleyi yerinde cevirir. */
    boolean towerBlockOk(Location loc) {
        String m = mat(loc.getBlock());
        if (m.equals("CHORUS_PLANT")) {
            return true;
        }
        if (m.isEmpty()) {
            return true;
        }
        if (!m.equals("NOTE_BLOCK")) {
            return false;
        }
        applyModel(loc, levelOf(Tower.keyOf(loc)));
        return true;
    }

    boolean isTowerTop(Location loc) {
        if (!isTowerMaterial(loc.getBlock())) {
            return false;
        }
        return store.isTowerKey(Tower.keyOf(below(loc)));
    }

    /** 0 = ust blok, 1-6 = kule seviyesi, -1 = degil */
    int levelOfState(Block b) {
        if (b.getType() != Material.CHORUS_PLANT) {
            return -1;
        }
        String s = b.getBlockData().getAsString();
        for (int i = 0; i <= 6; i++) {
            if (s.equals(stateStr[i])) {
                return i;
            }
        }
        return -1;
    }

    /** KAYDI KAYBOLMUS KULEYI GERI KAYDET (blok durumundan taninir). */
    boolean adopt(Location loc) {
        int st = levelOfState(loc.getBlock());
        if (st < 0) {
            return false;
        }
        Location base;
        int lv;
        if (st == 0) {
            base = below(loc);
            lv = levelOfState(base.getBlock());
            if (lv < 1) {
                return false;
            }
        } else {
            base = loc;
            lv = st;
            if (levelOfState(above(base).getBlock()) != 0) {
                return false;
            }
        }
        String k = Tower.keyOf(base);
        if (store.isTowerKey(k)) {
            return true;
        }
        String cid = Tower.chunkId(base);
        if (claims.owner(cid) == null) {
            return false;
        }
        String mainCid = claims.mainCidRaw(cid);
        if (mainCid == null || mainCid.equals("<none>") || mainCid.isEmpty()) {
            mainCid = cid;
        }
        Tower t = new Tower(k, base.getWorld(), base.getX(), base.getY(), base.getZ());
        t.cid = mainCid;
        t.fcid = cid;
        t.level = lv;
        t.hp = cfg.maxHp();
        t.lastAttack = 0;
        store.register(t);
        Text.console(cfg.prefix + " <#fbbf24>Kaydı kaybolmuş kule geri kaydedildi: <#e5e7eb>" + k
                + " <#9ca3af>(Sv" + lv + ", claim " + mainCid + ")");
        return true;
    }

    /** Tiklanan/kazilan blok bir kule parcasiysa kulenin ALT blogu, degilse null. */
    Location baseOf(Location loc) {
        if (store.isTowerKey(Tower.keyOf(loc))) {
            return loc;
        }
        if (isTowerTop(loc)) {
            return below(loc);
        }
        if (adopt(loc)) {
            if (store.isTowerKey(Tower.keyOf(loc))) {
                return loc;
            }
            return below(loc);
        }
        return null;
    }

    /** Kulenin iki yarisi da (alt + gorunmez ust) korunur; kaydi kaybolmus kule de. */
    boolean isTowerPart(Location loc) {
        if (store.isTowerKey(Tower.keyOf(loc))) {
            return true;
        }
        if (isTowerTop(loc)) {
            return true;
        }
        return adopt(loc);
    }

    void placeTop(Location loc) {
        Location up = above(loc);
        if (store.isTowerKey(Tower.keyOf(up))) {
            return;
        }
        Block ub = up.getBlock();
        String um = mat(ub);
        if (um.equals("AIR") || um.equals("CAVE_AIR") || um.equals("VOID_AIR") || um.equals("BARRIER") || um.equals("CHORUS_PLANT")) {
            setTowerBlock(up, 0);
            return;
        }
        if (um.equals("NOTE_BLOCK") && ub.getBlockData().getAsString().contains("note=18,")) {
            setTowerBlock(up, 0);
        }
    }

    void clearTop(Location loc) {
        Location up = above(loc);
        if (store.isTowerKey(Tower.keyOf(up))) {
            return;
        }
        String um = mat(up.getBlock());
        if (um.equals("CHORUS_PLANT") || um.equals("NOTE_BLOCK") || um.equals("BARRIER")) {
            up.getBlock().setType(Material.AIR);
        }
    }

    void applyModel(Location loc, int level) {
        setTowerBlock(loc, level);
        placeTop(loc);
    }

    /** Kaydi ve hedef sirasini siler (sok.sk unregisterTower). */
    void unregister(String key) {
        store.unregister(key);
        tgt.remove(key);
        thit.remove(key);
        restoreLog.remove(key);
        restoreCnt.remove(key);
    }

    // ------------------------------------------------------------------ //
    //  CLAIM ILISKISI
    // ------------------------------------------------------------------ //

    /** Kulenin claim baglantisi koptu mu? (canli, sadece degisken okur) */
    boolean claimGone(Tower t) {
        if (!claimsReady) {
            return false;
        }
        String cid = t.cid;
        if (cid == null) {
            return true;
        }
        if (claims.owner(cid) == null) {
            return true;
        }
        String fc = t.fcid;
        if (fc == null) {
            return false;
        }
        if (claims.owner(fc) == null) {
            return true;
        }
        String fm = claims.mainCidRaw(fc);
        if (fm == null || fm.equals("<none>") || fm.isEmpty()) {
            fm = fc;
        }
        return !fm.equals(cid);
    }

    boolean isHostile(Player p, String key) {
        Tower t = store.get(key);
        if (t == null || t.orphan) {
            return false;
        }
        if (!claimsReady) {
            return false;
        }
        String cid = t.cid;
        if (cid == null) {
            return false;
        }
        if (claimGone(t)) {
            return false;
        }
        String u = p.getUniqueId().toString();
        if (u.equals(claims.owner(cid))) {
            return false;
        }
        return !claims.isMember(cid, u);
    }

    boolean bypass(Player p) {
        return p.hasPermission(cfg.bypassPermission);
    }

    boolean isMemberOf(Player p, String cid) {
        String u = p.getUniqueId().toString();
        return u.equals(claims.owner(cid)) || claims.isMember(cid, u);
    }

    /** Claim'e yabanci yetkilinin (OP / claim.kurucu) anlik sokmesi: sadece egilerek. */
    boolean staffRemove(Player p, String cid) {
        if (!p.isSneaking()) {
            return false;
        }
        if (!p.isOp() && !p.hasPermission("claim.kurucu")) {
            return false;
        }
        return claims.canBreakTower(p, cid);
    }

    // ------------------------------------------------------------------ //
    //  SAVAS ETIKETI
    // ------------------------------------------------------------------ //

    private String pvpmApiKind() {
        String k = pvpmApi;
        if (k != null) {
            return k;
        }
        k = "yok";
        Plugin pl = Bukkit.getPluginManager().getPlugin("PvPManager");
        if (pl != null) {
            String cn = pl.getClass().getName();
            if (cn.equals("me.chancesd.pvpmanager.PvPManager")) {
                k = "v4";
            } else if (cn.equals("me.NoChance.PvPManager.PvPManager")) {
                k = "v3";
            }
        }
        pvpmApi = k;
        if (k.equals("yok")) {
            Text.console(cfg.prefix + " <#fbbf24>PvPManager sürümü tanınmadı, savaş etiketi komutla verilecek (pvptag/untag).");
        } else {
            Text.console(cfg.prefix + " <#4ade80>PvPManager API bağlandı <#4b5563>(<#e5e7eb>" + k + "<#4b5563>)");
        }
        return k;
    }

    private Object pvpmPlayer(Player p) {
        String kind = pvpmApiKind();
        if (kind.equals("yok")) {
            return null;
        }
        Plugin pl = Bukkit.getPluginManager().getPlugin("PvPManager");
        if (pl == null) {
            return null;
        }
        try {
            Object h = pl.getClass().getMethod(kind.equals("v4") ? "getPlayerManager" : "getPlayerHandler").invoke(pl);
            if (h == null) {
                return null;
            }
            for (Method m : h.getClass().getMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(Player.class)) {
                    return m.invoke(h, p);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // API degismis olabilir: komut yolu
        }
        return null;
    }

    private static Object call(Object o, String name, Object... args) {
        for (Method m : o.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    return m.invoke(o, args);
                } catch (ReflectiveOperationException | IllegalArgumentException e) {
                    // ayni adli baska imzayi dene
                }
            }
        }
        return null;
    }

    void combatTag(Player p) {
        PState s = ps(p);
        if (pvpm) {
            s.combat = now() + cfg.combatSeconds;
            Object pv = pvpmPlayer(p);
            if (pv != null) {
                // Kule "saldiran", oyuncu "saldirilan" (false); karsi taraf oyuncunun kendisi
                call(pv, pvpmApiKind().equals("v4") ? "tag" : "setTagged", false, pv);
                return;
            }
            cmdQueue.put(p.getUniqueId().toString(), p.getName());
            return;
        }
        double n = now();
        double old = s.combat;
        s.combat = n + cfg.combatSeconds;
        String cs = Text.num(cfg.combatSeconds);
        if (!PState.set(old)) {
            Text.send(p, "<#f87171>⚔ <#e5e7eb>Savaş modundasın <#4b5563>· <#9ca3af>" + cs + " saniye komut kullanamazsın");
            Text.send(p, "<#f87171>⚔ <#9ca3af>Bu sürede çıkış yaparsan <#f87171>ölürsün");
            p.getWorld().playSound(p.getLocation(), "entity.wither.spawn", 0.3f, 1.6f);
        } else if (n > old) {
            Text.send(p, "<#f87171>⚔ <#e5e7eb>Savaş modundasın <#4b5563>· <#9ca3af>" + cs + " saniye komut kullanamazsın");
        }
    }

    boolean inCombat(Player p) {
        if (pvpm) {
            Object pv = pvpmPlayer(p);
            if (pv != null) {
                return Boolean.TRUE.equals(call(pv, "isInCombat"));
            }
        }
        PState s = ps(p);
        double until = s.combat;
        if (!PState.set(until)) {
            return false;
        }
        if (now() >= until) {
            s.combat = PState.NONE;
            return false;
        }
        return true;
    }

    double combatLeft(Player p) {
        double until = ps(p).combat;
        if (!PState.set(until)) {
            return 0;
        }
        return Math.max(0, until - now());
    }

    /** claim.sk inSokCombat: {-towers::combat::<uuid>} bitis ani (yoksa NaN). */
    public double combatUntil(UUID u) {
        PState s = players.get(u);
        return s == null ? PState.NONE : s.combat;
    }

    private void runCmdQueue() {
        if (cmdQueue.isEmpty()) {
            return;
        }
        for (String k : new ArrayList<>(cmdQueue.keySet())) {
            String name = cmdQueue.remove(k);
            if (name == null) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cfg.pvpmUntagCommand.replace("<player>", name));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cfg.pvpmTagCommand.replace("<player>", name));
        }
    }

    // ------------------------------------------------------------------ //
    //  ELEKTRIK AKIMI
    // ------------------------------------------------------------------ //

    boolean hasCurrent(UUID u) {
        PState s = players.get(u);
        if (s == null || !PState.set(s.current)) {
            return false;
        }
        if (now() - s.current > (cfg.currentTicks / 20.0) + 1) {
            s.current = PState.NONE;
            return false;
        }
        return true;
    }

    boolean anyCurrent() {
        for (PState s : players.values()) {
            if (PState.set(s.current)) {
                return true;
            }
        }
        return false;
    }

    void startCurrent(Player p) {
        ps(p).current = now();
        PotionEffect we = p.getPotionEffect(PotionEffectType.WITHER);
        if (we != null && !we.isAmbient()) {
            return;
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, cfg.currentTicks, cfg.currentAmp, true, false, true));
    }

    void stopCurrent(Player p) {
        PState s = ps(p);
        if (!PState.set(s.current)) {
            return;
        }
        s.current = PState.NONE;
        PotionEffect we = p.getPotionEffect(PotionEffectType.WITHER);
        if (we != null && we.isAmbient()) {
            p.removePotionEffect(PotionEffectType.WITHER);
        }
    }

    /** Oyuncu su an dusman bir kulenin menzilinde mi? (kayda guvenmez, hesaplar) */
    boolean inHostileRange(Player p) {
        if (bypass(p)) {
            return false;
        }
        Set<String> keys = store.nearbyRange(p.getLocation());
        if (keys.isEmpty()) {
            return false;
        }
        return !scan(p, keys, false).isEmpty();
    }

    // ------------------------------------------------------------------ //
    //  ISIN EFEKTI
    // ------------------------------------------------------------------ //

    void clearBeam(Player p) {
        PState s = ps(p);
        if (s.beams.isEmpty()) {
            return;
        }
        synchronized (s.beams) {
            for (Entity e : s.beams) {
                if (e != null && Bukkit.isOwnedByCurrentRegion(e)) {
                    e.remove();
                }
            }
            s.beams.clear();
        }
        s.beamtime = PState.NONE;
    }

    private void spawnGuardian(Location from, Player p) {
        Location start = new Location(from.getWorld(), from.getX() + cfg.beamX, from.getY() + cfg.beamHeight,
                from.getZ() + cfg.beamZ);
        Guardian g = from.getWorld().spawn(start, Guardian.class, gd -> {
            gd.setInvisible(true);
            gd.setSilent(true);
            gd.setInvulnerable(true);
            gd.setGravity(false);
            gd.setPersistent(false);
            gd.setRemoveWhenFarAway(true);
            gd.setCollidable(false);
            gd.setMetadata("sok_beam", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        });
        PState s = ps(p);
        synchronized (s.beams) {
            s.beams.add(g);
        }
        Location look = g.getLocation();
        look.setDirection(p.getEyeLocation().toVector().subtract(look.toVector()));
        g.setRotation(look.getYaw(), look.getPitch());
        g.setTarget(p);
        if (cfg.beamFreeze) {
            g.setAI(false);
        }
        g.setLaser(true);
    }

    void fireBeams(Player p, List<String> keys) {
        clearBeam(p);
        Location pLoc = p.getLocation();
        int made = 0;
        for (String k : keys) {
            Tower t = store.get(k);
            if (t == null) {
                continue;
            }
            Location tLoc = t.loc();
            if (cfg.beamMode.equals("guardian") && Bukkit.isOwnedByCurrentRegion(tLoc)) {
                spawnGuardian(tLoc, p);
            } else {
                shockBeam(tLoc, pLoc);
            }
            made++;
            if (made >= cfg.maxBeams) {
                break;
            }
        }
        if (made > 0 && cfg.beamMode.equals("guardian")) {
            ps(p).beamtime = now();
        }
    }

    void shockBeam(Location from, Location to) {
        World w = to.getWorld();
        double x1 = from.getX() + cfg.beamX;
        double y1 = from.getY() + cfg.beamHeight;
        double z1 = from.getZ() + cfg.beamZ;
        double x2 = to.getX();
        double y2 = to.getY() + 1.1;
        double z2 = to.getZ();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.3) {
            len = 0.3;
        }
        int steps = (int) Math.ceil(len / cfg.beamStep);
        steps = Math.max(1, Math.min(80, steps));

        w.spawnParticle(Particle.ELECTRIC_SPARK, x1, y1, z1, 10, 0, 0, 0, 0);
        w.spawnParticle(Particle.END_ROD, x1, y1, z1, 3, 0, 0, 0, 0);
        for (int n = 1; n <= steps; n++) {
            double t = (double) n / steps;
            double px = x1 + dx * t;
            double py = y1 + dy * t;
            double pz = z1 + dz * t;
            w.spawnParticle(Particle.END_ROD, px, py, pz, 1, 0, 0, 0, 0);
            if (n % 3 == 0) {
                w.spawnParticle(Particle.ELECTRIC_SPARK, px, py, pz, 1, 0, 0, 0, 0);
            }
            if (n % 8 == 0) {
                w.spawnParticle(Particle.ELECTRIC_SPARK, px + cfg.beamSpread, py + cfg.beamSpread, pz, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, px - cfg.beamSpread, py - cfg.beamSpread, pz, 1, 0, 0, 0, 0);
            }
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, x2, y2, z2, 25, 0, 0, 0, 0);
        w.spawnParticle(Particle.END_ROD, x2, y2, z2, 8, 0, 0, 0, 0);
        Location hit = new Location(w, x2, y2, z2);
        w.playSound(hit, "block.conduit.attack.target", 1f, 1.4f);
        w.playSound(hit, "entity.lightning_bolt.impact", 0.35f, 1.8f);
    }

    // ------------------------------------------------------------------ //
    //  HEDEFLEME
    // ------------------------------------------------------------------ //

    boolean canSee(Player p, Tower t) {
        if (cfg.seesThroughWalls(t.level)) {
            return true;
        }
        Location pLoc = p.getLocation();
        double cx = Math.floor(t.x) + 0.5;
        double cz = Math.floor(t.z) + 0.5;
        double cy = Math.floor(t.y) + 1.5;
        double dx = pLoc.getX() - cx;
        double dz = pLoc.getZ() - cz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.8) {
            return true;
        }
        Location target = new Location(t.world, cx + (dx / len) * 0.75, cy, cz + (dz / len) * 0.75);
        return p.hasLineOfSight(target);
    }

    /** Oyuncuyu vurabilecek TUM kuleler: dusman + menzilde + gorus hatti. Tek gecis. */
    List<String> scan(Player p, Set<String> keys, boolean dbg) {
        Location pLoc = p.getLocation();
        World w = pLoc.getWorld();
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            Tower t = store.get(k);
            if (t == null || t.world != w) {
                continue;
            }
            Location tLoc = t.loc();
            if (tLoc.distance(pLoc) > cfg.range(t.level)) {
                continue;
            }
            if (!isHostile(p, k)) {
                continue;
            }
            if (Bukkit.isOwnedByCurrentRegion(tLoc)) {
                if (!towerBlockOk(tLoc)) {
                    continue;
                }
                if (t.remodel) {
                    t.remodel = false;
                    applyModel(tLoc, t.level);
                }
            }
            if (!canSee(p, t)) {
                if (dbg) {
                    Text.actionBar(p, "<#4b5563>[ŞOK] <#9ca3af>Duvar arkasındasın <#4b5563>(Sv1 kule göremiyor)");
                }
                continue;
            }
            out.add(k);
        }
        return out;
    }

    double immDur(String u) {
        PState s = players.get(UUID.fromString(u));
        if (s != null && PState.set(s.immdur)) {
            return s.immdur;
        }
        return cfg.shockImmunity;
    }

    void candMark(String k, String u, double n) {
        ConcurrentHashMap<String, Double> m = tgt.computeIfAbsent(k, kk -> new ConcurrentHashMap<>());
        Double o = m.get(u);
        if (o != null) {
            if (n - o < 0.5) {
                return;
            }
        } else {
            ptgt.computeIfAbsent(u, uu -> ConcurrentHashMap.newKeySet()).add(k);
        }
        m.put(u, n);
    }

    void candDrop(String k, String u) {
        ConcurrentHashMap<String, Double> m = tgt.get(k);
        if (m == null || m.remove(u) == null) {
            return;
        }
        ConcurrentHashMap<String, Double> h = thit.get(k);
        if (h != null) {
            h.remove(u);
        }
        Set<String> s = ptgt.get(u);
        if (s != null) {
            s.remove(k);
        }
    }

    void candClear(String u) {
        Set<String> s = ptgt.remove(u);
        if (s == null) {
            return;
        }
        for (String k : s) {
            ConcurrentHashMap<String, Double> m = tgt.get(k);
            if (m != null) {
                m.remove(u);
            }
            ConcurrentHashMap<String, Double> h = thit.get(k);
            if (h != null) {
                h.remove(u);
            }
        }
    }

    boolean hasCand(String k, String u) {
        ConcurrentHashMap<String, Double> m = tgt.get(k);
        return m != null && m.containsKey(u);
    }

    /** Bu kule simdi bu oyuncuya ates etmeli mi? (en uzun suredir vurulmayan once) */
    boolean targetOk(String k, String u, double n) {
        ConcurrentHashMap<String, Double> h = thit.get(k);
        double my = h != null && h.get(u) != null ? h.get(u) : 0;
        ConcurrentHashMap<String, Double> m = tgt.get(k);
        if (m == null) {
            return true;
        }
        for (String o : new ArrayList<>(m.keySet())) {
            if (o.equals(u)) {
                continue;
            }
            Double seen = m.get(o);
            if (seen == null) {
                continue;
            }
            if (n - seen > cfg.targetWindow) {
                candDrop(k, o);
                continue;
            }
            PState os = players.get(UUID.fromString(o));
            if (os != null && PState.set(os.immune) && n - os.immune < immDur(o)) {
                continue;
            }
            double oh = h != null && h.get(o) != null ? h.get(o) : 0;
            if (oh < my) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ //
    //  SALDIRI MOTORU (oyuncunun kendi bolge thread'inde)
    // ------------------------------------------------------------------ //

    boolean shockCheck(Player p) {
        if (!p.isOnline()) {
            return false;
        }
        PState s = ps(p);
        if (PState.set(s.beamtime) && now() - s.beamtime > cfg.beamLife) {
            clearBeam(p);
        }
        boolean dbg = s.debug;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) {
            return false;
        }
        if (p.isDead()) {
            return false;
        }
        Location pLoc = p.getLocation();
        Set<String> keys = store.nearbyRange(pLoc);
        if (keys.isEmpty()) {
            if (dbg) {
                Text.actionBar(p, "<#4b5563>[ŞOK] <#f87171>Yakında kule yok");
            }
            return false;
        }
        if (cfg.bypassStaff) {
            if (p.isOp()) {
                if (dbg) {
                    Text.actionBar(p, "<#4b5563>[ŞOK] <#fbbf24>OP olduğun için muafsın");
                }
                return false;
            }
            if (p.hasPermission("claim.admin")) {
                return false;
            }
        }
        if (bypass(p)) {
            if (dbg) {
                Text.actionBar(p, "<#4b5563>[ŞOK] <#fbbf24>Bypass yetkin var, kuleler vurmaz");
            }
            return false;
        }
        double n = now();
        String u = p.getUniqueId().toString();
        if (PState.set(s.immune) && n - s.immune < immDur(u)) {
            return true;
        }

        List<String> vis = scan(p, keys, dbg);
        if (vis.isEmpty()) {
            for (String k : keys) {
                if (hasCand(k, u)) {
                    candDrop(k, u);
                }
            }
            if (dbg) {
                Text.actionBar(p, "<#4b5563>[ŞOK] <#9ca3af>Menzil dışındasın");
            }
            return false;
        }
        Set<String> vs = new HashSet<>(vis);
        for (String k : keys) {
            if (!vs.contains(k) && hasCand(k, u)) {
                candDrop(k, u);
            }
        }
        for (String k : vis) {
            candMark(k, u, n);
        }

        if (!PState.set(s.current) || n - s.current > 2) {
            startCurrent(p);
        }

        int count = vis.size();
        String best = "";
        int bestLvl = 0;
        double bestLa = 0;
        double minWait = -1;
        for (String k : vis) {
            Tower t = store.get(k);
            if (t == null) {
                continue;
            }
            int lvl = t.level;
            double la = t.lastAttack;
            double wait = cfg.cooldown(lvl) - (n - la);
            if (wait > 0) {
                if (minWait < 0 || wait < minWait) {
                    minWait = wait;
                }
                continue;
            }
            if (!targetOk(k, u, n)) {
                continue;
            }
            boolean pick = best.isEmpty() || lvl > bestLvl || (lvl == bestLvl && la < bestLa);
            if (pick) {
                best = k;
                bestLvl = lvl;
                bestLa = la;
            }
        }
        if (best.isEmpty()) {
            if (dbg) {
                if (minWait > 0) {
                    Text.actionBar(p, "<#4b5563>[ŞOK] <#9ca3af>Bekleme: <#e5e7eb>" + Text.num(Math.round(minWait * 10) / 10.0) + "s");
                } else {
                    Text.actionBar(p, "<#4b5563>[ŞOK] <#9ca3af>Sıra diğer oyuncuda");
                }
            }
            return true;
        }
        Tower bt = store.get(best);
        bt.lastAttack = n;
        thit.computeIfAbsent(best, kk -> new ConcurrentHashMap<>()).put(u, n);
        doShock(p, best, count, vis);
        if (dbg) {
            Text.actionBar(p, "<#4b5563>[ŞOK] <#4ade80>VURULDU! <#9ca3af>(Sv" + bestLvl + ")");
        }
        return true;
    }

    /** Sok darbesi. HER ZAMAN hedef oyuncunun kendi bolge thread'inde. */
    void doShock(Player p, String locStr, int n, List<String> keys) {
        if (!p.isOnline()) {
            return;
        }
        Tower tw = store.get(locStr);
        if (tw == null) {
            return;
        }
        int lvl = tw.level;
        int cnt = Math.max(1, n);

        double base = cfg.damage(lvl);
        double sum = 0;
        for (String k : keys) {
            if (k.equals(locStr)) {
                continue;
            }
            Tower o = store.get(k);
            int ol = o == null ? 1 : o.level;
            if (ol >= cfg.stackMinLevel) {
                sum += cfg.damage(ol);
            }
        }
        double extra = 0;
        if (cfg.stackGrowth > 0) {
            if (sum > 0) {
                extra = cfg.stackGrowth * Math.log(1 + sum / cfg.stackRef) / Math.log(2);
            }
        } else {
            extra = base * cfg.stackBonus * (cnt - 1) + sum * cfg.stackSupport;
            double maxExtra = cfg.stackMaxDamage > 0 ? cfg.stackMaxDamage - base : base * (cfg.stackCap - 1);
            if (maxExtra < 0) {
                maxExtra = 0;
            }
            if (extra > maxExtra) {
                extra = maxExtra;
            }
        }
        if (Double.isNaN(extra) || extra < 0) {
            extra = 0;
        }
        double dmg = base + extra;
        double mult = dmg / base;

        double trueD = dmg * (cfg.trueDamagePercent / 100);
        double normal = (dmg - trueD) * TowerMath.armorFactor(p, dmg);

        int epf = TowerMath.epf(p);
        double pr = TowerMath.protRel(cfg, epf, cfg.protTowerP5, cfg.protTowerStep);
        double protMul = TowerMath.protFix(cfg, epf, cfg.protTowerP5, cfg.protTowerStep);
        if (cfg.protDouble) {
            int e16 = Math.min(16, epf);
            protMul = protMul * (1 - e16 * 0.04);
        }
        normal = normal * protMul;
        if (pr < 1) {
            trueD = trueD * pr;
        }

        double red = emici.reduction(p);
        if (Double.isNaN(red)) {
            red = 0;
        }
        if (red > 0) {
            red = red * cfg.emiciTowerMult;
            if (red > cfg.emiciTowerMax) {
                red = cfg.emiciTowerMax;
            }
        }
        if (red > 1) {
            red = 1;
        }
        if (red > 0) {
            normal = normal * (1 - red);
            trueD = trueD * (1 - red);
        }

        fireBeams(p, keys);

        PState s = ps(p);
        // Kule hasari dokunulmazlik penceresi ACMAZ ve var olani bozmaz
        int ndt = p.getNoDamageTicks();
        double ldm = p.getLastDamage();
        if (normal > 0) {
            p.setNoDamageTicks(0);
            boolean kdbg = s.debug;
            if (kdbg) {
                s.kdmg = true;
                kdmgAny = true;
            }
            p.damage(normal * 2);
            if (kdbg) {
                s.kdmg = false;
                kdmgAny = false;
            }
            if (!p.isDead()) {
                p.setNoDamageTicks(ndt);
                p.setLastDamage(ldm);
                if (dmgTest) {
                    Text.console("[HASAR-TEST] KULE vurdu -> " + p.getName() + " · önce dokunulmazlık=" + ndt
                            + " sonHasar=" + Text.num(ldm) + " · sonra dokunulmazlık=" + p.getNoDamageTicks()
                            + " sonHasar=" + Text.num(p.getLastDamage()));
                }
            }
        }
        // Zirh delen pay: dogrudan can eksiltme (1 kalp = 2 can puani)
        if (trueD > 0 && !p.isDead()) {
            double hearts = trueD / 2;
            if (p.getHealth() / 2 > hearts) {
                p.setHealth(p.getHealth() - hearts * 2);
            } else {
                p.damage(2000);
            }
        }
        // Oyuncuyu oldurduyse savas etiketi / akim tekrar baglanmaz
        if (p.isDead()) {
            return;
        }
        s.immune = now();
        s.immdur = cfg.immunity(lvl);
        combatTag(p);
        startCurrent(p);

        if (s.debug) {
            double pf = TowerMath.protFactor(p);
            double nk = normal * pf;
            double pfAll = protMul * pf;
            double toplam = Math.round((nk + trueD / 2) * 100) / 100.0;
            Text.send(p, "<#4b5563>[ŞOK] <#9ca3af>yığın <#e5e7eb>" + cnt + " kule <#4b5563>· <#9ca3af>çarpan <#e5e7eb>x"
                    + Text.num(Math.round(mult * 100) / 100.0) + " <#4b5563>· <#9ca3af>P4 üstü koruma -%"
                    + Math.round((1 - pr) * 100));
            Text.send(p, "<#4b5563>[ŞOK] <#9ca3af>ham <#e5e7eb>" + Text.num(dmg) + " <#4b5563>· <#9ca3af>zırh geçen %"
                    + Math.round(TowerMath.armorFactor(p, dmg) * 100) + " <#4b5563>· <#9ca3af>koruma geçen %"
                    + Text.num(Math.round(pfAll * 100 * 10) / 10.0) + " <#4b5563>· <#9ca3af>emici -%" + Math.round(red * 100));
            Text.send(p, "<#4b5563>[ŞOK] <#9ca3af>normal <#e5e7eb>" + Text.num(Math.round(normal * 100) / 100.0)
                    + " <#4b5563>→ <#9ca3af>koruma sonrası <#e5e7eb>" + Text.num(Math.round(nk * 100) / 100.0)
                    + " <#4b5563>· <#9ca3af>delen <#e5e7eb>" + Text.num(Math.round((trueD / 2) * 100) / 100.0)
                    + " <#4b5563>· <#9ca3af>tahmini toplam <#f87171>" + Text.num(toplam) + " kalp");
        }
        if (cnt > 1) {
            Text.actionBar(p, "<#5ac8fa>⚡ <#e5e7eb>Şok kulesi <#4b5563>· <#9ca3af>" + cnt + " kule menzilde");
        } else {
            Text.actionBar(p, "<#5ac8fa>⚡ <#e5e7eb>Şok kulesi <#4b5563>· <#9ca3af>elektrik akımına kapıldın");
        }
    }

    // ------------------------------------------------------------------ //
    //  KAZMA / KIRMA
    // ------------------------------------------------------------------ //

    static int pickTier(Player p) {
        Material m = p.getInventory().getItemInMainHand().getType();
        return switch (m) {
            case NETHERITE_PICKAXE -> 3;
            case DIAMOND_PICKAXE -> 2;
            case IRON_PICKAXE -> 1;
            default -> 0;
        };
    }

    static int hasteLevel(Player p) {
        PotionEffect e = p.getPotionEffect(PotionEffectType.HASTE);
        return e == null ? 0 : e.getAmplifier() + 1;
    }

    double mineTime(Player p) {
        int tier = pickTier(p);
        double base = tier == 3 ? cfg.mineTimeNetherite : tier == 2 ? cfg.mineTimeDiamond : cfg.mineTimeIron;
        ItemStack tool = p.getInventory().getItemInMainHand();
        int eff = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        double cut = Math.min(eff * cfg.effBonusPerLevel, cfg.effBonusMax);
        double hcut = Math.min(hasteLevel(p) * cfg.hasteBonusPerLevel, cfg.hasteBonusMax);
        return base * (1 - cut) * (1 - hcut);
    }

    /** Kulenin GUNCEL cani (uzun suredir kazilmadiysa tamamen dolar). */
    int curHp(Tower t) {
        int max = cfg.maxHp();
        int hp = t.hp;
        if (hp < 1) {
            hp = max;
            t.hp = max;
        }
        if (hp > max) {
            hp = max;
        }
        if (hp < max && cfg.hpRegenSeconds > 0) {
            if (!PState.set(t.hitAt) || now() - t.hitAt > cfg.hpRegenSeconds) {
                hp = max;
                t.hp = max;
                store.markDirty(t.fcid);
            }
        }
        return hp;
    }

    void bar(Player p, Tower t, double hp, double max, double frac) {
        PState s = ps(p);
        String title;
        if (max > 1) {
            title = "§b⚡ §fŞok Kulesi §7Sv" + t.level + " §8· §cCan " + Math.round(hp) + "§7/" + Math.round(max);
        } else {
            title = "§b⚡ §fŞok Kulesi §7Sv" + t.level + " §8· §cKırılıyor §f%" + Math.round(frac * 100);
        }
        double prog = Math.max(0, Math.min(1, (hp - frac) / max));
        BossBar b = s.bar;
        if (b == null) {
            b = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
            b.addPlayer(p);
            s.bar = b;
        } else {
            b.setTitle(title);
        }
        b.setProgress(prog);
        b.setVisible(true);
        s.barT = now();
    }

    void barHide(PState s) {
        BossBar b = s.bar;
        if (b != null) {
            b.setVisible(false);
        }
        s.barT = PState.NONE;
    }

    void markDigProof(Player p) {
        PState s = ps(p);
        if (s.holding) {
            s.sig = now();
        }
    }

    /** Yabanci oyuncunun kazma ilerlemesi (sok.sk towerRaidTick). */
    void raidTick(Player p, Location loc) {
        String key = Tower.keyOf(loc);
        Tower t = store.get(key);
        if (t == null) {
            return;
        }
        PState s = ps(p);
        if (!s.holding || !key.equals(s.tgt)) {
            return;
        }
        String fcidHere = Tower.chunkId(loc);
        double prot = claims.chunkProtectLeft(fcidHere);
        if (prot > 0) {
            s.prog = PState.NONE;
            if (claims.newProtectLeft(fcidHere) > 0) {
                Text.actionBar(p, "<#a8ff78>🛡 Yeni claim koruması <#4b5563>· <#9ca3af>şok kulesi kırılamaz <#4b5563>· <#fbbf24>" + claims.fmtDur(prot));
            } else {
                Text.actionBar(p, "<#7dd3fc>🌙 Gece koruması <#4b5563>· <#9ca3af>şok kulesi kırılamaz <#4b5563>· <#fbbf24>" + claims.fmtDur(prot));
            }
            return;
        }
        if (!PState.set(s.sig)) {
            return;
        }
        if (now() - s.sig > 2.0) {
            s.prog = PState.NONE;
            s.last = PState.NONE;
            return;
        }
        if (!towerBlockOk(loc)) {
            unregister(key);
            clearTop(loc);
            return;
        }
        if (t.orphan) {
            return;
        }
        String mainCid = t.cid;
        if (isMemberOf(p, mainCid)) {
            return;
        }
        if (staffRemove(p, mainCid)) {
            s.prog = PState.NONE;
            barHide(s);
            return;
        }
        double dist = p.getLocation().distance(loc);
        if (dist > cfg.mineMaxDistance) {
            if (s.debug) {
                Text.actionBar(p, "<#4b5563>[KAZI] <#f87171>Çok uzaksın <#4b5563>· <#e5e7eb>" + Text.num(dist) + "/" + Text.num(cfg.mineMaxDistance) + " blok");
            }
            return;
        }
        if (!claims.isRaidActive()) {
            Text.actionBar(p, "<#fbbf24>⚠ <#e5e7eb>Koruma aktif <#4b5563>· <#9ca3af>kule sadece baskın saatlerinde kırılabilir");
            return;
        }
        if (pickTier(p) < 1) {
            Text.actionBar(p, "<#f87171>Kırmak için en az <#e5e7eb>Demir Kazma <#f87171>gerekir!");
            return;
        }

        double n = now();
        double last = s.last;
        double prog = PState.set(s.prog) ? s.prog : 0;
        String tg = s.tgt;
        if (!PState.set(last)) {
            prog = 0;
        } else if (!key.equals(tg)) {
            prog = 0;
        } else if (n - last > cfg.mineReset) {
            prog = 0;
        } else {
            prog = prog + Math.min(0.25, n - last);
        }
        s.last = n;
        s.tgt = key;

        double need = mineTime(p);
        int hp = curHp(t);
        int maxHp = cfg.maxHp();
        t.hitAt = n;

        if (prog < need) {
            s.prog = prog;
            bar(p, t, hp, maxHp, prog / need);
            return;
        }
        prog = prog - need;
        s.prog = prog;
        hp = hp - 1;
        t.hp = hp;
        store.markDirty(t.fcid);
        loc.getWorld().playSound(loc, "block.chain.break", 1f, 0.8f);
        if (hp > 0) {
            bar(p, t, hp, maxHp, 0);
            return;
        }
        s.prog = PState.NONE;
        barHide(s);
        int dropLvl = t.level;
        claims.clog(mainCid, "&e" + p.getName() + " &cşok kulesini kırdı &8(Sv" + dropLvl + ", " + claims.ltxt(loc) + ")");
        claims.alog("KULE", "BASKIN_KIRMA " + p.getName() + "(" + p.getUniqueId() + ") claim=" + mainCid
                + " seviye=" + dropLvl + " loc=" + claims.altxt(loc));
        unregister(key);
        clearTop(loc);
        loc.getBlock().setType(Material.AIR);
        loc.getWorld().dropItemNaturally(loc, items.item(dropLvl));
        Text.send(p, cfg.prefix + " <#f87171>Şok kulesi yıkıldı <#4b5563>· <#9ca3af>seviye " + dropLvl + " kule düştü");
        p.getWorld().playSound(p.getLocation(), "entity.generic.explode", 1f, 1.5f);
    }

    /** Kuleye vurus (sok.sk towerHit). loc her zaman kulenin ALT blogu. */
    void hit(Player p, Location loc) {
        String key = Tower.keyOf(loc);
        Tower t = store.get(key);
        if (t == null) {
            return;
        }
        // DUPE ONLEME: kule esyasi SADECE kulenin blogu gercekten yerindeyse duser
        if (!towerBlockOk(loc)) {
            unregister(key);
            clearTop(loc);
            return;
        }
        String mainCid = t.cid;
        int dropLvl = t.level;

        if (t.orphan && !claimGone(t)) {
            t.orphan = false;
            t.orphanAt = PState.NONE;
        }
        if (t.orphan) {
            double oWait = cfg.orphanGraceSeconds;
            if (PState.set(t.orphanAt)) {
                oWait = cfg.orphanGraceSeconds - (now() - t.orphanAt);
            }
            if (oWait > 0) {
                Text.actionBar(p, "<#9ca3af>Bu kulenin claim'i yeni kalktı <#4b5563>· <#e5e7eb>" + (long) Math.ceil(oWait) + " sn <#9ca3af>sonra sahipsiz olur");
                return;
            }
            if (claims.owner(Tower.chunkId(loc)) != null && !claims.canInteract(p, loc, "break")) {
                Text.actionBar(p, "<#f87171>Bu kule başka bir claim'in içinde <#4b5563>· <#9ca3af>kırma yetkin yok");
                return;
            }
            claims.alog("KULE", "SAHIPSIZ_SOKME " + p.getName() + "(" + p.getUniqueId() + ") seviye=" + dropLvl + " loc=" + claims.altxt(loc));
            unregister(key);
            clearTop(loc);
            loc.getBlock().setType(Material.AIR);
            loc.getWorld().dropItemNaturally(loc, items.item(dropLvl));
            p.getWorld().playSound(p.getLocation(), "block.beacon.deactivate", 0.8f, 1.2f);
            Text.send(p, cfg.prefix + " <#9ca3af>Sahipsiz bir şok kulesi söktün.");
            return;
        }

        boolean isOwner = claims.canBreakTower(p, mainCid);
        if (isOwner && !isMemberOf(p, mainCid)) {
            isOwner = staffRemove(p, mainCid);
        }
        if (!isOwner && claims.isMember(mainCid, p.getUniqueId().toString())) {
            Text.actionBar(p, "<#f87171>Kuleyi sökmek için claim'de <#e5e7eb>Spawner / Kule Sökme <#f87171>yetkin olmalı");
            return;
        }
        if (isOwner) {
            PState s = ps(p);
            s.clearMine();
            barHide(s);
            claims.clog(mainCid, "&e" + p.getName() + " &7şok kulesini söktü &8(Sv" + dropLvl + ", " + claims.ltxt(loc) + ")");
            claims.alog("KULE", "SOKME " + p.getName() + "(" + p.getUniqueId() + ") claim=" + mainCid + " seviye=" + dropLvl + " loc=" + claims.altxt(loc));
            unregister(key);
            clearTop(loc);
            loc.getBlock().setType(Material.AIR);
            loc.getWorld().dropItemNaturally(loc, items.item(dropLvl));
            Text.send(p, cfg.prefix + " <#4ade80>Şok Kulesi başarıyla söküldü.");
            return;
        }
        raidTick(p, loc);
    }

    // ------------------------------------------------------------------ //
    //  BAKIM (10 sn, global; sadece degisken)
    // ------------------------------------------------------------------ //

    private void maintenance() {
        claimsReady = claims.dataReady();
        store.activateStash();
        double n = now();
        for (Tower t : new ArrayList<>(store.towers.values())) {
            if (claimGone(t)) {
                if (!t.orphan) {
                    t.orphan = true;
                    t.orphanAt = n;
                } else if (!PState.set(t.orphanAt)) {
                    t.orphanAt = n;
                }
            } else if (t.orphan) {
                t.orphan = false;
                t.orphanAt = PState.NONE;
            }
        }
        // Hasar testi 5 dk sonra kendiliginden kapanir
        if (dmgTest && PState.set(dmgTestAt) && n - dmgTestAt > 300) {
            dmgTest = false;
            dmgTestAt = PState.NONE;
            Text.console(cfg.prefix + " <#9ca3af>Hasar testi 5 dk dolduğu için kapandı.");
        }
    }

    // ------------------------------------------------------------------ //
    //  BILGI REHBERI (bilgi.sk)
    // ------------------------------------------------------------------ //

    void publishInfo() {
        if (!SkriptBridge.available()) {
            return;
        }
        for (int n = 1; n <= 6; n++) {
            SkriptBridge.set("-bilgi::api::sok_lvl" + n + "_kalp", Text.num(cfg.hearts(n)));
            SkriptBridge.set("-bilgi::api::sok_lvl" + n + "_menzil", Text.num(cfg.range(n)));
            SkriptBridge.set("-bilgi::api::sok_lvl" + n + "_bekleme", Text.num(cfg.cooldown(n)));
            SkriptBridge.set("-bilgi::api::sok_lvl" + n + "_can", Text.num(cfg.towerHp));
            SkriptBridge.set("-bilgi::api::sok_lvl" + n + "_ucret", Text.num(n == 1 ? cfg.shopPrice : cfg.cost(n)));
        }
        SkriptBridge.set("-bilgi::api::sok_fiyat", Text.num(cfg.shopPrice));
        SkriptBridge.set("-bilgi::api::sok_duvar_seviye", Text.num(cfg.wallLevel));
        SkriptBridge.set("-bilgi::api::sok_zirh_delen", Text.num(cfg.trueDamagePercent));
        SkriptBridge.set("-bilgi::api::sok_yigin_artis", Text.num(Math.round(cfg.stackBonus * 100)));
        SkriptBridge.set("-bilgi::api::sok_yigin_tavan", Text.num(Math.round((cfg.stackCap - 1) * 100)));
        SkriptBridge.set("-bilgi::api::sok_yigin_destek", Text.num(Math.round(cfg.stackSupport * 100)));
        SkriptBridge.set("-bilgi::api::sok_koruma_ustu", Text.num(cfg.protTowerStep));
        SkriptBridge.set("-bilgi::api::sok_koruma_p5", Text.num(cfg.protTowerP5));
        SkriptBridge.set("-bilgi::api::sok_koruma_adim", Text.num(cfg.protTowerStep));
        SkriptBridge.set("-bilgi::api::sok_pvp_koruma_p5", Text.num(cfg.protPvpP5));
        SkriptBridge.set("-bilgi::api::sok_pvp_koruma_adim", Text.num(cfg.protPvpStep));
        SkriptBridge.set("-bilgi::api::sok_yigin_max", cfg.stackGrowth > 0 ? "sınırsız" : Text.num(cfg.stackMaxDamage));
        SkriptBridge.set("-bilgi::api::sok_yigin_katlama", Text.num(cfg.stackGrowth));
        SkriptBridge.set("-bilgi::api::sok_emici_kule", Text.num(cfg.emiciTowerMult));
        SkriptBridge.set("-bilgi::api::sok_bagisiklik", Text.num(cfg.shockImmunity));
        SkriptBridge.set("-bilgi::api::sok_savas_sure", Text.num(cfg.combatSeconds));
        SkriptBridge.set("-bilgi::api::sok_kazma_mesafe", Text.num(cfg.mineMaxDistance));
        int hpAll = cfg.maxHp();
        SkriptBridge.set("-bilgi::api::sok_kazma_demir", Text.num(cfg.mineTimeIron * hpAll));
        SkriptBridge.set("-bilgi::api::sok_kazma_elmas", Text.num(cfg.mineTimeDiamond * hpAll));
        SkriptBridge.set("-bilgi::api::sok_kazma_netherite", Text.num(cfg.mineTimeNetherite * hpAll));
        SkriptBridge.set("-bilgi::api::sok_can", Text.num(hpAll));
        SkriptBridge.set("-bilgi::api::sok_verim_max", Text.num(Math.round(cfg.effBonusMax * 100)));
        SkriptBridge.set("-bilgi::api::sok_acele_max", Text.num(Math.round(cfg.hasteBonusMax * 100)));
        SkriptBridge.set("-bilgi::api::sok_max_kule", cfg.maxTowersPerClaim > 0 ? Text.num(cfg.maxTowersPerClaim) : "sınırsız");
        SkriptBridge.set("-bilgi::api::sok_can_dolma", Text.num(Math.round(cfg.hpRegenSeconds / 60)));
        SkriptBridge.set("-bilgi::api::sok_ornek_sure", Text.num(Math.round(hpAll * cfg.mineTimeNetherite)));
        SkriptBridge.set("-bilgi::cache::*", null);
        SkriptBridge.set("-bilgi::icon::*", null);
        SkriptBridge.set("-bilgi::resolver", null);
    }

    // ------------------------------------------------------------------ //
    //  DIS ERISIM (claim modulu)
    // ------------------------------------------------------------------ //

    /** claim.sk towerNearLoc icin: bu konumda kayitli kule var mi? */
    public boolean isTowerAt(World w, int x, int y, int z) {
        return store.isTowerKey(w.getName() + "_" + x + "_" + y + "_" + z);
    }

    /** claim silinince ilgili kule dosyasi yeniden yazilsin (sok.sk dirtyfcids). */
    public void markFcidDirty(String fcid) {
        store.markDirty(fcid);
    }

    public Map<String, Integer> countByCid() {
        Map<String, Integer> m = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : store.byCid.entrySet()) {
            m.put(e.getKey(), e.getValue().size());
        }
        return m;
    }
}
