package tr.dugecraft.claim;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import tr.dugecraft.DugeCraft;
import tr.dugecraft.tower.TowerModule;
import tr.dugecraft.util.Econ;
import tr.dugecraft.util.Gui;
import tr.dugecraft.util.SkriptBridge;
import tr.dugecraft.util.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * claim.sk v15.16'nin plugin karsiligi (cekirdek: veri, yetki, enerji, koruma,
 * log, ciftci fiyat/satis, claim tasi). Menuler, tiklamalar, sohbet girdileri,
 * olaylar ve komutlar ayri siniflarda.
 */
public final class ClaimModule implements ClaimApi {

    final DugeCraft plugin;
    final ClaimConfig cfg;
    final ClaimStore store;
    final Persist persist;
    volatile TowerModule towers;

    /** {-fprod}: "mc|k" -> bu dakika toplanan */
    final ConcurrentHashMap<String, AtomicLong> fprod = new ConcurrentHashMap<>();
    /** {-frate}: "mc|k" -> dakikalik yumusatilmis hiz */
    final ConcurrentHashMap<String, Double> frate = new ConcurrentHashMap<>();
    volatile double fprodLast = Double.NaN;
    /** {-fpay}: uuid -> kurus */
    final ConcurrentHashMap<String, AtomicLong> fpay = new ConcurrentHashMap<>();
    /** {-alog_pay::<uuid>}: 5 dk'lik oto satis toplami */
    final ConcurrentHashMap<String, Double> alogPay = new ConcurrentHashMap<>();
    /** {-creserve}: kisa sureli islem kilitleri */
    final ConcurrentHashMap<String, String> creserve = new ConcurrentHashMap<>();
    /** {-alogq}: admin log kuyrugu */
    final ConcurrentLinkedQueue<String> alogq = new ConcurrentLinkedQueue<>();
    /** {-f_prices}, {-f_price_src} */
    final ConcurrentHashMap<String, Double> fPrices = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, String> fPriceSrc = new ConcurrentHashMap<>();
    final AtomicLong fpriceVer = new AtomicLong();
    /** {-as_chk::<mc>} */
    final ConcurrentHashMap<String, Double> asChk = new ConcurrentHashMap<>();
    /** {-lowenergy::<mc>} */
    final java.util.Set<String> lowEnergy = ConcurrentHashMap.newKeySet();
    /** {-stones::chunk::<ck>::<mc>} */
    final ConcurrentHashMap<String, java.util.Set<String>> stonesByChunk = new ConcurrentHashMap<>();
    /** {-stone_cleanup::<ck>::<mc>} = eski tas konumu */
    final ConcurrentHashMap<String, ConcurrentHashMap<String, Location>> stoneCleanup = new ConcurrentHashMap<>();
    /** {-prot_msg::<uuid>} */
    final ConcurrentHashMap<String, Double> protMsg = new ConcurrentHashMap<>();
    /** Oyuncu basina menu / girdi durumu (Skript metadata + {-...::<uuid>}) */
    final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    private ZoneId nightZone;
    private final List<ScheduledTask> tasks = new ArrayList<>();
    volatile boolean enabled;

    public ClaimModule(DugeCraft plugin, ClaimConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.store = new ClaimStore(this);
        this.persist = new Persist(new File(plugin.getDataFolder(), "claim_kalici.yml"));
        try {
            nightZone = ZoneId.of(cfg.nightZone);
        } catch (RuntimeException e) {
            nightZone = ZoneId.systemDefault();
        }
    }

    public void attachTowers(TowerModule t) {
        this.towers = t;
    }

    static double now() {
        return System.currentTimeMillis() / 1000.0;
    }

    Session session(Player p) {
        return sessions.computeIfAbsent(p.getUniqueId(), u -> new Session());
    }

    static String uuid(Player p) {
        return p.getUniqueId().toString();
    }

    void send(org.bukkit.command.CommandSender to, String msg) {
        Text.send(to, msg);
    }

    void pre(org.bukkit.command.CommandSender to, String msg) {
        Text.send(to, cfg.prefix + " " + msg);
    }

    /** Skript "play sound X with volume V and pitch P to player" */
    static void sound(Player p, String key, float vol, float pitch) {
        p.playSound(p.getLocation(), key, vol, pitch);
    }

    /** Skript "play sound ... at location" (herkes duyar) */
    static void soundAt(Location l, String key, float vol, float pitch) {
        l.getWorld().playSound(l, key, vol, pitch);
    }

    // ------------------------------------------------------------------ //
    //  KIMLIK / CID
    // ------------------------------------------------------------------ //

    static String chunkId(Location l) {
        return l.getWorld().getName() + "_" + (long) Math.floor(l.getX() / 16) + "_" + (long) Math.floor(l.getZ() / 16);
    }

    static Integer cidZ(String cid) {
        String[] sp = cid.split("_", -1);
        try {
            return Integer.parseInt(sp[sp.length - 1]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Integer cidX(String cid) {
        String[] sp = cid.split("_", -1);
        if (sp.length < 2) {
            return null;
        }
        try {
            return Integer.parseInt(sp[sp.length - 2]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String cidWorld(String cid) {
        String[] sp = cid.split("_", -1);
        StringBuilder w = new StringBuilder();
        for (int i = 0; i < sp.length - 2; i++) {
            if (w.length() > 0) {
                w.append('_');
            }
            w.append(sp[i]);
        }
        return w.toString();
    }

    Claim claim(String cid) {
        return store.get(cid);
    }

    String mainOf(String cid) {
        Claim c = store.get(cid);
        if (c == null || c.mainCid == null) {
            return cid;
        }
        return c.mainCid;
    }

    boolean isOwner(Player p, String cid) {
        Claim c = store.get(cid);
        return c != null && uuid(p).equals(c.owner);
    }

    boolean isMemberUuid(String mainCid, String u) {
        Claim c = store.get(mainCid);
        return c != null && c.members.contains(u);
    }

    boolean permOf(String mainCid, String u, String perm) {
        Claim c = store.get(mainCid);
        return c != null && c.perm(u, perm);
    }

    // ------------------------------------------------------------------ //
    //  ClaimApi
    // ------------------------------------------------------------------ //

    @Override
    public boolean dataReady() {
        return !store.all.isEmpty();
    }

    @Override
    public String owner(String cid) {
        return store.owner(cid);
    }

    @Override
    public String mainCidRaw(String cid) {
        Claim c = store.get(cid);
        return c == null ? null : c.mainCid;
    }

    @Override
    public String ownerName(String cid) {
        Claim c = store.get(cid);
        return c == null ? null : c.ownerName;
    }

    @Override
    public boolean isMember(String mainCid, String u) {
        return isMemberUuid(mainCid, u);
    }

    static boolean isKurucu(Player p) {
        return p.hasPermission("claim.kurucu");
    }

    @Override
    public boolean canInteract(Player p, Location loc, String action) {
        if (p.isOp() || p.hasPermission("claim.admin") || isKurucu(p)) {
            return true;
        }
        String cid = chunkId(loc);
        Claim c = store.get(cid);
        if (c == null || c.owner == null) {
            return true;
        }
        String u = uuid(p);
        if (u.equals(c.owner)) {
            return true;
        }
        if (!action.equals("spawner_break")) {
            String pmc = c.mainOrSelf();
            if (chunkProtectLeft(cid) > 0) {
                return isMemberUuid(pmc, u);
            }
        }
        if (!action.equals("spawner_break") && isRaidActive()) {
            return true;
        }
        // Skript: {-claims::<main_cid>::members::*} (main_cid set degilse "<none>")
        String mc = c.mainCid;
        if (mc != null && isMemberUuid(mc, u) && permOf(mc, u, action)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean canBreakTower(Player p, String mainCid) {
        if (p.isOp() || isKurucu(p)) {
            return true;
        }
        String u = uuid(p);
        Claim c = store.get(mainCid);
        if (c != null && u.equals(c.owner)) {
            return true;
        }
        if (isMemberUuid(mainCid, u)) {
            return permOf(mainCid, u, "spawner_break") || permOf(mainCid, u, "break");
        }
        return false;
    }

    @Override
    public boolean canManageTower(Player p, String mainCid) {
        if (p.isOp() || isKurucu(p)) {
            return true;
        }
        String u = uuid(p);
        Claim c = store.get(mainCid);
        if (c != null && u.equals(c.owner)) {
            return true;
        }
        return isMemberUuid(mainCid, u);
    }

    boolean canUseFarmer(Player p, String mainCid) {
        Claim c = store.get(mainCid);
        if (c == null || c.owner == null) {
            return false;
        }
        String u = uuid(p);
        if (u.equals(c.owner) || isKurucu(p)) {
            return true;
        }
        return c.members.contains(u) && c.perm(u, "farmer");
    }

    @Override
    public boolean isRaidActive() {
        return true;
    }

    @Override
    public String forceRaid() {
        return null;
    }

    @Override
    public String fmtDur(double sec) {
        if (sec < 0) {
            sec = 0;
        }
        long d = (long) Math.floor(sec / 86400);
        long h = (long) Math.floor((sec % 86400) / 3600);
        long mi = (long) Math.floor((sec % 3600) / 60);
        if (d > 0) {
            return d + " gün " + h + " sa";
        }
        if (h > 0) {
            return h + " sa " + mi + " dk";
        }
        return mi + " dk";
    }

    @Override
    public boolean reserve(String key, String u) {
        double n = now();
        String val = u + "|" + n;
        String prev = creserve.putIfAbsent(key, val);
        if (prev == null) {
            return true;
        }
        String[] pp = prev.split("\\|");
        try {
            if (n - Double.parseDouble(pp[pp.length - 1]) > 10) {
                return creserve.replace(key, prev, val);
            }
        } catch (NumberFormatException ignored) {
            return creserve.replace(key, prev, val);
        }
        return false;
    }

    @Override
    public void release(String key) {
        creserve.remove(key);
    }

    @Override
    public String ltxt(Location l) {
        return (long) Math.floor(l.getX()) + ", " + (long) Math.floor(l.getY()) + ", " + (long) Math.floor(l.getZ());
    }

    @Override
    public String altxt(Location l) {
        return l.getWorld().getName() + " " + ltxt(l);
    }

    @Override
    public void alog(String cat, String msg) {
        String t = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        alogq.add("[" + t + "] [" + cat + "] " + msg);
    }

    @Override
    public void clog(String mainCid, String msg) {
        LinkedBlockingDeque<String> d = store.clog.computeIfAbsent(mainCid, k -> new LinkedBlockingDeque<>(150));
        String t = new SimpleDateFormat("dd.MM HH:mm").format(new Date());
        String line = "&8" + t + " &7› " + msg;
        if (!d.offerLast(line)) {
            d.pollFirst();
            d.offerLast(line);
        }
        Claim c = store.get(mainCid);
        if (c != null) {
            c.needsSave = true;
        }
    }

    /** Kuyruktaki admin loglarini dosyaya yazar (5 sn'de bir, async). */
    void flushAdminLog() {
        if (alogq.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (n < 5000) {
            String line = alogq.poll();
            if (line == null) {
                break;
            }
            sb.append(line).append('\n');
            n++;
        }
        if (n == 0) {
            return;
        }
        File dir = new File("plugins/Skript/logs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File f = new File(dir, "claim-admin-" + day + ".txt");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("claim-admin logu yazilamadi: " + e.getMessage());
        }
    }

    boolean canViewLogs(Player p, String mainCid) {
        String u = uuid(p);
        Claim c = store.get(mainCid);
        if (c != null && u.equals(c.owner)) {
            return true;
        }
        if (p.hasPermission("claim.admin") || isKurucu(p)) {
            return true;
        }
        return isMemberUuid(mainCid, u) && permOf(mainCid, u, "logs");
    }

    // ------------------------------------------------------------------ //
    //  SAYIMLAR / LIMITLER
    // ------------------------------------------------------------------ //

    int playerTotalChunks(Player p) {
        String u = uuid(p);
        int t = 0;
        for (String cid : store.all) {
            if (u.equals(store.owner(cid))) {
                t++;
            }
        }
        return t;
    }

    int playerMainClaimCount(Player p) {
        return mainClaimCountByUuid(uuid(p));
    }

    int mainClaimCountByUuid(String u) {
        int n = 0;
        for (String cid : store.all) {
            Claim c = store.get(cid);
            if (c != null && c.main() && u.equals(c.owner)) {
                n++;
            }
        }
        return n;
    }

    int countSubChunks(String mainCid) {
        int n = 0;
        for (String cid : store.all) {
            if (cid.equals(mainCid)) {
                continue;
            }
            Claim c = store.get(cid);
            if (c != null && mainCid.equals(c.mainCid) && c.owner != null) {
                n++;
            }
        }
        return n;
    }

    boolean inClaimWorld(Player p) {
        if (p.getWorld().getName().equals(cfg.claimWorld)) {
            return true;
        }
        pre(p, "<#ff4b1f>Bunu bu dünyada yapamazsın! &7Claim işlemleri sadece &eAna Dünya&7'da yapılabilir.");
        sound(p, "entity.villager.no", 1, 1);
        return false;
    }

    int memberLimit(Player p) {
        if (p.hasPermission("group.dvip+")) {
            return cfg.memberLimitDvipPlus;
        }
        if (p.hasPermission("group.dvip")) {
            return cfg.memberLimitDvip;
        }
        if (p.hasPermission("group.vip")) {
            return cfg.memberLimitVip;
        }
        return cfg.memberLimitDefault;
    }

    int maxMainClaims(Player p) {
        if (p.hasPermission("group.dvip+")) {
            return cfg.maxMainClaimsDvipPlus;
        }
        if (p.hasPermission("group.dvip")) {
            return cfg.maxMainClaimsDvip;
        }
        if (p.hasPermission("group.vip")) {
            return cfg.maxMainClaimsVip;
        }
        return cfg.maxMainClaims;
    }

    /** Genisletme fiyati: genisletilen claim'in kendi bolge sayisina gore. */
    long nextExpansionPrice(Player p, String mainCid) {
        Integer expansions = null;
        if (mainCid != null && !mainCid.isEmpty() && !mainCid.equals("<none>") && store.owner(mainCid) != null) {
            expansions = countSubChunks(mainCid);
        }
        if (expansions == null) {
            expansions = playerTotalChunks(p) - playerMainClaimCount(p);
        }
        if (expansions < 0) {
            expansions = 0;
        }
        double price = cfg.expansionBasePrice;
        for (int i = 0; i < expansions; i++) {
            price = price * cfg.priceIncreaseRate;
        }
        return Math.round(price);
    }

    // ------------------------------------------------------------------ //
    //  ENERJI
    // ------------------------------------------------------------------ //

    double upgMult(String mc) {
        Claim c = store.get(mc);
        double m = 1;
        if (c != null && c.upgOn("farm2x")) {
            m *= cfg.upgradeEnergyMult;
        }
        if (c != null && c.upgOn("night")) {
            m *= cfg.upgradeEnergyMult;
        }
        return m;
    }

    double drainPerHour(String mc) {
        Claim c = store.get(mc);
        int chunks = c == null ? 1 : Math.max(1, c.chunkCount);
        return chunks * cfg.energyPerChunkHour * upgMult(mc);
    }

    double energyMax(String mc) {
        return drainPerHour(mc) * 24 * cfg.energyMaxDays;
    }

    private static double exp(Claim c) {
        return c.expiry == null ? 0 : c.expiry;
    }

    /** Sadece hesaplar, HICBIR SEY yazmaz (gosterim icin). */
    double energyNow(String mc) {
        Claim c = store.get(mc);
        if (c == null || c.owner == null) {
            return 0;
        }
        double n = now();
        Double energy = c.energy;
        Double last = c.energyTime;
        if (energy == null) {
            double left = Math.max(0, exp(c) - n);
            return (left / 3600) * drainPerHour(mc);
        }
        if (last == null) {
            return energy;
        }
        double e = energy;
        double el = n - last;
        if (el > 0) {
            e = e - (el / 3600) * drainPerHour(mc);
        }
        return Math.max(0, e);
    }

    /** Kilitli yazma. Kilit alinamazsa sadece hesaplanir. */
    double syncEnergy(String mc) {
        if (store.owner(mc) == null) {
            return 0;
        }
        if (!reserve("en|" + mc, "sync")) {
            return energyNow(mc);
        }
        double e = syncEnergyRaw(mc);
        release("en|" + mc);
        return e;
    }

    double syncEnergyRaw(String mc) {
        Claim c = store.get(mc);
        if (c == null || c.owner == null) {
            return 0;
        }
        double n = now();
        Double energy = c.energy;
        Double last = c.energyTime;
        double e;
        if (energy == null) {
            double left = Math.max(0, exp(c) - n);
            e = (left / 3600) * drainPerHour(mc);
            last = n;
        } else {
            e = energy;
        }
        if (last == null) {
            last = n;
        }
        double el = n - last;
        if (el > 0) {
            e = e - (el / 3600) * drainPerHour(mc);
        }
        if (e < 0) {
            e = 0;
        }
        c.energy = e;
        c.energyTime = n;
        double rate = drainPerHour(mc);
        if (rate <= 0) {
            rate = 1;
        }
        c.expiry = n + Math.floor((e / rate) * 3600);
        return e;
    }

    /** Kilitli ekleme. Kilit alinamazsa -1 (cagiran parayi iade eder). */
    double addEnergy(String mc, double amount) {
        if (!reserve("en|" + mc, "add")) {
            return -1;
        }
        double cur = syncEnergyRaw(mc);
        double nw = cur + amount;
        double cap = energyMax(mc);
        if (nw > cap) {
            nw = cur < cap ? cap : cur;
        }
        Claim c = store.get(mc);
        if (c != null) {
            c.energy = nw;
            c.energyTime = now();
            double rate = drainPerHour(mc);
            if (rate <= 0) {
                rate = 1;
            }
            c.expiry = now() + Math.floor((nw / rate) * 3600);
        }
        release("en|" + mc);
        return nw;
    }

    double energyCost(double amount) {
        if (amount <= 0) {
            return 0;
        }
        return Math.max(1, Math.ceil(amount * cfg.energyPrice));
    }

    String energyTimeText(String mc) {
        Claim c = store.get(mc);
        double left = c == null ? 0 : Math.max(0, exp(c) - now());
        double hours = left / 3600;
        return (long) Math.floor(hours / 24) + " gün " + (long) Math.floor(hours % 24) + " saat";
    }

    // ------------------------------------------------------------------ //
    //  SINIR / MESAFE
    // ------------------------------------------------------------------ //

    boolean chunkInBorder(String cid) {
        Integer x = cidX(cid);
        Integer z = cidZ(cid);
        if (x == null || z == null) {
            return false;
        }
        if (x * 16 < cfg.borderMin || x * 16 + 16 > cfg.borderMax) {
            return false;
        }
        return z * 16 >= cfg.borderMin && z * 16 + 16 <= cfg.borderMax;
    }

    boolean tooCloseToOtherClaims(String targetCid, String currentMainCid) {
        String w = cidWorld(targetCid);
        Integer x = cidX(targetCid);
        Integer z = cidZ(targetCid);
        if (x == null || z == null) {
            return false;
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                String oc = w + "_" + (x + dx) + "_" + (z + dz);
                Claim o = store.get(oc);
                if (o != null && o.owner != null) {
                    if (currentMainCid.isEmpty()) {
                        return true;
                    }
                    if (!o.mainOrSelf().equals(currentMainCid)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    int mapRange() {
        return Math.max(0, (int) Math.floor((cfg.mapView - 5) / 2.0));
    }

    boolean inMapView(String mainCid, String targetCid) {
        Integer mx = cidX(mainCid);
        Integer mz = cidZ(mainCid);
        Integer tx = cidX(targetCid);
        Integer tz = cidZ(targetCid);
        if (mx == null || mz == null) {
            return true;
        }
        if (tx == null || tz == null) {
            return false;
        }
        int half = mapRange() + 2;
        return Math.abs(tx - mx) <= half && Math.abs(tz - mz) <= half;
    }

    // ------------------------------------------------------------------ //
    //  CIFTCI FIYATLARI (EconomyShopGUI) / OTO SATIS
    // ------------------------------------------------------------------ //

    private double esgSellPrice(String k) {
        Material mat = Material.matchMaterial(k);
        if (mat == null) {
            return -1;
        }
        try {
            Class<?> hook = Class.forName("me.gypopo.economyshopgui.api.EconomyShopGUIHook");
            org.bukkit.inventory.ItemStack is = new org.bukkit.inventory.ItemStack(mat, 1);
            Object si = hook.getMethod("getShopItem", org.bukkit.inventory.ItemStack.class).invoke(null, is);
            if (si == null) {
                return -1;
            }
            Method sellable = null;
            Method sellPrice = null;
            for (Method mm : hook.getMethods()) {
                if (mm.getName().equals("isSellAble") && mm.getParameterCount() == 1) {
                    sellable = mm;
                }
                if (mm.getName().equals("getItemSellPrice") && mm.getParameterCount() == 2) {
                    sellPrice = mm;
                }
            }
            if (sellable == null || !Boolean.TRUE.equals(sellable.invoke(null, si))) {
                return -1;
            }
            Object pr = sellPrice == null ? null : sellPrice.invoke(null, si, is);
            if (pr == null) {
                pr = si.getClass().getMethod("getSellPrice").invoke(si);
            }
            if (!(pr instanceof Number n) || n.doubleValue() <= 0) {
                return -1;
            }
            return n.doubleValue();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return -1;
        }
    }

    int refreshFarmerPrices(boolean announce) {
        int ok = 0;
        boolean changed = false;
        List<String> missing = new ArrayList<>();
        for (String k : Farmer.KEYS) {
            Double old = fPrices.get(k);
            double pr = esgSellPrice(k);
            if (pr > 0) {
                fPrices.put(k, Math.round(pr * 10000) / 10000.0);
                fPriceSrc.put(k, "ESG");
                ok++;
            } else {
                missing.add(k);
                if (cfg.farmerPriceFallback) {
                    fPrices.put(k, Farmer.DEF_PRICE.get(k));
                    fPriceSrc.put(k, "Varsayılan");
                } else {
                    fPrices.put(k, 0.0);
                    fPriceSrc.put(k, "Satılamaz");
                }
            }
            if (!fPrices.get(k).equals(old)) {
                changed = true;
            }
        }
        if (changed) {
            fpriceVer.incrementAndGet();
        }
        if (announce) {
            Text.console(cfg.prefix + " &aÇiftçi fiyatları EconomyShopGUI'den yüklendi: &e" + ok + "&7/&e" + Farmer.KEYS.size());
            if (!missing.isEmpty()) {
                Text.console(cfg.prefix + " &cShop'ta satış fiyatı bulunamayanlar: &7" + String.join(", ", missing));
            }
        }
        return ok;
    }

    double price(String k) {
        Double p = fPrices.get(k);
        return p == null ? 0 : p;
    }

    /** 0.015 -> "0.015", 4.0 -> "4" (4 haneye kadar, sondaki sifirlar atilir) */
    static String fmtPrice(double n) {
        boolean neg = n < 0;
        if (neg) {
            n = -n;
        }
        long i = Math.round(n * 10000);
        long whole = i / 10000;
        long frac = i - whole * 10000;
        StringBuilder out = new StringBuilder(Long.toString(whole));
        if (frac > 0) {
            StringBuilder fs = new StringBuilder(Long.toString(frac));
            while (fs.length() < 4) {
                fs.insert(0, '0');
            }
            while (fs.length() > 0 && fs.charAt(fs.length() - 1) == '0') {
                fs.setLength(fs.length() - 1);
            }
            out.append('.').append(fs);
        }
        return neg ? "-" + out : out.toString();
    }

    static String farmerPriceText(double price) {
        if (price <= 0) {
            return "&cSatılamaz";
        }
        return "&a" + fmtPrice(price) + "$";
    }

    /** 950 -> "950", 12345 -> "12.3K", 2500000 -> "2.5M" */
    static String fmtShort(double n) {
        if (n < 0) {
            n = 0;
        }
        if (n >= 1_000_000_000) {
            return fmtPrice(Math.round(n / 100_000_000) / 10.0) + "B";
        }
        if (n >= 1_000_000) {
            return fmtPrice(Math.round(n / 100_000) / 10.0) + "M";
        }
        if (n >= 1000) {
            return fmtPrice(Math.round(n / 100) / 10.0) + "K";
        }
        if (n >= 10) {
            return Long.toString(Math.round(n));
        }
        return fmtPrice(Math.round(n * 100) / 100.0);
    }

    boolean autoSellMemberValid(String mc, String u) {
        Claim c = store.get(mc);
        if (c == null) {
            return false;
        }
        if (u.equals(c.owner)) {
            return true;
        }
        return c.members.contains(u) && c.perm(u, "farmer");
    }

    static Player onlineByUuid(String u) {
        try {
            return Bukkit.getPlayer(UUID.fromString(u));
        } catch (RuntimeException e) {
            return null;
        }
    }

    boolean asCanTurnOn(Player p, String mc) {
        if (!p.hasPermission("group.dvip+")) {
            return false;
        }
        return autoSellMemberValid(mc, uuid(p));
    }

    boolean asCanTurnOff(Player p, String mc) {
        if (uuid(p).equals(store.owner(mc)) || p.hasPermission("claim.admin") || isKurucu(p)) {
            return true;
        }
        return asCanTurnOn(p, mc);
    }

    /** Yetkisini kaybedenlerin actigi urunleri kapatir. Donus: kapatilan sayi. */
    int validateAutoSell(String mc) {
        Claim c = store.get(mc);
        if (c == null) {
            return 0;
        }
        int closed = 0;
        for (String k : Farmer.KEYS) {
            String by = c.asOn.get(k);
            if (by == null) {
                continue;
            }
            boolean ok = autoSellMemberValid(mc, by);
            if (ok) {
                Player byP = onlineByUuid(by);
                if (byP != null && !byP.hasPermission("group.dvip+")) {
                    ok = false;
                }
            }
            if (!ok) {
                c.asOn.remove(k);
                c.asOnName.remove(k);
                closed++;
            }
        }
        if (closed > 0) {
            c.needsSave = true;
            store.bumpVer(mc);
            Player owner = c.owner == null ? null : onlineByUuid(c.owner);
            if (owner != null) {
                pre(owner, "&cÇiftçi: &e" + closed + " &cüründe oto satış kapandı. &7(Açan kişi yetkisini/üyeliğini kaybetti)");
            }
        }
        return closed;
    }

    void payAdd(String u, double amount) {
        fpay.computeIfAbsent(u, k -> new AtomicLong()).addAndGet(Math.round(amount * 100));
    }

    /** Oto satis alacaklarini oder (3 sn'de bir). */
    void payFlush() {
        for (Map.Entry<String, AtomicLong> e : fpay.entrySet()) {
            long c = e.getValue().getAndSet(0);
            if (c <= 0) {
                continue;
            }
            double amt = c / 100.0;
            try {
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(e.getKey()));
                if (!Econ.deposit(op, amt)) {
                    // Odenemediyse alacak geri yazilir (kaybolmaz)
                    e.getValue().addAndGet(c);
                    continue;
                }
                alogPay.merge(e.getKey(), amt, Double::sum);
            } catch (RuntimeException ex) {
                e.getValue().addAndGet(c);
            }
        }
    }

    void prodAdd(String mc, String k, double n) {
        if (n <= 0) {
            return;
        }
        fprod.computeIfAbsent(mc + "|" + k, kk -> new AtomicLong()).addAndGet((long) Math.floor(n));
    }

    void prodTick() {
        double n = now();
        double last = Double.isNaN(fprodLast) ? n - 60 : fprodLast;
        double min = (n - last) / 60;
        if (min < 0.25) {
            return;
        }
        fprodLast = n;
        java.util.Set<String> ch = new java.util.HashSet<>();
        for (Map.Entry<String, AtomicLong> e : fprod.entrySet()) {
            long c = e.getValue().getAndSet(0);
            Double old = frate.get(e.getKey());
            double nw = -1;
            if (old != null) {
                nw = old + ((c / min) - old) * 0.2;
            } else if (c > 0) {
                nw = c / min;
            }
            if (nw >= 0) {
                if (nw < 0.01) {
                    frate.remove(e.getKey());
                } else {
                    frate.put(e.getKey(), nw);
                }
                ch.add(e.getKey().split("\\|")[0]);
            }
        }
        for (String mc : ch) {
            store.bumpVer(mc);
        }
    }

    double prodRate(String mc, String k) {
        Double r = frate.get(mc + "|" + k);
        return r == null ? 0 : r;
    }

    List<String> prodLore(String mc, String k, double price) {
        double r = prodRate(mc, k);
        List<String> l = new ArrayList<>();
        if (r <= 0) {
            l.add("&7Üretim &8· &8şu an yok");
            return l;
        }
        l.add("&7Üretim &8· &f" + fmtShort(r * 60) + " &7adet/saat");
        if (price <= 0) {
            l.add("&7Getiri &8· &cSatılamaz");
        } else {
            double mm = r * price;
            l.add("&7Getiri &8· &a" + fmtShort(mm) + "$&7/dk &8| &a" + fmtShort(mm * 60) + "$&7/sa &8| &a" + fmtShort(mm * 1440) + "$&7/gün");
        }
        return l;
    }

    double farmerCap(int level) {
        return cfg.farmerBaseCap * Math.pow(2, level - 1);
    }

    /**
     * claim.sk "on item spawn" depo/oto satis govdesi (farmerTake).
     * true -> urun ciftcinin (satildi / depoya eklendi / depo dolu, silindi).
     */
    boolean farmerTake(String cid, String matchKey, long amount) {
        Claim c = store.get(cid);
        if (c == null || c.owner == null) {
            return false;
        }
        String mainCid = c.mainCid;
        Claim mc = mainCid == null ? null : store.get(mainCid);
        if (mc == null || !mc.farmerOn()) {
            return false;
        }
        String asBy = mc.asOn.get(matchKey);
        if (asBy != null) {
            double n = now();
            double lastT = asChk.getOrDefault(mainCid, 0.0);
            if (n - lastT >= 30) {
                asChk.put(mainCid, n);
                validateAutoSell(mainCid);
                asBy = mc.asOn.get(matchKey);
            }
        }
        if (asBy != null && autoSellMemberValid(mainCid, asBy)) {
            double pr = price(matchKey);
            if (pr > 0) {
                payAdd(mc.owner, amount * pr);
                prodAdd(mainCid, matchKey, amount);
                return true;
            }
        }
        double cap = farmerCap(mc.farmerLevel);
        long added = store.fAddCapped(mainCid, matchKey, amount, cap);
        if (added > 0) {
            store.bumpVer(mainCid);
            mc.needsSave = true;
            prodAdd(mainCid, matchKey, added);
        }
        return true;
    }

    // ------------------------------------------------------------------ //
    //  YENI CLAIM / GECE KORUMASI
    // ------------------------------------------------------------------ //

    double claimProtectLeft(String mc) {
        Claim c = store.get(mc);
        if (c == null || c.protectUntil == null) {
            return 0;
        }
        double left = c.protectUntil - now();
        return left <= 0 ? 0 : left;
    }

    @Override
    public double newProtectLeft(String cid) {
        Claim c = store.get(cid);
        if (c == null || c.owner == null) {
            return 0;
        }
        String mc = c.mainOrSelf();
        double left = claimProtectLeft(mc);
        if (left <= 0) {
            return 0;
        }
        Double pc = persist.num("protchunk", cid);
        Claim m = store.get(mc);
        if (pc == null || m == null || m.protectUntil == null || pc.doubleValue() != m.protectUntil.doubleValue()) {
            return 0;
        }
        return left;
    }

    double nightWindowLeft() {
        int s = LocalTime.now(nightZone).toSecondOfDay();
        int a = cfg.nightStartHour * 3600;
        int b = cfg.nightEndHour * 3600;
        if (a < b) {
            if (s >= a && s < b) {
                return b - s;
            }
            return 0;
        }
        if (a > b) {
            if (s >= a) {
                return (86400 - s) + b;
            }
            if (s < b) {
                return b - s;
            }
        }
        return 0;
    }

    @Override
    public double chunkNightLeft(String cid) {
        Claim c = store.get(cid);
        if (c == null || c.owner == null) {
            return 0;
        }
        Claim m = store.get(c.mainOrSelf());
        if (m == null || !m.upgOn("night")) {
            return 0;
        }
        return nightWindowLeft();
    }

    @Override
    public double chunkProtectLeft(String cid) {
        double left = newProtectLeft(cid);
        if (left > 0) {
            return left;
        }
        return chunkNightLeft(cid);
    }

    void markProtChunk(String cid, String mainCid) {
        if (persist.get("protchunk", cid) != null) {
            return;
        }
        if (claimProtectLeft(mainCid) > 0) {
            persist.set("protchunk", cid, store.get(mainCid).protectUntil);
        } else {
            persist.set("protchunk", cid, 0L);
        }
    }

    double protectLeftAt(Location l) {
        return chunkProtectLeft(chunkId(l));
    }

    boolean protectNear(Location l) {
        int[] d = {0, 8, -8};
        for (int a : d) {
            for (int b : d) {
                if (protectLeftAt(l.clone().add(a, 0, b)) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    String nightHours() {
        String a = Integer.toString(cfg.nightStartHour);
        if (a.length() < 2) {
            a = "0" + a;
        }
        String b = Integer.toString(cfg.nightEndHour);
        if (b.length() < 2) {
            b = "0" + b;
        }
        return a + ":00 - " + b + ":00";
    }

    /** Engelleme mesaji: korumali claimde koruma mesaji, degilse normal mesaj. */
    void denyNotice(Player p, Location loc, String msg) {
        String dcid = chunkId(loc);
        double left = newProtectLeft(dcid);
        String kind = "new";
        if (left <= 0) {
            left = chunkNightLeft(dcid);
            kind = "night";
        }
        if (left > 0) {
            String u = uuid(p);
            double n = now();
            Double last = protMsg.get(u);
            if (last != null && n - last < 2) {
                return;
            }
            protMsg.put(u, n);
            if (kind.equals("night")) {
                pre(p, "<#7dd3fc>Bu claim gece koruması altında! &7" + nightHours() + " arası raidlenemez. Kalan: &e" + fmtDur(left));
            } else {
                pre(p, "<#a8ff78>Bu claim yeni kuruldu ve korunuyor! &7Kalan: &e" + fmtDur(left));
            }
            sound(p, "item.shield.block", 0.6f, 1.2f);
        } else {
            send(p, msg);
        }
    }

    // ------------------------------------------------------------------ //
    //  ENERJI UYARISI
    // ------------------------------------------------------------------ //

    void refreshLowEnergyIndex() {
        double n = now();
        double lim = cfg.energyWarnDays * 86400;
        lowEnergy.clear();
        for (String cid : store.all) {
            Claim c = store.get(cid);
            if (c != null && c.main() && c.expiry != null && c.expiry - n < lim) {
                lowEnergy.add(cid);
            }
        }
    }

    void energyJoinWarn(Player p) {
        String u = uuid(p);
        double n = now();
        double lim = cfg.energyWarnDays * 86400;
        for (String mc : new ArrayList<>(lowEnergy)) {
            Claim c = store.get(mc);
            if (c == null || c.owner == null) {
                continue;
            }
            double left = exp(c) - n;
            if (left >= lim) {
                continue;
            }
            String cName = String.valueOf(c.claimName);
            if (u.equals(c.owner)) {
                send(p, "");
                pre(p, "<#ff6b6b>Enerji azalıyor &8· &f" + cName);
                send(p, " &7Kalan süre &8· &c" + fmtDur(left) + "&7. Enerji bitince claim patlar.");
                send(p, " &7Claim menüsünden &fClaim Enerjisi &7ile yükleyebilirsin.");
                send(p, " &8" + Text.num(cfg.energyWarnDays) + " günün üzerine çıkınca bu uyarı kaybolur.");
                send(p, "");
                sound(p, "block.note_block.bass", 1, 0.6f);
            } else if (c.members.contains(u)) {
                pre(p, "&7Üyesi olduğun &f" + cName + " &7claim'inin enerjisi azalıyor &8· &c" + fmtDur(left));
                send(p, "&8› &7Claim sahibi &e" + c.ownerName + " &7'e haber ver, yoksa claim patlayacak.");
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  CLAIM TASI VE HOLOGRAM
    // ------------------------------------------------------------------ //

    static String stoneKey(Location l) {
        return l.getWorld().getName() + "_" + (long) Math.floor(l.getX()) + "_" + (long) Math.floor(l.getY()) + "_" + (long) Math.floor(l.getZ());
    }

    boolean isStoneAt(Location loc) {
        String ck = chunkId(loc);
        String key = stoneKey(loc);
        java.util.Set<String> s = stonesByChunk.get(ck);
        if (s == null) {
            return false;
        }
        for (String mc : s) {
            Claim c = store.get(mc);
            Location sl = c == null ? null : c.stone;
            if (sl != null && stoneKey(sl).equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean stoneNearTowerBase(Location loc) {
        int x = (int) Math.floor(loc.getX());
        int y = (int) Math.floor(loc.getY());
        int z = (int) Math.floor(loc.getZ());
        World w = loc.getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (isStoneAt(new Location(w, x + dx, y + dy, z + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Konumun 1 blok cevresinde (3x3, y -2..+1) sok kulesi parcasi var mi? */
    boolean towerNearLoc(Location loc) {
        TowerModule t = towers;
        if (t == null) {
            return false;
        }
        World w = loc.getWorld();
        int x = (int) Math.floor(loc.getX());
        int y = (int) Math.floor(loc.getY());
        int z = (int) Math.floor(loc.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    if (t.isTowerAt(w, x + dx, y + dy, z + dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean inSokCombat(Player p) {
        TowerModule t = towers;
        if (t == null) {
            return false;
        }
        double until = t.combatUntil(p.getUniqueId());
        return !Double.isNaN(until) && now() < until;
    }

    void indexStone(String mc, Location l) {
        stonesByChunk.computeIfAbsent(chunkId(l), k -> ConcurrentHashMap.newKeySet()).add(mc);
    }

    void unindexStone(String mc, Location l) {
        stonesByChunk.computeIfPresent(chunkId(l), (k, s) -> {
            s.remove(mc);
            return s.isEmpty() ? null : s;
        });
    }

    String stoneHoloText(String mc) {
        Claim c = store.get(mc);
        double e = energyNow(mc);
        String name = c == null || c.claimName == null ? "İsimsiz" : c.claimName;
        String owner = c == null || c.ownerName == null ? "Bilinmiyor" : c.ownerName;
        String created = c == null || c.createdText == null ? "Bilinmiyor" : c.createdText;
        int chunks = c == null ? 1 : c.chunkCount;
        double left = Math.max(0, (c == null ? 0 : exp(c)) - now());
        long d = (long) Math.floor(left / 86400);
        long h = (long) Math.floor((left % 86400) / 3600);
        String tc = d > 7 ? "&a" : d > 2 ? "&e" : "&c";
        return "&6" + name + " &6\n&7Sahip &8· &f" + owner + "\n&7Kuruluş &8· &f" + created + "\n&7Enerji &8· &b"
                + (long) Math.floor(e) + "\n&7Kalan &8· " + tc + d + " gün " + h + " saat\n&7Boyut &8· &f" + chunks + " chunk";
    }

    private static boolean alive(Entity e) {
        return e != null && e.isValid() && !e.isDead();
    }

    private static void clearTextDisplays(Location center, double radius) {
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof TextDisplay && e.getLocation().distance(center) <= radius) {
                e.remove();
            }
        }
    }

    /** Hologrami olustur/tazele. SADECE oyuncu olayindan (tasin bolgesinde). */
    void refreshHolo(String mc) {
        Claim c = store.get(mc);
        if (c == null || c.stone == null) {
            return;
        }
        Location st = c.stone;
        Location holoLoc = new Location(st.getWorld(), Math.floor(st.getX()) + 0.5, Math.floor(st.getY()) + 1.5, Math.floor(st.getZ()) + 0.5);
        Entity holo = c.holo;
        if (alive(holo) && holo instanceof TextDisplay td) {
            td.text(Text.color(stoneHoloText(mc)));
            c.holoTime = now();
            return;
        }
        clearTextDisplays(holoLoc, 1.5);
        TextDisplay nw = holoLoc.getWorld().spawn(holoLoc, TextDisplay.class, td -> {
            td.text(Text.color(stoneHoloText(mc)));
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(false);
        });
        c.holo = nw;
        c.holoTime = now();
    }

    /** Tasi kurar (blok + kayit + hologram). Oyuncunun KENDI bolgesinden. */
    void placeStone(String mc, Location loc) {
        Claim c = store.get(mc);
        if (c == null) {
            return;
        }
        Location target = loc.getBlock().getLocation();
        Location old = c.stone;
        Entity oldHolo = c.holo;
        if (alive(oldHolo)) {
            oldHolo.remove();
        }
        if (old != null) {
            boolean far = !old.getWorld().equals(target.getWorld()) || old.distance(target) > 64;
            if (far) {
                String ock = chunkId(old);
                stoneCleanup.computeIfAbsent(ock, k -> new ConcurrentHashMap<>()).put(mc, old);
                unindexStone(mc, old);
                old = null;
            }
        }
        if (old != null) {
            Location ohl = new Location(old.getWorld(), Math.floor(old.getX()) + 0.5, Math.floor(old.getY()) + 1.5, Math.floor(old.getZ()) + 0.5);
            clearTextDisplays(ohl, 2.5);
            if (old.getBlock().getType() == Material.LODESTONE) {
                old.getBlock().setType(Material.AIR);
            }
            unindexStone(mc, old);
        }
        c.holo = null;
        c.holoTime = Double.NaN;
        target.getBlock().setType(Material.LODESTONE);
        c.stone = target;
        indexStone(mc, target);
        store.save(mc);
        refreshHolo(mc);
    }

    void moveStone(Player p, String mc, Location loc) {
        Claim c = store.get(mc);
        if (c == null) {
            return;
        }
        Location old = c.stone;
        if (old != null) {
            soundAt(old, "block.beacon.deactivate", 0.7f, 1.5f);
            old.getWorld().spawnParticle(Particle.CLOUD, old, 15);
        }
        placeStone(mc, loc);
        c.moveTime = now();
        clog(mc, "&e" + p.getName() + " &7claim taşını taşıdı &8(" + ltxt(loc) + ")");
        alog("TAS", p.getName() + "(" + p.getUniqueId() + ") claim=" + mc + " tasi tasidi loc=" + altxt(loc));
        Location nw = c.stone != null ? c.stone : loc;
        Location fx = new Location(nw.getWorld(), Math.floor(nw.getX()) + 0.5, Math.floor(nw.getY()) + 1.2, Math.floor(nw.getZ()) + 0.5);
        soundAt(fx, "block.respawn_anchor.set_spawn", 0.8f, 1.2f);
        soundAt(fx, "block.beacon.activate", 0.9f, 1.6f);
        soundAt(fx, "block.amethyst_block.chime", 1, 1.4f);
        sound(p, "entity.player.levelup", 0.5f, 2);
        fx.getWorld().spawnParticle(Particle.END_ROD, fx, 25);
        fx.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, fx, 15);
        pre(p, "<#a8ff78>Claim taşı buraya taşındı.");
    }

    /** Tasi ve hologrami kaldir (oyuncu olayindan). */
    void removeStone(String mc) {
        Claim c = store.get(mc);
        if (c == null) {
            return;
        }
        if (alive(c.holo)) {
            c.holo.remove();
        }
        Location st = c.stone;
        if (st != null) {
            Location hl = new Location(st.getWorld(), Math.floor(st.getX()) + 0.5, Math.floor(st.getY()) + 1.5, Math.floor(st.getZ()) + 0.5);
            clearTextDisplays(hl, 2.5);
            if (st.getBlock().getType() == Material.LODESTONE) {
                st.getBlock().setType(Material.AIR);
            }
            unindexStone(mc, st);
        }
        c.stone = null;
        c.holo = null;
        c.holoTime = Double.NaN;
        c.moveTime = Double.NaN;
    }

    // ------------------------------------------------------------------ //
    //  KAYIT / SILME
    // ------------------------------------------------------------------ //

    void save(String cid) {
        store.save(cid);
    }

    /** claim.sk deleteSingleClaim: ana claim + tum alt chunk'lari. */
    void deleteClaim(String cid) {
        List<String> toDelete = new ArrayList<>();
        for (String sub : store.all) {
            Claim c = store.get(sub);
            if (c != null && cid.equals(c.mainCid)) {
                toDelete.add(sub);
            }
        }
        if (!toDelete.contains(cid)) {
            toDelete.add(cid);
        }
        for (String del : toDelete) {
            Claim c = store.get(del);
            if (c != null && c.stone != null) {
                String sck = chunkId(c.stone);
                stoneCleanup.computeIfAbsent(sck, k -> new ConcurrentHashMap<>()).put(del, c.stone);
                unindexStone(del, c.stone);
            }
            store.backupDeletedFile(del);
            if (towers != null) {
                towers.markFcidDirty(del);
            }
            store.claims.remove(del);
            store.clog.remove(del);
            lowEnergy.remove(del);
            asChk.remove(del);
            for (String k : Farmer.KEYS) {
                store.fstock.remove(del + "|" + k);
                fprod.remove(del + "|" + k);
                frate.remove(del + "|" + k);
            }
            store.all.remove(del);
        }
    }

    // ------------------------------------------------------------------ //
    //  BILGI / PLACEHOLDER KOPRUSU
    // ------------------------------------------------------------------ //

    /** {-claim_plc::<alan>::<uuid>} (scoreboard placeholder'lari bunu okur) */
    void plc(String field, String u, String value) {
        SkriptBridge.set("-claim_plc::" + field + "::" + u, value);
    }

    Map<String, Object> debugSnapshot() {
        Map<String, Object> m = new HashMap<>();
        m.put("claims", store.claims.size());
        m.put("all", store.all.size());
        return m;
    }

    static String nameOf(Block b) {
        return b.getType().name();
    }

    static String colored(String s) {
        return Gui.legacy(s);
    }
}
