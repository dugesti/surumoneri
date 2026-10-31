package tr.dugecraft.claim;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import tr.dugecraft.util.SafeFiles;
import tr.dugecraft.util.SkriptBridge;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * claim.sk'nin variables.csv'deki KALICI degiskenleri ({protchunk::*},
 * {claimprot_used::*}, {claim_kick_notice::*}, {claim_paypending::*},
 * {claim_lastalive}). Plugin bunlari kendi dosyasinda tutar
 * (plugins/DugeCraft/claim_kalici.yml) ve geri donus icin Skript
 * degiskenlerine de ayni degeri yazar.
 * Ilk acilista dosya yoksa degerler Skript'ten devralinir.
 */
public final class Persist {

    public static final String[] LISTS = {"protchunk", "claimprot_used", "claim_kick_notice", "claim_paypending"};

    private final File file;
    final Map<String, ConcurrentHashMap<String, Object>> lists = new ConcurrentHashMap<>();
    volatile Object lastAlive;
    private volatile boolean dirty;

    Persist(File file) {
        this.file = file;
        for (String l : LISTS) {
            lists.put(l, new ConcurrentHashMap<>());
        }
    }

    /** @return true ise Skript'ten devralindi */
    @SuppressWarnings("unchecked")
    boolean load() {
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            for (String l : LISTS) {
                ConfigurationSection s = y.getConfigurationSection(l);
                if (s == null) {
                    continue;
                }
                for (String k : s.getKeys(false)) {
                    lists.get(l).put(k.replace("~", "."), s.get(k));
                }
            }
            lastAlive = y.get("claim_lastalive");
            return false;
        }
        // Ilk acilis: Skript'teki kalici degiskenler devralinir
        for (String l : LISTS) {
            Object v = SkriptBridge.get(l + "::*");
            if (v instanceof Map<?, ?> mp) {
                for (Map.Entry<?, ?> e : mp.entrySet()) {
                    if (e.getValue() != null && !(e.getValue() instanceof Map)) {
                        lists.get(l).put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            }
        }
        lastAlive = SkriptBridge.get("claim_lastalive");
        dirty = true;
        save();
        return true;
    }

    public Object get(String list, String key) {
        return lists.get(list).get(key);
    }

    public Double num(String list, String key) {
        Object o = get(list, key);
        return o instanceof Number n ? n.doubleValue() : null;
    }

    public void set(String list, String key, Object value) {
        if (value == null) {
            lists.get(list).remove(key);
        } else {
            lists.get(list).put(key, value);
        }
        dirty = true;
        SkriptBridge.set(list + "::" + key, value);
    }

    public void setLastAlive(Object v) {
        lastAlive = v;
        dirty = true;
        SkriptBridge.set("claim_lastalive", v);
    }

    void save() {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (String l : LISTS) {
            Map<String, Object> sorted = new TreeMap<>(lists.get(l));
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                y.set(l + "." + e.getKey().replace(".", "~"), e.getValue());
            }
        }
        y.set("claim_lastalive", lastAlive);
        File dir = file.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        if (!SafeFiles.write(file, new File(file.getPath() + ".tmp"), new File(file.getPath() + ".bak"), y.saveToString())) {
            dirty = true;
        }
    }
}
