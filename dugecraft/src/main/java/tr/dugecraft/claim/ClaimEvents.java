package tr.dugecraft.claim;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.InventoryHolder;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import tr.dugecraft.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** claim.sk 5. KORUMALAR + olay dinleyicileri (envanter tiklamalari ClaimClicks'te). */
@SuppressWarnings("deprecation")
final class ClaimEvents implements Listener {

    static final Set<String> WILD_BAN = Set.of("REDSTONE_WIRE", "REDSTONE_TORCH", "REDSTONE_WALL_TORCH", "REDSTONE_BLOCK", "REPEATER",
            "COMPARATOR", "OBSERVER", "PISTON", "STICKY_PISTON", "DISPENSER", "DROPPER", "HOPPER", "CRAFTER", "DAYLIGHT_DETECTOR", "TARGET",
            "LEVER", "TRIPWIRE", "TRIPWIRE_HOOK", "REDSTONE_LAMP", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR", "POWERED_RAIL", "DETECTOR_RAIL",
            "ACTIVATOR_RAIL", "SPAWNER", "BEACON", "LANTERN", "SOUL_LANTERN", "CACTUS", "MELON_STEM", "PUMPKIN_STEM", "KELP", "KELP_PLANT",
            "BAMBOO", "BAMBOO_SAPLING");
    static final Map<String, String> LOG_CONT = new HashMap<>();
    static final Map<String, String> LOG_BRK = new HashMap<>();

    static {
        String[][] c = {{"CHEST", "sandık"}, {"BARREL", "varil"}, {"SHULKER_BOX", "shulker"}, {"HOPPER", "huni"}, {"DISPENSER", "fırlatıcı"},
                {"DROPPER", "bırakıcı"}, {"FURNACE", "fırın"}, {"BLAST_FURNACE", "maden fırını"}, {"SMOKER", "tütsüleyici"},
                {"BREWING", "simya standı"}, {"CRAFTER", "crafter"}};
        for (String[] r : c) {
            LOG_CONT.put(r[0], r[1]);
        }
        String[][] b = {{"SPAWNER", "spawner"}, {"BEACON", "işaret feneri"}, {"LANTERN", "fener"}, {"SOUL_LANTERN", "ruh feneri"},
                {"CHEST", "sandık"}, {"TRAPPED_CHEST", "tuzaklı sandık"}, {"BARREL", "varil"}, {"HOPPER", "huni"}, {"DISPENSER", "fırlatıcı"},
                {"DROPPER", "bırakıcı"}, {"FURNACE", "fırın"}, {"BLAST_FURNACE", "maden fırını"}, {"SMOKER", "tütsüleyici"},
                {"BREWING_STAND", "simya standı"}, {"ENDER_CHEST", "ender sandığı"}, {"RESPAWN_ANCHOR", "yeniden doğma çapası"}};
        for (String[] r : b) {
            LOG_BRK.put(r[0], r[1]);
        }
    }

    private final ClaimModule m;
    /** {-farmer_guard::<cid>} ve {-farmer_guard_loc::<cid>} */
    final ConcurrentHashMap<String, Double> farmerGuard = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Location> farmerGuardLoc = new ConcurrentHashMap<>();
    /** {-tower_brk::<uuid>} */
    private final ConcurrentHashMap<String, String> towerBrk = new ConcurrentHashMap<>();
    /** {-log_last}, {-log_lastT} */
    private final ConcurrentHashMap<String, String> logLast = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> logLastT = new ConcurrentHashMap<>();

    ClaimEvents(ClaimModule m) {
        this.m = m;
    }

    private String pf() {
        return m.cfg.prefix;
    }

    private static String typeText(Material mt) {
        return mt.name().toLowerCase().replace('_', ' ');
    }

    // ================================================================== //
    //  CIFTCI (item spawn) + OLUM KORUMASI
    // ================================================================== //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void playerDeathGuard(PlayerDeathEvent e) {
        Location l = e.getEntity().getLocation();
        String gcid = ClaimModule.chunkId(l);
        farmerGuard.put(gcid, ClaimModule.now());
        farmerGuardLoc.put(gcid, l);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void itemSpawn(ItemSpawnEvent e) {
        Item it = e.getEntity();
        ItemStack is = it.getItemStack();
        String matchKey = Farmer.MAT.get(is.getType().name());
        if (matchKey == null) {
            return;
        }
        if (it.getThrower() != null) {
            return;
        }
        if (is.hasItemMeta() && is.getItemMeta().hasDisplayName()) {
            return;
        }
        Location el = e.getLocation();
        String cid = ClaimModule.chunkId(el);
        Double guard = farmerGuard.get(cid);
        if (guard != null) {
            if (ClaimModule.now() - guard < 4) {
                Location gl = farmerGuardLoc.get(cid);
                if (gl != null && gl.getWorld().equals(el.getWorld()) && el.distance(gl) <= 3) {
                    return;
                }
            } else {
                farmerGuard.remove(cid);
                farmerGuardLoc.remove(cid);
            }
        }
        if (m.farmerTake(cid, matchKey, is.getAmount())) {
            e.setCancelled(true);
        }
    }

    /** 2x FARM: mob droplari (HIGH). */
    @EventHandler(priority = EventPriority.HIGH)
    public void mobDeath(EntityDeathEvent e) {
        LivingEntity en = e.getEntity();
        String tn = en.getType().name();
        if (tn.equals("PLAYER") || tn.equals("ARMOR_STAND")) {
            return;
        }
        String cid = ClaimModule.chunkId(en.getLocation());
        Claim c = m.store.get(cid);
        String mc = c == null ? null : c.mainCid;
        if (mc == null) {
            return;
        }
        Claim mcl = m.store.get(mc);
        if (mcl == null || !mcl.upgOn("farm2x") || !mcl.farmerOn()) {
            return;
        }
        if (en instanceof InventoryHolder) {
            return;
        }
        Set<String> eqT = new HashSet<>();
        EntityEquipment eq = en.getEquipment();
        if (eq != null) {
            for (ItemStack i : new ItemStack[]{eq.getItemInMainHand(), eq.getItemInOffHand(), eq.getHelmet(), eq.getChestplate(), eq.getLeggings(), eq.getBoots()}) {
                if (i != null) {
                    eqT.add(i.getType().name());
                }
            }
        }
        List<ItemStack> add = new ArrayList<>();
        for (ItemStack is : e.getDrops()) {
            String dn = is.getType().name();
            if (Farmer.MAT.containsKey(dn) && !eqT.contains(dn)) {
                boolean ok = !(is.hasItemMeta() && is.getItemMeta().hasDisplayName());
                if (ok) {
                    add.add(is.clone());
                }
            }
        }
        if (!add.isEmpty()) {
            e.getDrops().addAll(add);
        }
    }

    // ================================================================== //
    //  TEMEL KORUMALAR (Skript "on break/place/right click..." = NORMAL)
    // ================================================================== //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        Location l = b.getLocation();
        if (b.getType() == Material.NOTE_BLOCK && m.towers != null) {
            World w = b.getWorld();
            if (m.towers.isTowerAt(w, b.getX(), b.getY(), b.getZ()) || m.towers.isTowerAt(w, b.getX(), b.getY() - 1, b.getZ())) {
                return;
            }
        }
        if (b.getType() == Material.LODESTONE && m.isStoneAt(l)) {
            e.setCancelled(true);
            m.pre(p, "&cClaim taşı kırılamaz! &7Yerini menüden değiştirebilirsin.");
            ClaimModule.soundAt(p.getLocation(), "entity.villager.no", 1, 1);
            return;
        }
        if (typeText(b.getType()).contains("spawner")) {
            if (!m.canInteract(p, l, "spawner_break")) {
                e.setCancelled(true);
                m.denyNotice(p, l, pf() + " <#ff4b1f>Bu bölgede spawner kırma yetkiniz yok!");
            }
        } else if (!m.canInteract(p, l, "break")) {
            e.setCancelled(true);
            m.denyNotice(p, l, pf() + " <#ff4b1f>Bu bölgede blok kırma yetkiniz yok!");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!m.canInteract(e.getPlayer(), e.getBlock().getLocation(), "place")) {
            e.setCancelled(true);
            m.denyNotice(e.getPlayer(), e.getBlock().getLocation(), pf() + " <#ff4b1f>Bu bölgede blok koyma yetkiniz yok!");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();
        Location l = b.getLocation();
        if (b.getType() == Material.LODESTONE && e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            if (stoneClick(e, p, l)) {
                return;
            }
        }
        String t = typeText(b.getType());
        if (t.contains("chest") || t.contains("barrel") || t.contains("furnace") || t.contains("hopper")) {
            if (!m.canInteract(p, l, "chest")) {
                e.setCancelled(true);
                m.denyNotice(p, l, pf() + " <#ff4b1f>Sandık erişimi yetkiniz yok!");
            }
        } else if (t.contains("door") || t.contains("trapdoor") || t.contains("lever") || t.contains("button") || t.contains("repeater") || t.contains("comparator")) {
            if (!m.canInteract(p, l, "interact")) {
                e.setCancelled(true);
                m.denyNotice(p, l, pf() + " <#ff4b1f>Kapı ve şalterleri kullanma yetkiniz yok!");
            }
        }
    }

    /** "on right click on lodestone": claim tasi menusu. true -> olay islendi. */
    private boolean stoneClick(PlayerInteractEvent e, Player p, Location l) {
        String key = ClaimModule.stoneKey(l);
        String found = "";
        Set<String> s = m.stonesByChunk.get(ClaimModule.chunkId(l));
        if (s != null) {
            for (String mc : s) {
                Claim c = m.store.get(mc);
                if (c != null && c.stone != null && ClaimModule.stoneKey(c.stone).equals(key)) {
                    found = mc;
                }
            }
        }
        if (found.isEmpty()) {
            return false;
        }
        e.setCancelled(true);
        String u = ClaimModule.uuid(p);
        Claim c = m.store.get(found);
        boolean can = (c != null && u.equals(c.owner)) || m.isMemberUuid(found, u) || p.hasPermission("claim.admin") || ClaimModule.isKurucu(p);
        if (!can) {
            m.pre(p, "<#ff4b1f>Bu claim'in menüsünü açma yetkin yok.");
            ClaimModule.soundAt(p.getLocation(), "entity.villager.no", 1, 1);
            return true;
        }
        m.refreshHolo(found);
        ClaimModule.soundAt(p.getLocation(), "block.note_block.harp", 0.7f, 1.8f);
        m.menus.claimMenu(p, found);
        return true;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void bucketEmpty(PlayerBucketEmptyEvent e) {
        Location l = e.getBlock().getLocation();
        if (!m.canInteract(e.getPlayer(), l, "place")) {
            e.setCancelled(true);
            m.denyNotice(e.getPlayer(), l, pf() + " <#ff4b1f>Bu bölgede kova kullanma yetkiniz yok!");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void bucketFill(PlayerBucketFillEvent e) {
        Location l = e.getBlock().getLocation();
        if (!m.canInteract(e.getPlayer(), l, "break")) {
            e.setCancelled(true);
            m.denyNotice(e.getPlayer(), l, pf() + " <#ff4b1f>Bu bölgede kova kullanma yetkiniz yok!");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void burn(BlockBurnEvent e) {
        if (m.protectLeftAt(e.getBlock().getLocation()) > 0) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        Player p = e.getPlayer();
        if (p == null) {
            return;
        }
        Location l = e.getBlock().getLocation();
        if (!m.canInteract(p, l, "place")) {
            e.setCancelled(true);
            m.denyNotice(p, l, pf() + " <#ff4b1f>Bu bölgede ateş yakma yetkiniz yok!");
        }
    }

    private static boolean protectedEntity(Entity en) {
        return en instanceof ItemFrame || en instanceof Painting || en instanceof ArmorStand || en instanceof Boat || en instanceof Minecart;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void entityDamage(EntityDamageEvent e) {
        Entity v = e.getEntity();
        if (v instanceof Player) {
            // Tasa isinlanirken bogulma hasari alinmaz
            if (e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
                Session s = m.sessions.get(v.getUniqueId());
                if (s != null && s.tpStone != null) {
                    e.setCancelled(true);
                }
            }
            return;
        }
        if (!(e instanceof EntityDamageByEntityEvent be)) {
            return;
        }
        Player a = attackerPlayer(be.getDamager());
        if (a == null || !protectedEntity(v)) {
            return;
        }
        if (!m.canInteract(a, v.getLocation(), "break")) {
            e.setCancelled(true);
            m.denyNotice(a, v.getLocation(), pf() + " <#ff4b1f>Bu bölgede eşya kırma yetkiniz yok!");
        }
    }

    /** Skript "attacker": mermi ise atan oyuncu. */
    static Player attackerPlayer(Entity d) {
        if (d instanceof Player p) {
            return p;
        }
        if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void entityInteract(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (!protectedEntity(en)) {
            return;
        }
        if (!m.canInteract(e.getPlayer(), en.getLocation(), "interact")) {
            e.setCancelled(true);
            m.denyNotice(e.getPlayer(), en.getLocation(), pf() + " <#ff4b1f>Bu bölgede etkileşim yetkiniz yok!");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void vehicleDestroy(VehicleDestroyEvent e) {
        Player a = e.getAttacker() == null ? null : attackerPlayer(e.getAttacker());
        if (a == null) {
            return;
        }
        Location l = e.getVehicle().getLocation();
        if (!m.canInteract(a, l, "break")) {
            e.setCancelled(true);
            m.denyNotice(a, l, pf() + " <#ff4b1f>Bu bölgede araç kırma yetkiniz yok!");
        }
    }

    /** Claim tasi patlamayla yok olamaz (Skript "on explode"). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void explodeStone(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> b.getType() == Material.LODESTONE && m.isStoneAt(b.getLocation()));
    }

    // ================================================================== //
    //  SPAWNER KORUMASI (LOWEST) + LOG DINLEYICILERI (MONITOR)
    // ================================================================== //

    @EventHandler(priority = EventPriority.LOWEST)
    public void spawnerBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.SPAWNER) {
            Player p = e.getPlayer();
            if (!m.canInteract(p, b.getLocation(), "spawner_break")) {
                e.setCancelled(true);
                m.pre(p, "<#ff4b1f>Bu bölgede spawner kırma yetkiniz yok! &7(Baskın açık olsa da spawner korunur.)");
                m.alog("ENGEL", p.getName() + "(" + p.getUniqueId() + ") yetkisiz SPAWNER kirma denemesi loc=" + m.altxt(b.getLocation()));
            }
            return;
        }
        if (b.getType() == Material.NOTE_BLOCK && m.towers != null && m.towers.isTowerAt(b.getWorld(), b.getX(), b.getY(), b.getZ())) {
            towerBrk.put(e.getPlayer().getUniqueId().toString(), b.getWorld().getName() + "_" + b.getX() + "_" + b.getY() + "_" + b.getZ());
        }
    }

    private void protectExplosion(List<Block> blocks, Location center, boolean lodestone) {
        boolean pn = m.protectNear(center);
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            boolean rm = pn && m.protectLeftAt(b.getLocation()) > 0;
            if (!rm) {
                if (b.getType() == Material.SPAWNER) {
                    rm = m.store.owner(ClaimModule.chunkId(b.getLocation())) != null;
                } else if (lodestone && b.getType() == Material.LODESTONE) {
                    rm = m.isStoneAt(b.getLocation());
                }
            }
            if (rm) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void explodeLowest(EntityExplodeEvent e) {
        protectExplosion(e.blockList(), e.getLocation(), false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void blockExplodeLowest(BlockExplodeEvent e) {
        protectExplosion(e.blockList(), e.getBlock().getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void breakLog(BlockBreakEvent e) {
        Player pl = e.getPlayer();
        String u = pl.getUniqueId().toString();
        if (e.isCancelled()) {
            return;
        }
        Block b = e.getBlock();
        String n = b.getType().name();
        String w = LOG_BRK.get(n);
        if (w == null) {
            if (n.endsWith("SHULKER_BOX")) {
                w = "shulker";
            } else if (n.equals("NOTE_BLOCK")) {
                String ctk = b.getWorld().getName() + "_" + b.getX() + "_" + b.getY() + "_" + b.getZ();
                if (ctk.equals(towerBrk.get(u))) {
                    w = "şok kulesi";
                }
            }
        }
        towerBrk.remove(u);
        if (w == null) {
            return;
        }
        Location loc = b.getLocation();
        Claim c = m.store.get(ClaimModule.chunkId(loc));
        String mc = c == null ? null : c.mainCid;
        if (mc != null) {
            m.clog(mc, "&e" + pl.getName() + " &c" + w + " kırdı &8(" + m.ltxt(loc) + ")");
            m.alog("KIRMA", pl.getName() + "(" + u + ") " + n + " kirdi claim=" + mc + " loc=" + m.altxt(loc));
        } else if (n.equals("SPAWNER")) {
            m.alog("KIRMA", pl.getName() + "(" + u + ") SPAWNER kirdi claim=YOK loc=" + m.altxt(loc));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placeLog(BlockPlaceEvent e) {
        String n = e.getBlockPlaced().getType().name();
        if (!n.equals("SPAWNER") && !n.equals("BEACON")) {
            return;
        }
        Player pl = e.getPlayer();
        Location loc = e.getBlockPlaced().getLocation();
        Claim c = m.store.get(ClaimModule.chunkId(loc));
        String mc = c == null ? null : c.mainCid;
        String mcTxt = "YOK";
        if (mc != null) {
            mcTxt = mc;
            m.clog(mc, "&e" + pl.getName() + " &a" + LOG_BRK.get(n) + " koydu &8(" + m.ltxt(loc) + ")");
        }
        m.alog("KOYMA", pl.getName() + "(" + pl.getUniqueId() + ") " + n + " koydu claim=" + mcTxt + " loc=" + m.altxt(loc));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void openLog(InventoryOpenEvent e) {
        String it = e.getInventory().getType().name();
        String w = LOG_CONT.get(it);
        if (w == null) {
            return;
        }
        Location loc = e.getInventory().getLocation();
        if (loc == null) {
            return;
        }
        Claim c = m.store.get(ClaimModule.chunkId(loc));
        String mc = c == null ? null : c.mainCid;
        if (mc == null) {
            return;
        }
        if (!(e.getPlayer() instanceof Player pl)) {
            return;
        }
        String u = pl.getUniqueId().toString();
        String key = it + "|" + m.ltxt(loc);
        double now = ClaimModule.now();
        if (key.equals(logLast.get(u)) && now - logLastT.getOrDefault(u, 0.0) < 20) {
            return;
        }
        logLast.put(u, key);
        logLastT.put(u, now);
        m.clog(mc, "&e" + pl.getName() + " &7" + w + " açtı &8(" + m.ltxt(loc) + ")");
        m.alog("ACMA", pl.getName() + "(" + u + ") " + it + " acti claim=" + mc + " loc=" + m.altxt(loc));
    }

    private void logExplosion(List<Block> blocks, String src, Location loc) {
        Map<String, Integer> cnt = new HashMap<>();
        for (Block b : blocks) {
            String n = b.getType().name();
            String w = LOG_BRK.get(n);
            if (w == null && n.endsWith("SHULKER_BOX")) {
                w = "shulker";
            }
            if (w != null) {
                Claim c = m.store.get(ClaimModule.chunkId(b.getLocation()));
                if (c != null && c.mainCid != null) {
                    cnt.merge(c.mainCid, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> en : cnt.entrySet()) {
            m.clog(en.getKey(), "&cPatlama &7(" + src + ") &f" + en.getValue() + " &7değerli blok yok etti &8(" + m.ltxt(loc) + ")");
            m.alog("PATLAMA", "claim=" + en.getKey() + " kaynak=" + src + " yok_edilen_degerli_blok=" + en.getValue() + " loc=" + m.altxt(loc));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explodeLog(EntityExplodeEvent e) {
        Location loc = e.getLocation();
        boolean any = false;
        for (int a : new int[]{0, 8, -8}) {
            for (int b : new int[]{0, 8, -8}) {
                if (m.store.owner(ClaimModule.chunkId(loc.clone().add(a, 0, b))) != null) {
                    any = true;
                }
            }
        }
        if (!any) {
            return;
        }
        Entity en = e.getEntity();
        String src = en.getType().name();
        if (src.equals("TNT") && en instanceof TNTPrimed t && t.getSource() != null) {
            src = "TNT - " + t.getSource().getName();
        }
        logExplosion(e.blockList(), src, loc);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void blockExplodeLog(BlockExplodeEvent e) {
        Location loc = e.getBlock().getLocation();
        if (m.store.owner(ClaimModule.chunkId(loc)) == null) {
            return;
        }
        logExplosion(e.blockList(), "blok patlaması", loc);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void changeBlock(EntityChangeBlockEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.SPAWNER) {
            if (m.store.owner(ClaimModule.chunkId(b.getLocation())) != null) {
                e.setCancelled(true);
            }
        } else if (b.getType() == Material.LODESTONE && m.isStoneAt(b.getLocation())) {
            e.setCancelled(true);
        }
    }

    /** Claimsiz alanda redstone / piston / spawner / fener / bitki yasagi (LOWEST). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void wildBan(BlockPlaceEvent e) {
        String n = e.getBlockPlaced().getType().name();
        boolean ban = WILD_BAN.contains(n) || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE");
        if (!ban) {
            return;
        }
        Player p = e.getPlayer();
        if (p.isOp() || p.hasPermission("claim.admin") || ClaimModule.isKurucu(p)) {
            return;
        }
        if (m.store.owner(ClaimModule.chunkId(e.getBlockPlaced().getLocation())) != null) {
            return;
        }
        e.setCancelled(true);
        m.pre(p, "<#ff4b1f>Bu bloğu claimsiz alana koyamazsın! &7Redstone, piston, spawner, fener ve çiftlik bitkileri (kaktüs, karpuz/kabak tohumu, su yosunu, bambu) sadece claim içinde kullanılabilir.");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void pistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (b.getType() == Material.LODESTONE && m.isStoneAt(b.getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void pistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (b.getType() == Material.LODESTONE && m.isStoneAt(b.getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ================================================================== //
    //  HAREKET: ISINLANMA INISI, TAS / HOLOGRAM, PLACEHOLDER
    // ================================================================== //

    static boolean tpSafeAt(World w, int x, int y, int z) {
        Block feet = w.getBlockAt(x, y, z);
        Block head = w.getBlockAt(x, y + 1, z);
        Block floor = w.getBlockAt(x, y - 1, z);
        if (!feet.isPassable() || !head.isPassable() || !floor.getType().isSolid()) {
            return false;
        }
        for (String n : new String[]{feet.getType().name(), head.getType().name()}) {
            for (String bad : new String[]{"LAVA", "FIRE", "WATER", "POWDER_SNOW", "COBWEB", "PORTAL", "SWEET_BERRY", "WITHER_ROSE"}) {
                if (n.contains(bad)) {
                    return false;
                }
            }
        }
        String fn = floor.getType().name();
        for (String bad : new String[]{"MAGMA", "CACTUS", "CAMPFIRE", "POINTED_DRIPSTONE"}) {
            if (fn.contains(bad)) {
                return false;
            }
        }
        return true;
    }

    private void stoneLand(Player p, Location sl) {
        World w = sl.getWorld();
        int sx = (int) Math.floor(sl.getX());
        int sy = (int) Math.floor(sl.getY());
        int sz = (int) Math.floor(sl.getZ());
        int[] ox = {0, 0, -1, 1};
        int[] oz = {-1, 1, 0, 0};
        float[] oy = {0, 180, -90, 90};
        for (int i = 0; i < 4; i++) {
            int tx = sx + ox[i];
            int tz = sz + oz[i];
            if (tpSafeAt(w, tx, sy, tz)) {
                Location dst = new Location(w, tx + 0.5, sy, tz + 0.5, oy[i], 20);
                p.teleportAsync(dst);
                p.setFallDistance(0);
                w.spawnParticle(Particle.PORTAL, dst, 40);
                return;
            }
        }
        if (tpSafeAt(w, sx, sy + 1, sz)) {
            p.setFallDistance(0);
            return;
        }
        landOnTop(p);
    }

    private void landOnTop(Player p) {
        Location pl = p.getLocation();
        Block top = pl.getWorld().getHighestBlockAt(pl);
        Location safe = top.getLocation().add(0, 1, 0);
        safe.setX(pl.getX());
        safe.setZ(pl.getZ());
        safe.setYaw(pl.getYaw());
        safe.setPitch(pl.getPitch());
        p.teleportAsync(safe);
        p.setFallDistance(0);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void moveLand(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Session s = m.session(p);
        if (s.tpFx) {
            s.tpFx = false;
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), 50);
        }
        if (s.tpStone != null) {
            Location sl = s.tpStone;
            if (ClaimModule.now() - s.tpStoneT > 30) {
                s.tpStone = null;
                s.tpStoneT = Double.NaN;
            } else if (p.getWorld().equals(sl.getWorld()) && p.getLocation().distance(sl) < 4) {
                s.tpStone = null;
                s.tpStoneT = Double.NaN;
                stoneLand(p, sl);
            }
        }
        if (!s.tpLand) {
            return;
        }
        s.tpLand = false;
        landOnTop(p);
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), 50);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void moveClaim(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Session s = m.session(p);
        String u = ClaimModule.uuid(p);
        double now = ClaimModule.now();
        if (!Double.isNaN(s.cmarkFbt) && now > s.cmarkFbt) {
            m.menus.revertMark(p);
        }
        Location pLoc = p.getLocation();
        String pcid = ClaimModule.chunkId(pLoc);
        if (!pcid.equals(s.plcChunk)) {
            s.plcChunk = pcid;
            m.menus.updatePlaceholder(p, pcid);
            m.menus.enterNotice(p, pcid);
        }
        String pendCid = s.stonePendingCid;
        if (pendCid != null) {
            Location pendLoc = s.stonePendingLoc;
            Claim pc = m.store.get(pendCid);
            if (pc == null || pc.owner == null || pendLoc == null) {
                s.stonePendingCid = null;
                s.stonePendingLoc = null;
            } else if (pendLoc.getWorld().equals(p.getWorld()) && pLoc.distance(pendLoc) < 24) {
                s.stonePendingCid = null;
                s.stonePendingLoc = null;
                if (pc.stone == null) {
                    if (m.towerNearLoc(pendLoc)) {
                        m.pre(p, "&eClaim taşı şok kulesinin dibine kurulamadı. &7Claim menüsünden &fClaim Taşını Taşı &7ile başka yere koy.");
                    } else {
                        m.placeStone(pendCid, pendLoc);
                    }
                }
            }
        }
        if (!Double.isNaN(s.stoneScan) && now - s.stoneScan < 2) {
            return;
        }
        s.stoneScan = now;
        String w = pLoc.getWorld().getName();
        World wObj = pLoc.getWorld();
        long cx = (long) Math.floor(pLoc.getX() / 16);
        long cz = (long) Math.floor(pLoc.getZ() / 16);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                String ck = w + "_" + (cx + ox) + "_" + (cz + oz);
                Location own = new Location(wObj, (cx + ox) * 16 + 8, pLoc.getY(), (cz + oz) * 16 + 8);
                if (!Bukkit.isOwnedByCurrentRegion(own)) {
                    continue;
                }
                ConcurrentHashMap<String, Location> clean = m.stoneCleanup.get(ck);
                if (clean != null) {
                    for (Map.Entry<String, Location> en : new ArrayList<>(clean.entrySet())) {
                        Location dl = en.getValue();
                        if (dl != null) {
                            Location dhl = new Location(dl.getWorld(), Math.floor(dl.getX()) + 0.5, Math.floor(dl.getY()) + 1.5, Math.floor(dl.getZ()) + 0.5);
                            for (Entity ent : dhl.getWorld().getNearbyEntities(dhl, 2.5, 2.5, 2.5)) {
                                if (ent instanceof TextDisplay && ent.getLocation().distance(dhl) <= 2.5) {
                                    ent.remove();
                                }
                            }
                            if (dl.getBlock().getType() == Material.LODESTONE) {
                                dl.getBlock().setType(Material.AIR);
                            }
                        }
                        clean.remove(en.getKey());
                    }
                    if (clean.isEmpty()) {
                        m.stoneCleanup.remove(ck, clean);
                    }
                }
                Set<String> stones = m.stonesByChunk.get(ck);
                if (stones == null) {
                    continue;
                }
                for (String mc : new ArrayList<>(stones)) {
                    Claim c = m.store.get(mc);
                    if (c == null || c.owner == null) {
                        // Claim silinmis: tas ve hologram temizlenir
                        stones.remove(mc);
                        continue;
                    }
                    if (Double.isNaN(c.holoTime) || now - c.holoTime >= 10) {
                        m.refreshHolo(mc);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String u = ClaimModule.uuid(p);
        Map<String, Object> kick = new HashMap<>();
        for (Map.Entry<String, Object> en : m.persist.lists.get("claim_kick_notice").entrySet()) {
            if (en.getKey().startsWith(u + "::")) {
                kick.put(en.getKey(), en.getValue());
            }
        }
        if (!kick.isEmpty()) {
            for (Object v : new java.util.TreeMap<>(kick).values()) {
                Text.send(p, String.valueOf(v));
            }
            for (String k : kick.keySet()) {
                m.persist.set("claim_kick_notice", k, null);
            }
        }
        Session s = m.session(p);
        String jcid = ClaimModule.chunkId(p.getLocation());
        s.plcChunk = jcid;
        m.menus.updatePlaceholder(p, jcid);
        s.abarLast = null;
        m.menus.enterNotice(p, jcid);
        p.getScheduler().runDelayed(m.plugin, t -> {
            if (!p.isOnline()) {
                return;
            }
            m.energyJoinWarn(p);
            String jcc = s.cchat;
            if (jcc != null) {
                if (m.chat.canUse(u, jcc)) {
                    Claim c = m.store.get(jcc);
                    m.pre(p, "<#ff9a3c>Claim sohbetin açık &8· &f" + (c == null ? "<none>" : c.claimName) + " &7(kapatmak için &e/claim sohbet&7)");
                } else {
                    s.cchat = null;
                }
            }
        }, null, 60L);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void quit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        String u = ClaimModule.uuid(p);
        Session s = m.sessions.remove(p.getUniqueId());
        for (String f : new String[]{"chunk", "name", "owner", "status", "chunks", "energy", "left"}) {
            m.plc(f, u, null);
        }
        m.protMsg.remove(u);
        m.menus.abarSnd.remove(u);
        if (s != null) {
            synchronized (s.cmarkFb) {
                s.cmarkFb.clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void chat(AsyncChatEvent e) {
        m.chat.onChat(e);
    }
}
