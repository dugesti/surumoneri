package tr.dugecraft.claim;

import org.bukkit.Location;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bir claim chunk'inin kaydi ({-claims::<cid>::*}). Her chunk ayri kayittir;
 * ana claim'in chunk'inda isMain = true, ek chunk'larda mainCid ana claim'i gosterir.
 * null = Skript'te "set degil".
 */
public final class Claim {

    public static final String[] PERMS = {"break", "place", "chest", "interact", "farmer", "teleport", "sethome", "spawner_break", "logs"};
    public static final String[] UPGRADES = {"farm2x", "night"};

    public final String cid;

    public volatile String owner;
    public volatile String ownerName;
    public volatile String claimName;
    /** Bitis zamani (unix sn); Skript'te dosyada yoksa 0 */
    public volatile Double expiry;
    public volatile Boolean isMain;
    public volatile String mainCid;
    public volatile int chunkCount;
    public volatile int centerY;
    public volatile Double protectUntil;
    public volatile Double energy;
    public volatile Double energyTime;
    public volatile String createdText;
    public volatile boolean needsSave;

    /** Uye UUID'leri (Skript listesi sirasi korunur) */
    public final List<String> members = new CopyOnWriteArrayList<>();
    /** perms::<uuid>::<yetki> */
    public final Map<String, Map<String, Boolean>> perms = new ConcurrentHashMap<>();

    /** upg::<id>::own / on / offt */
    public final Map<String, Boolean> upgOwn = new ConcurrentHashMap<>();
    public final Map<String, Boolean> upgOn = new ConcurrentHashMap<>();
    public final Map<String, Double> upgOffT = new ConcurrentHashMap<>();

    public volatile Boolean farmerStatus;
    public volatile int farmerLevel = 1;
    /** farmer::as_on::<urun> = acan kisinin UUID'si */
    public final Map<String, String> asOn = new ConcurrentHashMap<>();
    public final Map<String, String> asOnName = new ConcurrentHashMap<>();

    // --- claim tasi ({-stones::<cid>::*}) ---
    public volatile Location stone;
    public volatile org.bukkit.entity.Entity holo;
    public volatile double holoTime = Double.NaN;
    public volatile double moveTime = Double.NaN;

    Claim(String cid) {
        this.cid = cid;
    }

    public boolean main() {
        return Boolean.TRUE.equals(isMain);
    }

    /** main_cid, yoksa kendisi (Skript'te sik kullanilan kalip). */
    public String mainOrSelf() {
        String m = mainCid;
        return m == null ? cid : m;
    }

    public boolean perm(String uuid, String perm) {
        Map<String, Boolean> m = perms.get(uuid);
        return m != null && Boolean.TRUE.equals(m.get(perm));
    }

    public void setPerm(String uuid, String perm, Boolean val) {
        Map<String, Boolean> m = perms.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
        if (val == null) {
            m.remove(perm);
        } else {
            m.put(perm, val);
        }
    }

    public boolean upgOn(String id) {
        return Boolean.TRUE.equals(upgOn.get(id));
    }

    public boolean upgOwn(String id) {
        return Boolean.TRUE.equals(upgOwn.get(id));
    }

    public boolean farmerOn() {
        return Boolean.TRUE.equals(farmerStatus);
    }
}
