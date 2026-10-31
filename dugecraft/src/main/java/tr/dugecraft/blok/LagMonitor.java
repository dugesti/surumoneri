package tr.dugecraft.blok;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import tr.dugecraft.util.Text;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * bloksinir.sk LAG UYARI SISTEMI. Ayni olcumler, ayni esikler, ayni mesajlar.
 * Sayaclar kilitsiz (LongAdder); kontrol dakikada bir global bolgede calisir.
 */
public final class LagMonitor {

    private final BlokConfig cfg;

    final LongAdder scanNs = new LongAdder();
    final LongAdder scans = new LongAdder();
    final LongAdder drops = new LongAdder();
    final LongAdder direct = new LongAdder();
    final LongAdder spikes = new LongAdder();
    private final AtomicLong worst = new AtomicLong(0);
    /** "worst", "save", "tpswho" */
    final Map<String, Object> info = new ConcurrentHashMap<>();
    /** /bloklag icin son dakikanin ozeti */
    final Map<String, Object> stats = new ConcurrentHashMap<>();
    private final Map<String, Double> cool = new ConcurrentHashMap<>();

    private ObjectName osName;
    private Method regionTps;

    LagMonitor(BlokConfig cfg) {
        this.cfg = cfg;
        try {
            osName = new ObjectName("java.lang:type=OperatingSystem");
        } catch (Exception e) {
            osName = null;
        }
        // Folia'da bolge bazli TPS API'si var, Paper'da tek global TPS.
        for (Method m : Bukkit.getServer().getClass().getMethods()) {
            if (m.getName().equals("getRegionTPS") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == Location.class) {
                regionTps = m;
                break;
            }
        }
    }

    static double unixNow() {
        return System.currentTimeMillis() / 1000.0;
    }

    /** Tek bir yerlestirme kontrolunun suresi. Esigi asanlar sayilir. */
    void op(long ns, Block b) {
        if (ns < cfg.warnSpikeMs * 1_000_000L) {
            return;
        }
        spikes.increment();
        long cur;
        do {
            cur = worst.get();
            if (ns <= cur) {
                return;
            }
        } while (!worst.compareAndSet(cur, ns));
        long ms = Math.round(ns / 1_000_000.0);
        info.put("worst", ms + " ms @ " + b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ());
    }

    void onSave(long sms) {
        info.put("save", sms);
        if (sms >= cfg.warnSaveMs) {
            warn("save", cfg.prefix + " &cbloksinir: &fDiske kayıt &e" + sms + " ms &fsürdü. &7Kayıt dosyası çok büyümüş olabilir.");
        }
    }

    /** Ayni turden uyari en fazla warn_repeat_minutes'te bir gonderilir. */
    void warn(String key, String msg) {
        double now = unixNow();
        Double last = cool.get(key);
        if (last != null && now - last < cfg.warnRepeatMinutes * 60.0) {
            return;
        }
        cool.put(key, now);
        Text.console(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(cfg.warnPerm)) {
                Text.send(p, msg);
            }
        }
    }

    /** Java isleminin CPU kullanimi (%), bir ondalik. Okunamazsa -1. */
    double cpuPercent() {
        if (osName == null) {
            return -1;
        }
        try {
            MBeanServer srv = ManagementFactory.getPlatformMBeanServer();
            Object v = srv.getAttribute(osName, "ProcessCpuLoad");
            if (!(v instanceof Number n)) {
                return -1;
            }
            double d = n.doubleValue();
            if (d < 0) {
                return -1;
            }
            return Math.round(d * 1000) / 10.0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** TPS dizisinden 1 dakikalik ortalama (Folia: 5s,15s,1m,..., Paper: 1m,5m,15m). */
    static double tpsOf(double[] arr) {
        int n = arr.length;
        int i = 0;
        if (n >= 5) {
            i = 2;
        } else if (n == 4) {
            i = 1;
        }
        double v = Math.min(arr[i], 20);
        return Math.round(v * 10) / 10.0;
    }

    /** Oyuncularin bulundugu bolgeler arasindaki en dusuk TPS. Oyuncu yoksa -1. */
    double worstTps() {
        double min = -1;
        Player who = null;
        boolean global = regionTps == null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            double[] arr;
            if (global) {
                arr = Bukkit.getTPS();
            } else {
                try {
                    arr = (double[]) regionTps.invoke(Bukkit.getServer(), p.getLocation());
                } catch (Exception e) {
                    arr = null;
                }
            }
            if (arr != null && arr.length > 0) {
                double tps = tpsOf(arr);
                if (min == -1 || tps < min) {
                    min = tps;
                    who = p;
                }
            }
            if (global) {
                break;
            }
        }
        if (who != null) {
            if (global) {
                info.put("tpswho", "tüm sunucu");
            } else {
                Location l = who.getLocation();
                info.put("tpswho", who.getName() + " yakını · " + l.getWorld().getName() + " "
                        + Math.round(l.getX()) + " " + Math.round(l.getZ()));
            }
        }
        return min;
    }

    /** Dakikada bir (script lagCheck). */
    void check() {
        long scanms = Math.round(scanNs.sumThenReset() / 1_000_000.0);
        long sc = scans.sumThenReset();
        long dr = drops.sumThenReset();
        long di = direct.sumThenReset();
        long sp = spikes.sumThenReset();
        Object w = info.remove("worst");
        worst.set(0);
        double cpu = cpuPercent();
        double tps = worstTps();

        stats.put("scanms", scanms);
        stats.put("scans", sc);
        stats.put("drops", dr);
        stats.put("direct", di);
        stats.put("spikes", sp);
        stats.put("cpu", cpu);
        stats.put("tps", tps);
        if (w != null) {
            stats.put("worst", w);
        } else {
            stats.remove("worst");
        }

        boolean busy = false;
        if (scanms >= cfg.warnScriptMs) {
            busy = true;
            warn("scan", cfg.prefix + " &cbloksinir: &fSon 1 dk'da taramalara &e" + scanms
                    + " ms &fharcandı &7(" + sc + " tarama). Detay: /bloklag");
        }
        if (sp > 0) {
            busy = true;
            warn("spike", cfg.prefix + " &cbloksinir: &e" + sp + " &fyerleştirme kontrolü " + cfg.warnSpikeMs
                    + " ms'yi aştı. &7En kötüsü: " + (w == null ? "<none>" : w));
        }
        if (dr >= cfg.warnDrops) {
            warn("drops", cfg.prefix + " &cbloksinir: &fFarmlar son 1 dk'da yere &e" + dr
                    + " &fürün düşürdü. &7Yerde eşya birikiyor olabilir.");
        }
        String src = busy ? "&cbu skript de şu an yoğun" : "&abu skript kaynaklı görünmüyor";
        if (tps >= 0 && tps < cfg.warnTps) {
            Object who = info.get("tpswho");
            warn("tps", cfg.prefix + " &cTPS düştü: &e" + Text.num(tps) + " &7(" + who + ") &8· " + src);
        }
        if (cpu >= cfg.warnCpu) {
            warn("cpu", cfg.prefix + " &cCPU yüksek: &e%" + Text.num(cpu) + " &7(Java işlemi) &8· " + src);
        }
    }
}
