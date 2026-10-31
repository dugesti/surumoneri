package tr.dugecraft.claim;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Kule (sok.sk) ve blok (bloksinir.sk) modullerinin claim sistemine sordugu
 * her sey. claim.sk'deki ayni adli fonksiyonlarin / degiskenlerin karsiligi.
 * cid'ler claim.sk getChunkID formatindadir: "dunya_cx_cz".
 */
public interface ClaimApi {

    /** {-all_claims::*} dolu mu? (claim verisi bellekte; bos = yuklenmemis / hata) */
    boolean dataReady();

    /** {-claims::<cid>::owner} (UUID metni) ya da null */
    String owner(String cid);

    /** {-claims::<cid>::main_cid} ham deger ya da null */
    String mainCidRaw(String cid);

    /** {-claims::<cid>::owner_name} ya da null */
    String ownerName(String cid);

    /** {-claims::<mainCid>::members::*} contains uuid */
    boolean isMember(String mainCid, String uuid);

    /** claim.sk canInteract(p, loc, action) — gerekirse oyuncuya kendi mesajini yollar */
    boolean canInteract(Player p, Location loc, String action);

    /** claim.sk canBreakTower(p, cid) */
    boolean canBreakTower(Player p, String cid);

    /** claim.sk canManageTower(p, cid) */
    boolean canManageTower(Player p, String cid);

    /** claim.sk isRaidActive() */
    boolean isRaidActive();

    /** {-force_raid} ya da null */
    String forceRaid();

    /** claim.sk chunkProtectLeft(cid): yeni claim + gece korumasi kalan sn */
    double chunkProtectLeft(String cid);

    /** claim.sk newProtectLeft(cid) */
    double newProtectLeft(String cid);

    /** claim.sk chunkNightLeft(cid) */
    double chunkNightLeft(String cid);

    /** claim.sk fmtDur(sec): "5 sa 12 dk" */
    String fmtDur(double sec);

    /** claim.sk stoneNearTowerBase(loc) */
    boolean stoneNearTowerBase(Location loc);

    /** claim.sk reserveChunk(key, uuid): kisa sureli islem kilidi */
    boolean reserve(String key, String uuid);

    /** claim.sk releaseChunk(key) */
    void release(String key);

    /** claim.sk clog(mainCid, msg): claim kayitlari (oyuncu logu) */
    void clog(String mainCid, String msg);

    /** claim.sk alog(type, msg): yetkili logu */
    void alog(String type, String msg);

    /** claim.sk ltxt(loc) */
    String ltxt(Location loc);

    /** claim.sk altxt(loc) */
    String altxt(Location loc);
}
