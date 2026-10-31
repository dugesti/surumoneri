package tr.dugecraft.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import tr.dugecraft.util.Text;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * claim.sk 1. VERI DEPOLAMA: plugins/Skript/yaml/claims/<cid>.yml.
 * Kayit, mevcut dosyayi OKUYUP ustune yazar (bilinmeyen anahtarlar korunur);
 * metin alanlari Skript'teki gibi "%deger%" (set degilse "<none>").
 */
public final class ClaimStore {

    public static final String DIR = "plugins/Skript/yaml/claims";
    public static final String BACKUP_DELETED = "plugins/Skript/yaml/claim-backups/deleted";
    public static final String BACKUP_CORRUPT = "plugins/Skript/yaml/claim-backups/corrupt";

    final ConcurrentHashMap<String, Claim> claims = new ConcurrentHashMap<>();
    /** {-all_claims::*} (yukleme sirasi korunur) */
    final List<String> all = new CopyOnWriteArrayList<>();
    /** {-fstock}: "mc|urun" -> depo */
    final ConcurrentHashMap<String, AtomicLong> fstock = new ConcurrentHashMap<>();
    /** {-clog::<mc>}: oyuncu log gecmisi (son 150) */
    final ConcurrentHashMap<String, LinkedBlockingDeque<String>> clog = new ConcurrentHashMap<>();
    /** {-fver::<mc>}: acik menuleri guncellemek icin surum sayaci */
    final ConcurrentHashMap<String, AtomicLong> fver = new ConcurrentHashMap<>();

    private final ClaimModule m;

    ClaimStore(ClaimModule m) {
        this.m = m;
    }

    public Claim get(String cid) {
        return cid == null ? null : claims.get(cid);
    }

    public String owner(String cid) {
        Claim c = get(cid);
        return c == null ? null : c.owner;
    }

    static String sk(Object o) {
        return o == null ? "<none>" : o.toString();
    }

    // ------------------------------------------------------------------ //
    //  DEPO SAYACLARI
    // ------------------------------------------------------------------ //

    AtomicLong fStock(String mc, String k) {
        return fstock.computeIfAbsent(mc + "|" + k, kk -> new AtomicLong(0));
    }

    long fGet(String mc, String k) {
        return fStock(mc, k).get();
    }

    void fSet(String mc, String k, double n) {
        fStock(mc, k).set((long) Math.floor(n));
    }

    /** Kapasiteye kadar ekler, eklenen miktari doner (0 = depo dolu). */
    long fAddCapped(String mc, String k, long n, double cap) {
        AtomicLong a = fStock(mc, k);
        for (int i = 0; i < 64; i++) {
            long cur = a.get();
            if (cur >= cap) {
                return 0;
            }
            long add = n;
            if (cur + add > cap) {
                add = (long) (cap - cur);
            }
            if (a.compareAndSet(cur, (long) Math.floor(cur + add))) {
                return add;
            }
        }
        return 0;
    }

    /** Yeterliyse dusurur (true), yetmiyorsa hic dokunmaz (false). */
    boolean fTake(String mc, String k, long n) {
        AtomicLong a = fStock(mc, k);
        for (int i = 0; i < 64; i++) {
            long cur = a.get();
            if (cur < n) {
                return false;
            }
            if (a.compareAndSet(cur, cur - n)) {
                return true;
            }
        }
        return false;
    }

    void bumpVer(String cid) {
        fver.computeIfAbsent(cid, k -> new AtomicLong()).incrementAndGet();
    }

    long ver(String cid) {
        AtomicLong a = fver.get(cid);
        return a == null ? 0 : a.get();
    }

    // ------------------------------------------------------------------ //
    //  DOSYA YARDIMCILARI
    // ------------------------------------------------------------------ //

    /** Tmp dosyasini hedefin yerine koyar; once tek adim, olmazsa eski yol. */
    static boolean replaceFile(File tmp, File dst) {
        try {
            Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // asagida eski yol denenir
        }
        if (!tmp.exists()) {
            return true;
        }
        if (dst.exists()) {
            dst.delete();
        }
        tmp.renameTo(dst);
        return !tmp.exists();
    }

    /** Yarim kalmis gecici dosyalari kurtarir (sadece 10 sn'den eski olanlar). */
    void recoverTmp(String dirPath, String suffix) {
        File[] arr = new File(dirPath).listFiles();
        if (arr == null) {
            return;
        }
        for (File f : arr) {
            String fn = f.getName();
            if (!fn.endsWith(suffix)) {
                continue;
            }
            int cut = fn.indexOf(".yml.");
            if (System.currentTimeMillis() - f.lastModified() < 10000) {
                cut = -1;
            }
            if (cut > 0) {
                String base = fn.substring(0, cut + 4);
                File dst = new File(dirPath, base);
                if (dst.exists()) {
                    f.delete();
                } else {
                    f.renameTo(dst);
                    Text.console(m.cfg.prefix + " &eYarım kalmış kayıt kurtarıldı: &f" + dirPath + "/" + base);
                    m.alog("SISTEM", "yarim kalmis kayit kurtarildi: " + dirPath + "/" + base);
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  YUKLEME
    // ------------------------------------------------------------------ //

    void loadAll() {
        File dir = new File(DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        recoverTmp(DIR, ".ctmp");
        claims.clear();
        all.clear();
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            if (!fileName.endsWith(".yml")) {
                continue;
            }
            String cid = fileName.replace(".yml", "");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            String own = y.getString("owner");
            if (own == null || own.equals("<none>") || own.isEmpty()) {
                File cDir = new File(BACKUP_CORRUPT);
                cDir.mkdirs();
                String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                file.renameTo(new File(cDir, stamp + "_" + fileName));
                Text.console(m.cfg.prefix + " &cBozuk claim dosyası yedeğe alındı: &f" + fileName);
                m.alog("HATA", "bozuk claim dosyasi yuklenmedi, yedege tasindi: " + fileName);
                continue;
            }
            all.add(cid);
            Claim c = new Claim(cid);
            c.owner = own;
            c.ownerName = y.getString("owner_name");
            c.claimName = y.getString("claim_name");
            c.expiry = (double) y.getLong("expiry");
            c.isMain = y.getBoolean("is_main");
            String mc = y.getString("main_cid");
            c.mainCid = (mc == null || mc.equals("<none>") || mc.isEmpty()) ? null : mc;
            if ("<none>".equals(c.claimName)) {
                c.claimName = "Claim";
            }
            c.chunkCount = y.getInt("chunk_count");
            c.centerY = y.getInt("center_y");
            long pu = y.getLong("protect_until");
            if (pu > 0) {
                c.protectUntil = (double) pu;
            }
            c.energy = y.getDouble("energy");
            c.energyTime = (double) y.getLong("energy_time");
            if (c.energyTime <= 0) {
                c.energy = null;
                c.energyTime = null;
            }
            String ct = y.getString("created_text");
            if (ct != null && !ct.equals("<none>")) {
                c.createdText = ct;
            }
            String sw = y.getString("stone.world");
            if (sw != null) {
                World w = Bukkit.getWorld(sw);
                if (w != null) {
                    c.stone = new Location(w, y.getInt("stone.x"), y.getInt("stone.y"), y.getInt("stone.z"));
                }
            }
            for (String mem : y.getStringList("members")) {
                c.members.add(mem);
                for (String perm : Claim.PERMS) {
                    c.setPerm(mem, perm, y.getBoolean("perms." + mem + "." + perm));
                }
            }
            for (String up : Claim.UPGRADES) {
                if (y.getBoolean("upgrades." + up + ".owned")) {
                    c.upgOwn.put(up, true);
                    c.upgOn.put(up, y.getBoolean("upgrades." + up + ".active"));
                    long uoff = y.getLong("upgrades." + up + ".off_time");
                    if (uoff > 0) {
                        c.upgOffT.put(up, (double) uoff);
                    }
                }
            }
            c.farmerStatus = y.getBoolean("farmer.status");
            c.farmerLevel = Math.max(1, y.getInt("farmer.level"));
            for (String k : Farmer.KEYS) {
                if (c.main()) {
                    fSet(cid, k, y.getInt("farmer.items." + k));
                }
                String asOn = y.getString("farmer.as_on." + k);
                if (asOn != null) {
                    c.asOn.put(k, asOn);
                    String asName = y.getString("farmer.as_on_name." + k);
                    if (asName != null) {
                        c.asOnName.put(k, asName);
                    }
                }
            }
            List<String> logs = y.getStringList("logs");
            if (!logs.isEmpty()) {
                LinkedBlockingDeque<String> dq = new LinkedBlockingDeque<>(150);
                for (String l : logs) {
                    dq.offerLast(l);
                }
                clog.put(cid, dq);
            }
            claims.put(cid, c);
        }
    }

    // ------------------------------------------------------------------ //
    //  KAYIT
    // ------------------------------------------------------------------ //

    /** claim.sk saveSingleClaim. Folia: her thread kendi gecici dosyasina yazar. */
    void save(String cid) {
        bumpVer(cid);
        Claim c = claims.get(cid);
        if (c == null || c.owner == null) {
            return;
        }
        try {
            File file = new File(DIR, cid + ".yml");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            y.set("owner", sk(c.owner));
            y.set("owner_name", sk(c.ownerName));
            y.set("claim_name", sk(c.claimName));
            y.set("expiry", c.expiry);
            y.set("is_main", c.isMain);
            y.set("main_cid", sk(c.mainCid));
            y.set("chunk_count", c.chunkCount);
            y.set("center_y", c.centerY);
            y.set("protect_until", c.protectUntil);
            y.set("created_text", sk(c.createdText));
            y.set("energy", c.energy);
            y.set("energy_time", c.energyTime);
            Location st = c.stone;
            if (st != null) {
                y.set("stone.world", st.getWorld().getName());
                y.set("stone.x", (long) Math.floor(st.getX()));
                y.set("stone.y", (long) Math.floor(st.getY()));
                y.set("stone.z", (long) Math.floor(st.getZ()));
            } else {
                y.set("stone", null);
            }
            if (!c.members.isEmpty()) {
                List<String> mem = new ArrayList<>(c.members);
                for (String mu : mem) {
                    Map<String, Boolean> pm = c.perms.get(mu);
                    for (String perm : Claim.PERMS) {
                        y.set("perms." + mu + "." + perm, pm == null ? null : pm.get(perm));
                    }
                }
                y.set("members", mem);
            } else {
                y.set("members", null);
            }
            for (String up : Claim.UPGRADES) {
                if (c.upgOwn(up)) {
                    y.set("upgrades." + up + ".owned", true);
                    y.set("upgrades." + up + ".active", c.upgOn(up));
                    y.set("upgrades." + up + ".off_time", c.upgOffT.get(up));
                } else {
                    y.set("upgrades." + up, null);
                }
            }
            y.set("farmer.status", c.farmerStatus);
            y.set("farmer.level", c.farmerLevel);
            y.set("farmer.autosell", null);
            y.set("farmer.autosell_by", null);
            y.set("farmer.autosell_by_name", null);
            y.set("farmer.as_off", null);
            for (String k : Farmer.KEYS) {
                y.set("farmer.items." + k, fGet(cid, k));
                String on = c.asOn.get(k);
                if (on != null) {
                    y.set("farmer.as_on." + k, on);
                    y.set("farmer.as_on_name." + k, sk(c.asOnName.get(k)));
                } else {
                    y.set("farmer.as_on." + k, null);
                    y.set("farmer.as_on_name." + k, null);
                }
            }
            LinkedBlockingDeque<String> dq = clog.get(cid);
            if (dq != null) {
                y.set("logs", new ArrayList<>(dq));
            }
            File tmp = new File(DIR, cid + ".yml." + Thread.currentThread().getId() + ".ctmp");
            y.save(tmp);
            // Yazma sirasinda claim baska bir thread'de silindiyse dosya DIRILTILMEZ
            Claim now = claims.get(cid);
            if (now == null || now.owner == null) {
                tmp.delete();
                return;
            }
            if (!replaceFile(tmp, file)) {
                c.needsSave = true;
                Text.console(m.cfg.prefix + " &cClaim dosyası yazılamadı (kilitli olabilir): &f" + cid + ".yml &7· tekrar denenecek");
            }
            if (!all.contains(cid)) {
                all.add(cid);
            }
        } catch (IOException | RuntimeException e) {
            c.needsSave = true;
            Text.console(m.cfg.prefix + " &cClaim dosyası yazılamadı (kilitli olabilir): &f" + cid + ".yml &7· tekrar denenecek");
        }
    }

    /** Dosyayi yedek klasorune tasir (silmek yerine). */
    void backupDeletedFile(String cid) {
        File file = new File(DIR, cid + ".yml");
        if (!file.exists()) {
            return;
        }
        File bDir = new File(BACKUP_DELETED);
        if (!bDir.exists()) {
            bDir.mkdirs();
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File bFile = new File(bDir, stamp + "_" + cid + ".yml");
        try {
            Files.move(file.toPath(), bFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // asagidaki yol denenir
        }
        if (file.exists() && !file.renameTo(bFile)) {
            file.delete();
        }
    }
}
