package tr.dugecraft.tower;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import tr.dugecraft.util.SafeFiles;
import tr.dugecraft.util.Text;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * sok.sk KALICILIK + INDEKSLER.
 * Her kulenin verisi, fiziksel durdugu chunk'in cid'i (fcid) adiyla
 * {data_dir}/<fcid>.yml dosyasinin "shock_towers:" bolumune yazilir.
 * Bu dosyalari baska hicbir sey yazmaz. Yazma tek yerden (flush), kilitli.
 */
public final class TowerStore {

    /** Dunyasi yuklu olmayan kule kaydi: silinmez, aynen yazilmaya devam eder. */
    static final class Stash {
        String world;
        double x;
        double y;
        double z;
        String cid;
        String fcid;
        int level;
        int hp;
    }

    private final TowerConfig cfg;

    final ConcurrentHashMap<String, Tower> towers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Set<String>> byCid = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Set<String>> byFcid = new ConcurrentHashMap<>();
    /** 3x3 chunk konum indeksi (kirma/patlama/piston/yerlestirme korumalari) */
    final ConcurrentHashMap<String, Set<String>> byChunk = new ConcurrentHashMap<>();
    /** Menzil indeksi (sadece sok hedeflemesi) */
    final ConcurrentHashMap<String, Set<String>> byRange = new ConcurrentHashMap<>();
    final Set<String> dirtyFcids = ConcurrentHashMap.newKeySet();
    final ConcurrentHashMap<String, Stash> stash = new ConcurrentHashMap<>();

    volatile boolean loaded;
    private final Object flushLock = new Object();

    TowerStore(TowerConfig cfg) {
        this.cfg = cfg;
    }

    // ------------------------------------------------------------------ //
    //  OKUMA
    // ------------------------------------------------------------------ //

    public Tower get(String key) {
        return key == null ? null : towers.get(key);
    }

    public boolean isTowerKey(String key) {
        return key != null && towers.containsKey(key);
    }

    public int size() {
        return towers.size();
    }

    static Set<String> idx(ConcurrentHashMap<String, Set<String>> m, String k) {
        Set<String> s = m.get(k);
        return s == null ? Collections.emptySet() : s;
    }

    private static void idxAdd(ConcurrentHashMap<String, Set<String>> m, String k, String v) {
        m.computeIfAbsent(k, kk -> ConcurrentHashMap.newKeySet()).add(v);
    }

    private static void idxRemove(ConcurrentHashMap<String, Set<String>> m, String k, String v) {
        m.computeIfPresent(k, (kk, s) -> {
            s.remove(v);
            return s.isEmpty() ? null : s;
        });
    }

    public Set<String> byCid(String cid) {
        return idx(byCid, cid);
    }

    /** Oyuncunun bulundugu chunk'a kayitli kuleler (3x3 indeks). */
    public Set<String> nearby(Location l) {
        return idx(byChunk, Tower.chunkId(l));
    }

    /** Oyuncunun chunk'ina menzili yetisebilecek kuleler. */
    public Set<String> nearbyRange(Location l) {
        return idx(byRange, Tower.chunkId(l));
    }

    // ------------------------------------------------------------------ //
    //  INDEKS
    // ------------------------------------------------------------------ //

    private void indexChunks(Tower t) {
        String w = t.world.getName();
        long cx = (long) Math.floor(t.x / 16);
        long cz = (long) Math.floor(t.z / 16);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                idxAdd(byChunk, w + "_" + (cx + ox) + "_" + (cz + oz), t.key);
            }
        }
        indexRange(t);
    }

    private void indexRange(Tower t) {
        int r = cfg.indexRadius(t.level);
        String w = t.world.getName();
        long cx = (long) Math.floor(t.x / 16);
        long cz = (long) Math.floor(t.z / 16);
        for (int ox = -r; ox <= r; ox++) {
            for (int oz = -r; oz <= r; oz++) {
                idxAdd(byRange, w + "_" + (cx + ox) + "_" + (cz + oz), t.key);
            }
        }
    }

    private void unindexRange(Tower t) {
        int r = cfg.indexRadiusMax();
        String w = t.world.getName();
        long cx = (long) Math.floor(t.x / 16);
        long cz = (long) Math.floor(t.z / 16);
        for (int ox = -r; ox <= r; ox++) {
            for (int oz = -r; oz <= r; oz++) {
                idxRemove(byRange, w + "_" + (cx + ox) + "_" + (cz + oz), t.key);
            }
        }
    }

    private void unindexChunks(Tower t) {
        unindexRange(t);
        String w = t.world.getName();
        long cx = (long) Math.floor(t.x / 16);
        long cz = (long) Math.floor(t.z / 16);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                idxRemove(byChunk, w + "_" + (cx + ox) + "_" + (cz + oz), t.key);
            }
        }
    }

    /** Seviye degisince menzil indeksi yeni yaricapla yeniden yazilir. */
    void reindexRange(Tower t) {
        unindexRange(t);
        indexRange(t);
    }

    void markDirty(String fcid) {
        if (fcid != null) {
            dirtyFcids.add(fcid);
        }
    }

    /** Kuleyi listeye ve tum indekslere ekler, dosyasini kirli isaretler. */
    void register(Tower t) {
        towers.put(t.key, t);
        if (t.cid != null) {
            idxAdd(byCid, t.cid, t.key);
        }
        if (t.fcid != null) {
            idxAdd(byFcid, t.fcid, t.key);
            markDirty(t.fcid);
        }
        unindexChunks(t);
        indexChunks(t);
    }

    /** Kaydi ve indeksleri siler; dosyasi yeniden yazilsin diye kirli isaretlenir. */
    Tower unregister(String key) {
        Tower t = towers.get(key);
        if (t == null) {
            return null;
        }
        unindexChunks(t);
        towers.remove(key);
        if (t.cid != null) {
            idxRemove(byCid, t.cid, key);
        }
        if (t.fcid != null) {
            idxRemove(byFcid, t.fcid, key);
            markDirty(t.fcid);
        }
        return t;
    }

    private void rebuildIndex() {
        byCid.clear();
        byChunk.clear();
        byRange.clear();
        byFcid.clear();
        for (Tower t : towers.values()) {
            if (t.cid != null) {
                idxAdd(byCid, t.cid, t.key);
            }
            if (t.fcid != null) {
                idxAdd(byFcid, t.fcid, t.key);
            }
            indexChunks(t);
        }
    }

    // ------------------------------------------------------------------ //
    //  YUKLEME
    // ------------------------------------------------------------------ //

    /** Tek kayit. Dunya yuklu degilse null (cagiran kaydi SAKLAR). */
    Tower loadEntry(String w, double x, double y, double z, String cid, String fcid, int lvl, int hp) {
        World world = Bukkit.getWorld(w);
        if (world == null) {
            return null;
        }
        String key = Tower.keyOf(world, x, y, z);
        Tower t = new Tower(key, world, x, y, z);
        t.cid = cid;
        t.fcid = fcid;
        t.level = Math.max(1, Math.min(6, lvl));
        t.hp = hp;
        t.lastAttack = 0;
        towers.put(key, t);
        return t;
    }

    private void loadDir(String dirPath) {
        File dir = new File(dirPath);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String name = f.getName();
            if (!name.endsWith(".yml")) {
                continue;
            }
            String fcid = name.replace(".yml", "");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection sec = y.getConfigurationSection("shock_towers");
            if (sec == null) {
                continue;
            }
            for (String yk : new ArrayList<>(sec.getKeys(false))) {
                String p = "shock_towers." + yk;
                String w = y.getString(p + ".world");
                double px = y.getDouble(p + ".x");
                double py = y.getDouble(p + ".y");
                double pz = y.getDouble(p + ".z");
                String cid = y.getString(p + ".cid");
                if (cid == null) {
                    // cid eksikse kule KAYBOLMASIN: dosyanin chunk'i kullanilir
                    cid = fcid;
                }
                int lv = y.getInt(p + ".level");
                int hp = y.getInt(p + ".hp");
                if (w == null) {
                    continue; // bozuk kayit: atlanir
                }
                if (loadEntry(w, px, py, pz, cid, fcid, lv, hp) == null) {
                    Text.console(cfg.prefix + " <#f87171>Kule beklemede, dünya yok: <#e5e7eb>" + w);
                    Stash s = new Stash();
                    s.world = w;
                    s.x = px;
                    s.y = py;
                    s.z = pz;
                    s.cid = cid;
                    s.fcid = fcid;
                    s.level = lv;
                    s.hp = hp;
                    stash.put(yk, s);
                }
            }
        }
    }

    /**
     * Acilista: "<fcid>.yml.tmp" var ama "<fcid>.yml" yoksa yazma yarida kalmis
     * demektir -> tmp asil dosya yapilir. Ikisi de varsa tmp silinir.
     */
    private void recoverTmpFiles() {
        File[] arr = new File(cfg.dataDir).listFiles();
        if (arr == null) {
            return;
        }
        for (File f : arr) {
            String fn = f.getName();
            if (!fn.endsWith(".yml.tmp")) {
                continue;
            }
            String base = fn.replace(".yml.tmp", ".yml");
            File dst = new File(cfg.dataDir, base);
            if (dst.exists()) {
                f.delete();
            } else {
                f.renameTo(dst);
                Text.console(cfg.prefix + " <#fbbf24>Yarım kalmış kule kaydı kurtarıldı: <#e5e7eb>" + base);
            }
        }
    }

    /** sok.sk loadAllTowers. */
    void loadAll() {
        File dDir = new File(cfg.dataDir);
        if (!dDir.exists()) {
            dDir.mkdirs();
        }
        if (loaded) {
            flush(0);
        }
        loaded = false;
        towers.clear();
        byCid.clear();
        byFcid.clear();
        stash.clear();
        dirtyFcids.clear();

        File marker = new File(cfg.dataDir, "_tasindi.txt");
        boolean legacy = false;
        if (!marker.exists()) {
            legacy = true;
            loadDir("plugins/Skript/yaml/tower_orphans");
            loadDir("plugins/Skript/yaml/claims");
        }
        recoverTmpFiles();
        loadDir(cfg.dataDir);

        rebuildIndex();
        loaded = true;
        migrateOldFile();

        if (legacy) {
            for (Tower t : towers.values()) {
                markDirty(t.fcid);
            }
            for (Stash s : stash.values()) {
                markDirty(s.fcid);
            }
            flush(0);
            if (dirtyFcids.isEmpty()) {
                try {
                    marker.createNewFile();
                } catch (IOException ignored) {
                    // isaret konamazsa tasima sonraki yuklemede tekrar denenir
                }
                Text.console(cfg.prefix + " <#4ade80>Kule kayıtları kendi klasörüne taşındı: <#e5e7eb>" + cfg.dataDir);
            } else {
                Text.console(cfg.prefix + " <#f87171>Bazı kule dosyaları yazılamadı, taşıma sonraki yüklemede tekrar denenecek.");
            }
        }
    }

    /** Tek seferlik gecis: v10.0 ve oncesinin shocktowers.yml dosyasi. */
    private void migrateOldFile() {
        File old = new File("plugins/Skript/yaml/shocktowers.yml");
        if (!old.exists()) {
            return;
        }
        boolean allDone = true;
        int migrated = 0;
        if (old.length() > 20) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(old);
            ConfigurationSection sec = y.getConfigurationSection("towers");
            if (sec != null) {
                for (String yk : new ArrayList<>(sec.getKeys(false))) {
                    String p = "towers." + yk;
                    String w = y.getString(p + ".world");
                    double px = y.getDouble(p + ".x");
                    double py = y.getDouble(p + ".y");
                    double pz = y.getDouble(p + ".z");
                    String cid = y.getString(p + ".cid");
                    int lv = y.getInt(p + ".level");
                    int hp = y.getInt(p + ".hp");
                    World world = w == null ? null : Bukkit.getWorld(w);
                    if (world == null) {
                        allDone = false;
                        continue;
                    }
                    String fcid = Tower.chunkId(world, px, pz);
                    if (cid == null) {
                        cid = fcid;
                    }
                    Tower t = loadEntry(w, px, py, pz, cid, fcid, lv, hp);
                    if (t != null) {
                        register(t);
                        migrated++;
                    } else {
                        allDone = false;
                    }
                }
            }
        }
        if (migrated > 0) {
            flush(0);
            Text.console(cfg.prefix + " <#4ade80>" + migrated + " kule eski shocktowers.yml dosyasından claim dosyalarına taşındı.");
        }
        if (allDone) {
            File bak = new File("plugins/Skript/yaml/shocktowers.yml.migrated-" + (System.currentTimeMillis() / 1000));
            old.renameTo(bak);
            Text.console(cfg.prefix + " <#9ca3af>Eski shocktowers.yml yedeklendi: <#e5e7eb>" + bak.getName());
        }
    }

    /** Dunyasi sonradan yuklenen kuleler (bakim gorevi, 10 sn'de bir). */
    boolean activateStash() {
        boolean changed = false;
        for (Map.Entry<String, Stash> e : new ArrayList<>(stash.entrySet())) {
            Stash s = e.getValue();
            Tower t = loadEntry(s.world, s.x, s.y, s.z, s.cid, s.fcid, s.level, s.hp);
            if (t == null) {
                continue;
            }
            if (t.cid == null) {
                t.cid = t.fcid;
            }
            register(t);
            stash.remove(e.getKey());
            changed = true;
            Text.console(cfg.prefix + " <#4ade80>Bekleyen kule aktiflendi: <#e5e7eb>" + t.key);
        }
        return changed;
    }

    // ------------------------------------------------------------------ //
    //  YAZMA
    // ------------------------------------------------------------------ //

    /** Bellekte bu fcid'e ait kule var mi? Varsa indeksi de onarir. */
    private boolean fcidHasTowers(String fcid) {
        boolean found = false;
        for (Tower t : towers.values()) {
            if (fcid.equals(t.fcid)) {
                idxAdd(byFcid, fcid, t.key);
                found = true;
            }
        }
        return found;
    }

    /**
     * Kirli dosyalari diske yazar. limit: tur basina en fazla dosya (0 = sinirsiz).
     * Isaret, veri okunmadan ONCE silinir: arada gelen degisiklik ya bu
     * yazmaya girer ya da yeni isaret birakir. Yazilamazsa tekrar isaretlenir.
     */
    void flush(int limit) {
        if (!loaded || dirtyFcids.isEmpty()) {
            return;
        }
        synchronized (flushLock) {
            int done = 0;
            for (String fcid : new ArrayList<>(dirtyFcids)) {
                if (limit > 0 && done >= limit) {
                    return;
                }
                if (!dirtyFcids.remove(fcid)) {
                    continue;
                }
                done++;
                writeFcid(fcid);
            }
        }
    }

    private void writeFcid(String fcid) {
        File file = new File(cfg.dataDir, fcid + ".yml");
        YamlConfiguration y = new YamlConfiguration();
        int written = 0;
        for (String key : new ArrayList<>(idx(byFcid, fcid))) {
            Tower t = towers.get(key);
            if (t == null) {
                continue;
            }
            // YAML yolu nokta ile ayrilir -> dunya adindaki nokta anahtari bozmasin
            String p = "shock_towers." + key.replace(".", "~");
            y.set(p + ".world", t.world.getName());
            y.set(p + ".x", t.x);
            y.set(p + ".y", t.y);
            y.set(p + ".z", t.z);
            y.set(p + ".cid", t.cid != null ? t.cid : fcid);
            y.set(p + ".level", t.level);
            y.set(p + ".hp", t.hp);
            written++;
        }
        for (Map.Entry<String, Stash> e : stash.entrySet()) {
            Stash s = e.getValue();
            if (!fcid.equals(s.fcid)) {
                continue;
            }
            String p = "shock_towers." + e.getKey();
            y.set(p + ".world", s.world);
            y.set(p + ".x", s.x);
            y.set(p + ".y", s.y);
            y.set(p + ".z", s.z);
            y.set(p + ".cid", s.cid);
            y.set(p + ".level", s.level);
            y.set(p + ".hp", s.hp);
            written++;
        }

        if (written == 0) {
            // Bu chunk'ta kule kalmadi -> dosya silinir. Once bellekte gercekten
            // kule olmadigi dogrulanir; indeks eksikse dosya SILINMEZ.
            if (!file.exists()) {
                return;
            }
            if (fcidHasTowers(fcid)) {
                markDirty(fcid);
                return;
            }
            if (!file.delete() && file.exists()) {
                markDirty(fcid);
            }
            return;
        }

        File tmp = new File(cfg.dataDir, fcid + ".yml.tmp");
        if (!SafeFiles.write(file, tmp, null, y.saveToString())) {
            markDirty(fcid);
            Text.console(cfg.prefix + " <#f87171>Kule dosyası yazılamadı (kilitli olabilir): <#e5e7eb>" + fcid + ".yml <#9ca3af>· tekrar denenecek");
        }
    }

    // ------------------------------------------------------------------ //
    //  GUNLUK YEDEK
    // ------------------------------------------------------------------ //

    void backup() {
        File src = new File(cfg.dataDir);
        if (!src.exists()) {
            return;
        }
        String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File dst = new File(cfg.backupDir, day);
        if (dst.exists()) {
            return;
        }
        dst.mkdirs();
        int copied = 0;
        File[] arr = src.listFiles();
        if (arr != null) {
            for (File f : arr) {
                if (f.getName().endsWith(".yml")) {
                    try {
                        Files.copy(f.toPath(), new File(dst, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    } catch (IOException ignored) {
                        // tek dosya kopyalanamasa da digerleri devam eder
                    }
                }
            }
        }
        Text.console(cfg.prefix + " <#9ca3af>Kule kayıtları yedeklendi: <#e5e7eb>" + copied + " dosya <#4b5563>(" + cfg.backupDir + "/" + day + ")");
        prune();
    }

    private void prune() {
        File[] dirs = new File(cfg.backupDir).listFiles();
        if (dirs == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (File d : dirs) {
            if (d.isDirectory()) {
                names.add(d.getName());
            }
        }
        Collections.sort(names);
        int extra = names.size() - cfg.backupDays;
        for (int i = 0; i < extra; i++) {
            File dir = new File(cfg.backupDir, names.get(i));
            File[] fs = dir.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    f.delete();
                }
            }
            dir.delete();
        }
    }
}
