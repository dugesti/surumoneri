package tr.dugecraft.blok;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import tr.dugecraft.util.SafeFiles;
import tr.dugecraft.util.Text;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * bloksinir.sk VERI KATMANI.
 * Anahtarlar (script ile birebir ayni):
 * dilim  -> "dunya;cx;cz;sy;tur"  (sy = "L" : eski surum verisi)
 * toplam -> "dunya;cx;cz;tur"
 * Disk: plugins/DugeCraft/chunklimits_v3.yml ("s" bolumu, YAML yol ayiricisi "|").
 */
public final class LimitStore {

    public static final String[] TYPES = {"piston", "cactus", "repeater", "comparator", "bamboo", "kelp"};
    static final String[] LEGACY_TYPES = {"piston", "cactus", "repeater", "comparator"};

    private final ConcurrentHashMap<String, Integer> sec = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> tot = new ConcurrentHashMap<>();
    /** Bu acilista sayilmis dilimler: "dunya;cx;cz;sy" */
    final Set<String> seen = ConcurrentHashMap.newKeySet();
    /** Limitte yeniden sayim bekleme: "dunya;cx;cz;tur" -> unix saniye */
    final ConcurrentHashMap<String, Double> scan = new ConcurrentHashMap<>();

    private final AtomicBoolean dirty = new AtomicBoolean(false);
    /** Kayit kilidi: 0 = bos, degilse kilidin alindigi an (ms) */
    private final AtomicLong saveLock = new AtomicLong(0);

    private final File dir;
    private final String prefix;
    private final LagMonitor lag;

    LimitStore(File dir, String prefix, LagMonitor lag) {
        this.dir = dir;
        this.prefix = prefix;
        this.lag = lag;
    }

    public int blGet(String k) {
        Integer v = sec.get(k);
        return v == null ? 0 : v;
    }

    public int totGet(String k) {
        Integer v = tot.get(k);
        return v == null ? 0 : v;
    }

    public int size() {
        return sec.size();
    }

    public void markDirty() {
        dirty.set(true);
    }

    /**
     * Dilim sayacini degistirir, chunk toplamini da gunceller (script addCount).
     * Her iki harita guncellemesi anahtar basina atomik.
     */
    public void addCount(String cid, String sy, String t, int delta) {
        if (delta == 0) {
            return;
        }
        String k = cid + ";" + sy + ";" + t;
        int[] diff = new int[1];
        sec.compute(k, (key, ov) -> {
            int old = ov == null ? 0 : ov;
            int nw = Math.max(0, old + delta);
            diff[0] = nw - old;
            return nw == 0 ? null : nw;
        });
        if (diff[0] == 0) {
            return;
        }
        String tk = cid + ";" + t;
        tot.compute(tk, (key, ov) -> {
            int v = (ov == null ? 0 : ov) + diff[0];
            return v <= 0 ? null : v;
        });
        dirty.set(true);
    }

    // ------------------------------------------------------------------ //
    //  YUKLEME
    // ------------------------------------------------------------------ //

    private static YamlConfiguration newYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().pathSeparator('|');
        return y;
    }

    /** Bir v3 dosyasini okur; sayilar mevcut degerlere EKLENIR. Okunamazsa false. */
    private boolean loadFile(File f) {
        if (!f.exists() || f.length() == 0) {
            return false;
        }
        YamlConfiguration y = newYaml();
        try {
            y.load(f);
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            return false;
        }
        ConfigurationSection s = y.getConfigurationSection("s");
        if (s == null) {
            return false;
        }
        for (String k : s.getKeys(false)) {
            int v = s.getInt(k);
            if (v <= 0) {
                continue;
            }
            String[] p = k.split(";", -1);
            if (p.length != 5) {
                continue;
            }
            sec.merge(k, v, Integer::sum);
            tot.merge(p[0] + ";" + p[1] + ";" + p[2] + ";" + p[4], v, Integer::sum);
        }
        return true;
    }

    /** Sunucu acilisi: diskten oku (script loadLimits, acilis yolu). */
    void load() {
        File main = new File(dir, "chunklimits_v3.yml");
        File bak = new File(dir, "chunklimits_v3.yml.bak");

        long t0 = System.nanoTime();
        if (loadFile(main)) {
            long ms = Math.round((System.nanoTime() - t0) / 1_000_000.0);
            Text.console(prefix + " &aChunk limitleri yüklendi. &7(" + sec.size() + " kayıt, " + ms + " ms)");
            return;
        }

        // Ana dosya var ama okunamadi -> uzerine yazilmadan once sakla
        boolean broken = false;
        if (main.exists() && main.length() > 0) {
            broken = true;
            File bz = new File(dir, "chunklimits_v3.yml.bozuk");
            try {
                Files.copy(main.toPath(), bz.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // kopyalanamasa bile ana dosyaya kayit yapilmadan once .bak denenir
            }
            Text.console(prefix + " &cchunklimits_v3.yml okunamadı! &7Dosya chunklimits_v3.yml.bozuk olarak saklandı.");
        }

        if (loadFile(bak)) {
            dirty.set(true);
            Text.console(prefix + " &eChunk limitleri yedekten (.bak) yüklendi. &7(" + sec.size() + " kayıt)");
            return;
        }

        if (broken) {
            Text.console(prefix + " &cYedek de okunamadı, sayaçlar boş başlatıldı. &7(Bloklar ilk yerleştirmede yeniden sayılır.)");
            return;
        }

        // --- v2.1 dosyasindan tek seferlik aktarim ---
        File old = new File(dir, "chunklimits.yml");
        if (!old.exists()) {
            Text.console(prefix + " &7Limit kaydı yok, temiz başlatıldı.");
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(old);
        ConfigurationSection s = y.getConfigurationSection("limits");
        if (s == null) {
            return;
        }
        for (String ok : s.getKeys(false)) {
            String[] p = ok.split("_", -1);
            int n = p.length;
            if (n < 3) {
                continue;
            }
            String cz = p[n - 1];
            String cx = p[n - 2];
            StringBuilder w = new StringBuilder(p[0]);
            for (int i = 1; i <= n - 3; i++) {
                w.append('_').append(p[i]);
            }
            for (String t : LEGACY_TYPES) {
                int v = y.getInt("limits." + ok + "." + t);
                if (v > 0) {
                    addCount(w + ";" + cx + ";" + cz, "L", t, v);
                }
            }
        }
        dirty.set(true);
        save();
        Text.console(prefix + " &eEski chunklimits.yml aktarıldı. &7(Eski dosyayı silebilirsin.)");
    }

    // ------------------------------------------------------------------ //
    //  KAYIT
    // ------------------------------------------------------------------ //

    /**
     * Degisiklik varsa kaydeder. Basarisizsa veri "kirli" kalir, sonraki turda
     * tekrar denenir. Ayni anda sadece bir kayit calisir.
     * @return true: kayit yapildi ya da gerek yoktu; false: kayit su an yapilamadi
     */
    public boolean save() {
        long now = System.currentTimeMillis();
        if (!saveLock.compareAndSet(0, now)) {
            long held = saveLock.get();
            if (now - held < 60_000) {
                return false;
            }
            if (!saveLock.compareAndSet(held, now)) {
                return false;
            }
            Text.console(prefix + " &eTakılı kalan kayıt kilidi açıldı.");
        }
        try {
            // Once false: kayit sirasinda gelen degisiklik bir sonraki turda yazilir
            if (!dirty.getAndSet(false)) {
                return true;
            }
            long t0 = System.nanoTime();
            boolean ok = write();
            long sms = Math.round((System.nanoTime() - t0) / 1_000_000.0);
            lag.onSave(sms);
            if (!ok) {
                dirty.set(true);
                Text.console(prefix + " &cChunk limit kaydı diske yazılamadı! &7Sonraki turda tekrar denenecek.");
            }
            return ok;
        } finally {
            // Sadece kilit hala bizdeyse birak
            saveLock.compareAndSet(now, 0);
        }
    }

    /** Kapanis: surmekte olan kayit varsa bitmesini bekle (en fazla ~5 sn), sonra kaydet. */
    void saveFinal() {
        for (int i = 0; i < 100; i++) {
            if (save()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Text.console(prefix + " &cKapanış kaydı tamamlanamadı! &7Son değişiklikler kaybolmuş olabilir.");
    }

    private boolean write() {
        try {
            Map<String, Object> snap = new HashMap<>(sec);
            YamlConfiguration y = newYaml();
            y.createSection("s", snap);
            String data = y.saveToString();
            return SafeFiles.write(new File(dir, "chunklimits_v3.yml"),
                    new File(dir, "chunklimits_v3.yml.tmp"),
                    new File(dir, "chunklimits_v3.yml.bak"), data);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
