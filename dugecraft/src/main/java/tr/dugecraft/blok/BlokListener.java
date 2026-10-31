package tr.dugecraft.blok;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * bloksinir.sk olaylari. Oncelikler script ile ayni:
 * highest = karar (iptal), monitor = sayac guncelleme.
 * Script'te "if event.isCancelled() is true: stop" olan yerler
 * ignoreCancelled = true; olmayanlar false.
 */
final class BlokListener implements Listener {

    private final BlokModule m;

    BlokListener(BlokModule m) {
        this.m = m;
    }

    // ------------------------------------------------------------------ //
    //  YERLESTIRME / KIRMA
    // ------------------------------------------------------------------ //

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placeCheck(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        String t = m.types.get(b.getType());
        if (t == null) {
            return;
        }
        long t0 = System.nanoTime();
        boolean deny = m.tryPlace(e.getPlayer(), b, t);
        m.lag.op(System.nanoTime() - t0, b);
        if (deny) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placeCount(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        String t = m.types.get(b.getType());
        if (t == null) {
            return;
        }
        m.store.addCount(BlokModule.cidOf(b), Integer.toString(BlokModule.syOf(b.getY())), t, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakCount(BlockBreakEvent e) {
        Block b = e.getBlock();
        String t = m.types.get(b.getType());
        if (t == null) {
            return;
        }
        m.breakColumn(b, t);
    }

    // ------------------------------------------------------------------ //
    //  PISTON
    // ------------------------------------------------------------------ //

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pistonExtendCheck(BlockPistonExtendEvent e) {
        pistonCheck(e, e.getBlocks(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pistonRetractCheck(BlockPistonRetractEvent e) {
        pistonCheck(e, e.getBlocks(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pistonExtendApply(BlockPistonExtendEvent e) {
        pistonApply(e, e.getBlocks(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pistonRetractApply(BlockPistonRetractEvent e) {
        pistonApply(e, e.getBlocks(), false);
    }

    /** Hareket yonu: itmede pistonun baktigi yon, cekmede tersi. */
    private static BlockFace pistonFace(BlockPistonEvent e, boolean ext) {
        BlockData bd = e.getBlock().getBlockData();
        BlockFace face = bd instanceof Directional d ? d.getFacing() : e.getDirection();
        return ext ? face : face.getOppositeFace();
    }

    private void pistonCheck(BlockPistonEvent e, List<Block> list, boolean ext) {
        boolean has = false;
        for (Block b : list) {
            if ("piston".equals(m.types.get(b.getType()))) {
                has = true;
                break;
            }
        }
        if (!has) {
            return;
        }
        BlockFace face = pistonFace(e, ext);
        Map<String, Integer> in = new HashMap<>();
        for (Block b : list) {
            if ("piston".equals(m.types.get(b.getType()))) {
                String src = BlokModule.cidOf(b);
                String dst = BlokModule.cidOf(b.getRelative(face));
                if (!src.equals(dst)) {
                    in.merge(dst, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> en : in.entrySet()) {
            if (m.store.totGet(en.getKey() + ";piston") + en.getValue() > m.cfg.limitPiston) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void pistonApply(BlockPistonEvent e, List<Block> list, boolean ext) {
        BlockFace face = null;
        for (Block blk : list) {
            String t = m.types.get(blk.getType());
            if (t == null) {
                continue;
            }
            String sc = BlokModule.cidOf(blk);
            String ss = Integer.toString(BlokModule.syOf(blk.getY()));
            if (t.equals("piston")) {
                if (face == null) {
                    face = pistonFace(e, ext);
                }
                Block to = blk.getRelative(face);
                String dc = BlokModule.cidOf(to);
                String ds = Integer.toString(BlokModule.syOf(to.getY()));
                if (!(sc + ";" + ss).equals(dc + ";" + ds)) {
                    m.store.addCount(sc, ss, "piston", -1);
                    m.store.addCount(dc, ds, "piston", 1);
                }
            } else {
                m.store.addCount(sc, ss, t, -1);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  BUYUME
    // ------------------------------------------------------------------ //

    @EventHandler(priority = EventPriority.HIGHEST)
    public void grow(BlockGrowEvent e) {
        BlockState ns = e.getNewState();
        Material n = ns.getType();

        // Iptal kontrolunden ONCE: sap buyumesini baska eklenti iptal etse bile
        // kemik tozu izi birakilmali.
        if (n == Material.MELON_STEM || n == Material.PUMPKIN_STEM) {
            if (ns.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge()) {
                m.marks.put(BlokModule.melonKey(e.getBlock()), System.nanoTime());
            }
        }

        if (e.isCancelled()) {
            return;
        }

        switch (n) {
            case CACTUS -> {
                e.setCancelled(true);
                m.growPop(e.getBlock(), Material.CACTUS, m.cfg.cactusDropChance, true, m.farmMult(e.getBlock()));
            }
            case CACTUS_FLOWER -> e.setCancelled(true);
            case MELON -> {
                if (BlokModule.roll() <= m.cfg.cropCancel) {
                    e.setCancelled(true);
                    return;
                }
                Block b = e.getBlock();
                if (m.melonPress(b)) {
                    e.setCancelled(true);
                    if (!m.melonBoneMeal(b)) {
                        m.melonPop(b, m.farmMult(b));
                    }
                } else if (m.farmMult(b) > 1) {
                    // Pressiz karpuz blogu olusur; 2x Farm'da 3-7 bonus dilim
                    if (!m.melonBoneMeal(b)) {
                        m.melonPop(b, 1);
                    }
                }
            }
            case PUMPKIN -> {
                if (BlokModule.roll() <= m.cfg.cropCancel) {
                    e.setCancelled(true);
                    return;
                }
                Block b = e.getBlock();
                if (m.melonPress(b)) {
                    e.setCancelled(true);
                    if (!m.melonBoneMeal(b)) {
                        m.pumpkinPop(b, m.farmMult(b));
                    }
                } else if (m.farmMult(b) > 1) {
                    if (!m.melonBoneMeal(b)) {
                        m.farmOut(b, Material.PUMPKIN, 1);
                    }
                }
            }
            case WHEAT, CARROTS, POTATOES, BEETROOTS, SUGAR_CANE, MELON_STEM, PUMPKIN_STEM, NETHER_WART -> {
                if (BlokModule.roll() <= m.cfg.cropCancel) {
                    e.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    /** Bambu ve su yosunu BlockSpreadEvent ile buyur; diger yayilmalarda hemen cikilir. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void spread(BlockSpreadEvent e) {
        Material n = e.getNewState().getType();
        if (n == Material.BAMBOO) {
            if (e.isCancelled()) {
                return;
            }
            e.setCancelled(true);
            Block src = e.getSource();
            if (src.getType() == Material.BAMBOO_SAPLING) {
                // Fidan -> tek blokluk govde. Urun DUSMEZ (kemik tozu dupe'u).
                src.setType(Material.BAMBOO);
                return;
            }
            int bm = m.farmMult(e.getBlock());
            if (bm > 1 && m.bambooBoneMeal(src)) {
                bm = 1;
            }
            m.growPop(e.getBlock(), Material.BAMBOO, m.cfg.bambooDropChance, true, bm);
        } else if (n == Material.KELP) {
            if (e.isCancelled()) {
                return;
            }
            e.setCancelled(true);
            m.growPop(e.getBlock(), Material.KELP, m.cfg.kelpDropChance, false, m.farmMult(e.getBlock()));
        }
    }

    // ------------------------------------------------------------------ //
    //  SU YOSUNUNA KEMIK TOZU
    // ------------------------------------------------------------------ //

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fertilizeCheck(BlockFertilizeEvent e) {
        Material n = e.getBlock().getType();
        if (n != Material.KELP && n != Material.KELP_PLANT) {
            return;
        }
        int add = kelpFert(e, false);
        if (add <= 0) {
            return;
        }
        String cid = BlokModule.cidOf(e.getBlock());
        if (m.store.totGet(cid + ";kelp") + add > m.cfg.limitKelp) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            if (p != null) {
                m.denyMsg(p, m.cfg.prefix + " &cBu chunkta en fazla &e" + m.cfg.limitKelp + " &cadet su yosunu olabilir!");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fertilizeCount(BlockFertilizeEvent e) {
        Material n = e.getBlock().getType();
        if (n == Material.KELP || n == Material.KELP_PLANT) {
            kelpFert(e, true);
        }
    }

    /** Olay aninda dunya henuz degismedi: eskiden yosun olmayan yerdeki yeni yosunlar. */
    private int kelpFert(BlockFertilizeEvent e, boolean apply) {
        int add = 0;
        for (BlockState s : e.getBlocks()) {
            if ("kelp".equals(m.types.get(s.getType()))) {
                if (m.types.get(s.getBlock().getType()) == null) {
                    add++;
                    if (apply) {
                        m.store.addCount(BlokModule.cidOf(s.getWorld(), s.getX(), s.getZ()),
                                Integer.toString(BlokModule.syOf(s.getY())), "kelp", 1);
                    }
                }
            }
        }
        return add;
    }

    // ------------------------------------------------------------------ //
    //  BAMBU KEMIK TOZU IZI
    // ------------------------------------------------------------------ //

    /** Oyuncu bambuya kemik tozu basiyor (buyume olayindan ONCE gelir). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void bambooClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.BAMBOO) {
            return;
        }
        Player p = e.getPlayer();
        if (p.getInventory().getItemInMainHand().getType() == Material.BONE_MEAL
                || p.getInventory().getItemInOffHand().getType() == Material.BONE_MEAL) {
            m.marks.put(BlokModule.bambooKey(b), System.nanoTime());
        }
    }

    /** Dagitici kemik tozu atiyor: onundeki blok isaretlenir. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void dispense(BlockDispenseEvent e) {
        if (e.getItem().getType() != Material.BONE_MEAL) {
            return;
        }
        Block d = e.getBlock();
        // Sadece firlatici kemik tozunu kullanir (birakici/crafter sadece atar)
        if (d.getType() != Material.DISPENSER) {
            return;
        }
        if (d.getBlockData() instanceof Directional dir) {
            m.marks.put(BlokModule.bambooKey(d.getRelative(dir.getFacing())), System.nanoTime());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        m.msgCool.remove(e.getPlayer().getUniqueId());
    }
}
