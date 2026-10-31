package tr.dugecraft.claim;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import tr.dugecraft.farm.FarmClaims;

/**
 * Claim modulu aciksa blok limit / farm modulunun claim sorulari dogrudan
 * bellekten cevaplanir. Urun yere dusurulmeden ciftciye verilir: eskiden
 * bloksinir.sk urunu dusurur, claim.sk "on item spawn" ile toplardi; sonuc
 * ayni (ayni olum korumasi, ayni depo / oto satis kurali), ama arada esya
 * entity'si ve olay zinciri olusmaz.
 */
public final class ModuleFarmClaims implements FarmClaims {

    private final ClaimModule m;

    public ModuleFarmClaims(ClaimModule m) {
        this.m = m;
    }

    @Override
    public String dropZone(World world, int blockX, int blockZ) {
        String ck = FarmClaims.chunkId(world, blockX, blockZ);
        Claim c = m.store.get(ck);
        if (c == null || c.owner == null) {
            return "";
        }
        return c.mainCid != null ? c.mainCid : ck;
    }

    @Override
    public int farmMult(World world, int blockX, int blockZ) {
        String z = dropZone(world, blockX, blockZ);
        if (z.isEmpty()) {
            return 1;
        }
        Claim c = m.store.get(z);
        if (c == null || !c.upgOn("farm2x") || !c.farmerOn()) {
            return 1;
        }
        return 2;
    }

    @Override
    public boolean farmerTake(Location loc, Material material, int amount) {
        if (!m.isEnabled()) {
            return false;
        }
        String key = Farmer.MAT.get(material.name());
        if (key == null) {
            return false;
        }
        String cid = ClaimModule.chunkId(loc);
        if (m.events.guarded(cid, loc)) {
            return false;
        }
        return m.farmerTake(cid, key, amount);
    }
}
