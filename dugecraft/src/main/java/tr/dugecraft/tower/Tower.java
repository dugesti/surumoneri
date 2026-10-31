package tr.dugecraft.tower;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Bir kule kaydi ({-towers::data::<anahtar>::*}).
 * cid  = MAIN CID (claim sahiplik / limit gruplamasi)
 * fcid = kulenin fiziksel durdugu chunk'in cid'i = kayit dosyasinin adi
 */
public final class Tower {

    public final String key;
    public final World world;
    /** Skript'teki "loc": kaydedilen konum (blok kosesi). */
    public final double x;
    public final double y;
    public final double z;

    public volatile String cid;
    public volatile String fcid;
    public volatile int level;
    public volatile int hp;
    public volatile double lastAttack;
    /** Son kazilma ani (can dolma sayaci), yoksa NaN */
    public volatile double hitAt = Double.NaN;
    public volatile boolean orphan;
    public volatile double orphanAt = Double.NaN;
    public volatile boolean remodel;
    /** Yerlestirme ani (olay sonradan iptal edilirse kayit geri alinir), yoksa NaN */
    public volatile double placedAt = Double.NaN;

    Tower(String key, World world, double x, double y, double z) {
        this.key = key;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Location loc() {
        return new Location(world, x, y, z);
    }

    public int bx() {
        return (int) Math.floor(x);
    }

    public int by() {
        return (int) Math.floor(y);
    }

    public int bz() {
        return (int) Math.floor(z);
    }

    /** sok.sk towerKey: "dunya_x_y_z" (tam sayi blok koordinati). */
    public static String keyOf(Location l) {
        return keyOf(l.getWorld(), l.getX(), l.getY(), l.getZ());
    }

    public static String keyOf(World w, double x, double y, double z) {
        return w.getName() + "_" + (long) Math.floor(x) + "_" + (long) Math.floor(y) + "_" + (long) Math.floor(z);
    }

    /** sok.sk chunkIDAt (claim.sk getChunkID ile ayni format). */
    public static String chunkId(Location l) {
        return chunkId(l.getWorld(), l.getX(), l.getZ());
    }

    public static String chunkId(World w, double x, double z) {
        return w.getName() + "_" + (long) Math.floor(x / 16) + "_" + (long) Math.floor(z / 16);
    }
}
