package tr.dugecraft.farm;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Blok limit / farm modulunun claim sistemine sordugu sorular.
 * claim.sk Skript'te calistigi surece {@link SkriptFarmClaims} kullanilir;
 * claim modulu plugine tasininca kendi uygulamasi devreye girer.
 * Hepsi sadece veri okur, dunyaya dokunmaz (Folia: her thread'den cagrilabilir).
 */
public interface FarmClaims {

    /**
     * bloksinir.sk dropZone: blogun claim bolgesi. Claim icindeyse ana claim
     * kimligi (main_cid, yoksa chunk kimligi), degilse "".
     */
    String dropZone(World world, int blockX, int blockZ);

    /** bloksinir.sk farmMult: 2x Farm acik ve ciftci aktifse 2, yoksa 1. */
    int farmMult(World world, int blockX, int blockZ);

    /**
     * Urunu yere dusurmeden ciftciye vermeyi dener.
     * true -> ciftci aldi (satildi / depoya eklendi / depo dolu oldugu icin
     * silindi), urun yere DUSURULMEZ. false -> eskisi gibi yere dusur.
     */
    boolean farmerTake(Location loc, Material material, int amount);

    /** claim.sk getChunkID ile birebir ayni anahtar: "dunya_cx_cz". */
    static String chunkId(World world, int blockX, int blockZ) {
        return world.getName() + "_" + Math.floorDiv(blockX, 16) + "_" + Math.floorDiv(blockZ, 16);
    }
}
