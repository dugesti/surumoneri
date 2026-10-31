package tr.dugecraft.farm;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import tr.dugecraft.util.SkriptBridge;

/**
 * Gecis donemi: claim.sk hala Skript'te calisiyor. Claim verisi claim.sk'nin
 * RAM degiskenlerinden SADECE OKUNUR (bloksinir.sk'nin yaptigiyla ayni).
 * Urun her zaman yere dusurulur; claim.sk'nin "on item spawn" ciftcisi
 * eskisi gibi toplar. Boylece depo / oto satis mantigi tek yerde kalir.
 * Skript yoksa her yer "claimsiz" sayilir (bloksinir.sk'de claim.sk
 * yuklu degilken oldugu gibi).
 */
public final class SkriptFarmClaims implements FarmClaims {

    @Override
    public String dropZone(World world, int blockX, int blockZ) {
        String ck = FarmClaims.chunkId(world, blockX, blockZ);
        if (SkriptBridge.get("-claims::" + ck + "::owner") == null) {
            return "";
        }
        Object mc = SkriptBridge.get("-claims::" + ck + "::main_cid");
        if (mc != null) {
            return String.valueOf(mc);
        }
        return ck;
    }

    @Override
    public int farmMult(World world, int blockX, int blockZ) {
        String z = dropZone(world, blockX, blockZ);
        if (z.isEmpty()) {
            return 1;
        }
        if (!SkriptBridge.isTrue("-claims::" + z + "::upg::farm2x::on")) {
            return 1;
        }
        if (!SkriptBridge.isTrue("-claims::" + z + "::farmer::status")) {
            return 1;
        }
        return 2;
    }

    @Override
    public boolean farmerTake(Location loc, Material material, int amount) {
        return false;
    }
}
