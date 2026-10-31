package tr.dugecraft.tower;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import tr.dugecraft.util.Text;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * sok.sk olaylari. Skript'in kendi olaylari ("on place", "on damage"...)
 * NORMAL oncelik + iptal edilmisleri atlar (ignoreCancelled = true).
 * skript-reflect olaylari kendi oncelikleriyle ve iptal kontrolu script'te
 * nasilsa oyle.
 */
@SuppressWarnings("deprecation")
final class TowerListener implements Listener {

    private final TowerModule m;
    private final TowerUi ui;

    TowerListener(TowerModule m) {
        this.m = m;
        this.ui = m.ui;
    }

    private static double now() {
        return TowerModule.now();
    }

    // ================================================================== //
    //  YERLESTIRME
    // ================================================================== //

    /** Kule yerlestirme (Skript "on place"). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        if (hand.getType() != Material.NOTE_BLOCK) {
            // Hayalet kule temizligi (ikinci "on place")
            ghostCleanup(e);
            return;
        }
        int placeLvl = m.items.level(hand);
        if (placeLvl <= 0) {
            ghostCleanup(e);
            return;
        }
        Player p = e.getPlayer();
        Location loc = e.getBlock().getLocation();
        String cid = Tower.chunkId(loc);
        if (m.claims.owner(cid) == null) {
            e.setCancelled(true);
            Text.send(p, m.cfg.prefix + " <#f87171>Şok kulesini sadece bir claim içine yerleştirebilirsiniz!");
            return;
        }
        String mainCid = m.claims.mainCidRaw(cid);
        if (mainCid == null) {
            mainCid = cid;
        }
        String u = p.getUniqueId().toString();
        boolean canPlace = u.equals(m.claims.owner(mainCid)) || m.claims.isMember(mainCid, u);
        if (!canPlace) {
            e.setCancelled(true);
            Text.send(p, m.cfg.prefix + " <#f87171>Buraya şok kulesi koymak için yetkiniz yok!");
            return;
        }
        if (m.cfg.maxTowersPerClaim > 0 && m.store.byCid(mainCid).size() >= m.cfg.maxTowersPerClaim) {
            e.setCancelled(true);
            Text.send(p, m.cfg.prefix + " <#f87171>Bu claim'de en fazla <#fbbf24>" + m.cfg.maxTowersPerClaim + " <#f87171>kule olabilir!");
            p.getWorld().playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
            return;
        }
        if (m.claims.stoneNearTowerBase(loc)) {
            e.setCancelled(true);
            Text.send(p, m.cfg.prefix + " <#f87171>Şok kulesi claim taşının dibine konulamaz! <#9ca3af>Arada en az 1 blok boşluk bırak.");
            p.getWorld().playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
            return;
        }
        if (!m.claims.canInteract(p, loc, "place")) {
            e.setCancelled(true);
            return;
        }
        String selfKey = Tower.keyOf(loc);
        for (String other : List.copyOf(m.store.nearby(loc))) {
            if (other.equals(selfKey)) {
                m.unregister(other);
                m.clearTop(loc);
                continue;
            }
            Tower o = m.store.get(other);
            if (o == null || o.world != loc.getWorld()) {
                continue;
            }
            if (m.cfg.minTowerGap <= 0) {
                continue;
            }
            if (o.loc().distance(loc) < m.cfg.minTowerGap) {
                e.setCancelled(true);
                Text.send(p, m.cfg.prefix + " <#f87171>Kuleler arasında en az <#fbbf24>1 blok <#f87171>boşluk olmalı!");
                p.getWorld().playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
                return;
            }
        }
        String upM = TowerModule.mat(TowerModule.above(loc).getBlock());
        if (!upM.equals("AIR") && !upM.equals("CAVE_AIR") && !upM.equals("VOID_AIR")) {
            e.setCancelled(true);
            Text.send(p, m.cfg.prefix + " <#f87171>Kulenin üstünde <#fbbf24>1 blok boşluk <#f87171>olmalı!");
            p.getWorld().playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
            return;
        }
        Tower t = new Tower(selfKey, loc.getWorld(), loc.getX(), loc.getY(), loc.getZ());
        t.cid = mainCid;
        t.fcid = cid;
        t.level = placeLvl;
        t.hp = m.cfg.maxHp();
        t.lastAttack = 0;
        t.placedAt = now();
        m.store.register(t);
        m.applyModel(loc, placeLvl);
        Text.send(p, m.cfg.prefix + " <#4ade80>Şok Kulesi başarıyla kuruldu! Düşmanlara saldırmaya hazır.");
        p.getWorld().playSound(p.getLocation(), "block.beacon.activate", 1f, 1f);
    }

    /** Kayitli kule konumuna blok konabiliyorsa kulenin blogu YOK: kayit silinir. */
    private void ghostCleanup(BlockPlaceEvent e) {
        Location pl = e.getBlock().getLocation();
        if (m.store.nearby(pl).isEmpty()) {
            return;
        }
        if (m.items.level(e.getItemInHand()) > 0) {
            return;
        }
        String gk = Tower.keyOf(pl);
        if (m.store.isTowerKey(gk)) {
            m.unregister(gk);
            m.clearTop(pl);
            Text.console(m.cfg.prefix + " <#9ca3af>Blogu olmayan kule kaydi temizlendi: <#e5e7eb>" + gk);
        }
    }

    /** Yerlestirme baska eklenti tarafindan geri alindiysa kayit da geri alinir. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void placeUndone(BlockPlaceEvent e) {
        if (!e.isCancelled()) {
            return;
        }
        Location pbl = e.getBlockPlaced().getLocation();
        String pk = Tower.keyOf(pbl);
        Tower t = m.store.get(pk);
        if (t == null || !PState.set(t.placedAt) || now() - t.placedAt > 2) {
            return;
        }
        m.unregister(pk);
        m.clearTop(pbl);
        Text.console(m.cfg.prefix + " <#fbbf24>Kule yerleştirmesi başka bir eklenti tarafından iptal edildi, kayıt geri alındı: <#e5e7eb>" + pk);
    }

    // ================================================================== //
    //  KIRMA
    // ================================================================== //

    @EventHandler(priority = EventPriority.LOWEST)
    public void breakLowest(BlockBreakEvent e) {
        Block blk = e.getBlock();
        if (!TowerModule.isTowerMaterial(blk)) {
            return;
        }
        Location t = m.baseOf(blk.getLocation());
        if (t == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        m.markDigProof(p);
        m.hit(p, t);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void breakMonitor(BlockBreakEvent e) {
        Block blk = e.getBlock();
        String n = blk.getType().name();
        boolean isAir = n.equals("AIR");
        if (!isAir && !n.equals("CHORUS_PLANT") && !n.equals("NOTE_BLOCK")) {
            return;
        }
        Location bl = blk.getLocation();
        if (!isAir) {
            if (e.isCancelled() || !m.isTowerPart(bl)) {
                return;
            }
            e.setCancelled(true);
            Player p = e.getPlayer();
            PState s = m.ps(p);
            double nw = now();
            double lw = PState.set(s.bypasslog) ? s.bypasslog : 0;
            if (nw - lw > 10) {
                s.bypasslog = nw;
                Text.console(m.cfg.prefix + " <#fbbf24>Kule kırma iptali başka bir eklenti/skript tarafından kaldırılmıştı, tekrar iptal edildi <#4b5563>· <#e5e7eb>"
                        + p.getName() + " <#9ca3af>" + Tower.keyOf(bl));
            }
            return;
        }
        // Blok olay sirasinda elle silinmis: kayitli kuleyse geri koy
        Location base = bl;
        if (!m.store.isTowerKey(Tower.keyOf(bl))) {
            base = TowerModule.below(bl);
            if (!m.store.isTowerKey(Tower.keyOf(base))) {
                return;
            }
        }
        String bk = Tower.keyOf(base);
        int lv = m.levelOf(bk);
        String bm = TowerModule.mat(base.getBlock());
        if (bm.equals("AIR") || bm.equals("CAVE_AIR") || bm.equals("VOID_AIR")) {
            m.applyModel(base, lv);
        } else {
            m.placeTop(base);
        }
        m.restoreCnt.merge(bk, 1, Integer::sum);
        double rnw = now();
        double rlw = m.restoreLog.getOrDefault(bk, 0.0);
        if (rnw - rlw > 30) {
            m.restoreLog.put(bk, rnw);
            Integer rc = m.restoreCnt.remove(bk);
            Text.console(m.cfg.prefix + " <#fbbf24>Kule bloğu başka bir eklenti/skript tarafından silindi, geri konuldu <#4b5563>· <#9ca3af>"
                    + bk + " <#4b5563>· <#e5e7eb>" + e.getPlayer().getName() + " <#4b5563>· <#9ca3af>" + (rc == null ? 0 : rc)
                    + " kez <#4b5563>(30 sn'de bir yazılır, sebep: /sokkulesi dinleyiciler)");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void dropItem(BlockDropItemEvent e) {
        String n = e.getBlockState().getType().name();
        if (!n.equals("CHORUS_PLANT") && !n.equals("NOTE_BLOCK")) {
            return;
        }
        Location bl = e.getBlock().getLocation();
        Player p = e.getPlayer();
        String k = Tower.keyOf(bl);
        Tower t = m.store.get(k);
        if (t != null) {
            e.getItems().clear();
            int lv = t.level;
            boolean give = t.orphan || m.claims.canBreakTower(p, t.cid);
            m.unregister(k);
            m.clearTop(bl);
            if (give) {
                bl.getWorld().dropItemNaturally(bl, m.items.item(lv));
            }
            Text.console(m.cfg.prefix + " <#f87171>UYARI: kule korumayı atlayarak kırıldı <#4b5563>· <#e5e7eb>" + p.getName()
                    + " <#9ca3af>" + k + " Sv" + lv + " <#4b5563>· <#9ca3af>eşya verildi: " + give
                    + " <#4b5563>· <#9ca3af>kıran başka bir eklenti/skript olabilir");
            return;
        }
        Location below = TowerModule.below(bl);
        String bk = Tower.keyOf(below);
        if (m.store.isTowerKey(bk)) {
            e.getItems().clear();
            m.placeTop(below);
            Text.console(m.cfg.prefix + " <#f87171>UYARI: kulenin üst bloğu korumayı atlayarak kırıldı <#4b5563>· <#e5e7eb>"
                    + p.getName() + " <#9ca3af>" + bk + " <#4b5563>· <#9ca3af>geri konuldu");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void destroy(BlockDestroyEvent e) {
        Block blk = e.getBlock();
        if (!TowerModule.isTowerMaterial(blk)) {
            return;
        }
        if (m.isTowerPart(blk.getLocation())) {
            e.setCancelled(true);
        }
    }

    // ================================================================== //
    //  KAZMA BASILI TUTMA TAKIBI
    // ================================================================== //

    @EventHandler(priority = EventPriority.NORMAL)
    public void damageStart(BlockDamageEvent e) {
        Block blk = e.getBlock();
        if (!TowerModule.isTowerMaterial(blk)) {
            return;
        }
        Location bl = blk.getLocation();
        String tk = Tower.keyOf(bl);
        if (!m.store.isTowerKey(tk)) {
            if (!m.isTowerTop(bl)) {
                if (!m.adopt(bl)) {
                    return;
                }
                if (!m.store.isTowerKey(tk) && !m.isTowerTop(bl)) {
                    return;
                }
            }
            if (!m.store.isTowerKey(tk)) {
                tk = Tower.keyOf(TowerModule.below(bl));
            }
        }
        Player p = e.getPlayer();
        PState s = m.ps(p);
        double n = now();
        if (s.holding && tk.equals(s.tgt) && PState.set(s.last) && n - s.last <= 0.3) {
            s.sig = n;
            return;
        }
        s.clearMine();
        s.holding = true;
        s.tgt = tk;
        s.prog = 0;
        s.last = n;
        s.sig = n;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void damageAbort(BlockDamageAbortEvent e) {
        Player p = e.getPlayer();
        PState s = m.ps(p);
        if (!s.holding) {
            return;
        }
        Block ablk = e.getBlock();
        if (!TowerModule.isTowerMaterial(ablk)) {
            return;
        }
        Location abl = ablk.getLocation();
        String atk = Tower.keyOf(abl);
        if (!m.store.isTowerKey(atk)) {
            if (!m.isTowerTop(abl)) {
                return;
            }
            atk = Tower.keyOf(TowerModule.below(abl));
        }
        if (!atk.equals(s.tgt)) {
            return;
        }
        double had = s.prog;
        s.clearMine();
        if (PState.set(had) && had > 0) {
            Text.actionBar(p, "<#fbbf24>⚠ <#e5e7eb>Kuleyi kırmak için kazmayı <#fbbf24>basılı tut");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (m.dmgTest) {
            dmgTestSwing(p);
        }
        PState s = m.ps(p);
        if (!s.holding) {
            return;
        }
        if (m.store.nearby(p.getLocation()).isEmpty()) {
            return;
        }
        if (PState.set(s.rc) && now() - s.rc < 0.5) {
            return;
        }
        Block b = p.getTargetBlockExact(5);
        if (b == null || !TowerModule.isTowerMaterial(b)) {
            return;
        }
        Location loc = m.baseOf(b.getLocation());
        if (loc == null) {
            return;
        }
        m.raidTick(p, loc);
    }

    // ================================================================== //
    //  SAG TIK / MENU / MAGAZA
    // ================================================================== //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void rightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null || !TowerModule.isTowerMaterial(b)) {
            return;
        }
        Location t = m.baseOf(b.getLocation());
        if (t == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        m.towerBlockOk(t);
        PState s = m.ps(p);
        s.rc = now();
        s.prog = PState.NONE;
        s.last = PState.NONE;
        if (e.getHand() == EquipmentSlot.HAND) {
            ui.towerMenu(p, t);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void npcClick(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (!en.hasMetadata("NPC") || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String nn = org.bukkit.ChatColor.stripColor(en.getName());
        if (!nn.equals(m.cfg.shopNpcName)) {
            return;
        }
        m.ps(e.getPlayer()).shopPending = PState.NONE;
        ui.openShop(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void invClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        PState s = m.ps(p);
        Inventory top = e.getView().getTopInventory();
        if (s.towerMenu != null && top == s.towerMenu) {
            e.setCancelled(true);
            if (e.getRawSlot() == 15) {
                ui.upgradeClick(p);
            }
            return;
        }
        if (s.shop != null && top == s.shop) {
            e.setCancelled(true);
            ui.shopClick(p, s.shop, e.getRawSlot(), e.getCursor());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void invDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        PState s = m.ps(p);
        Inventory top = e.getView().getTopInventory();
        if ((s.towerMenu != null && top == s.towerMenu) || (s.shop != null && top == s.shop)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void invClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        PState s = m.ps(p);
        Inventory inv = e.getInventory();
        if (s.towerMenu != null && inv == s.towerMenu) {
            s.towerMenu = null;
            s.towerLoc = null;
        }
        if (s.shop != null && inv == s.shop) {
            s.shop = null;
        }
        ui.repairInv(p);
        ui.repairTop(p, inv);
    }

    // ================================================================== //
    //  SALDIRI MOTORU
    // ================================================================== //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        PState s = m.ps(p);
        double n = now();
        if (PState.set(s.check) && n - s.check < 0.2) {
            return;
        }
        if (PState.set(s.shopPending)) {
            double sp = s.shopPending;
            s.shopPending = PState.NONE;
            if (n - sp <= 10 && s.shop == null) {
                ui.openShop(p);
            }
        }
        if (PState.set(s.barT) && n - s.barT > 2.5) {
            m.barHide(s);
        }
        if (!e.hasChangedPosition()) {
            return;
        }
        s.check = n;
        m.shockCheck(p);
    }

    /** Akim (wither nabzi) + gardiyan lazeri hasari (Skript "on damage"). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        Entity attacker = e instanceof EntityDamageByEntityEvent be ? be.getDamager() : null;
        if (m.cfg.beamMode.equals("guardian") && attacker != null && attacker.hasMetadata("sok_beam")) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.WITHER) {
            return;
        }
        if (!(e.getEntity() instanceof Player v)) {
            return;
        }
        if (!m.hasCurrent(v.getUniqueId())) {
            return;
        }
        if (attacker != null) {
            return;
        }
        PotionEffect we = v.getPotionEffect(PotionEffectType.WITHER);
        if (we != null && !we.isAmbient()) {
            return;
        }
        e.setCancelled(true);
        if (m.dmgTest) {
            Text.console("[HASAR-TEST] sok.sk akım nabzı iptal edildi (neden=WITHER) -> " + v.getName());
        }
        boolean r = m.shockCheck(v);
        if (m.ps(v).debug) {
            Text.actionBar(v, r ? "<#4b5563>[AKIM] <#5ac8fa>Nabız <#9ca3af>-> menzilde"
                    : "<#4b5563>[AKIM] <#9ca3af>Menzil dışı -> akım kesildi");
        }
        if (!r) {
            m.stopCurrent(v);
        }
    }

    /** Akim sutle kesilemez. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void potion(EntityPotionEffectEvent e) {
        if (!m.anyCurrent()) {
            return;
        }
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        if (!m.hasCurrent(p.getUniqueId())) {
            return;
        }
        if (!PotionEffectType.WITHER.equals(e.getModifiedType())) {
            return;
        }
        if (e.getCause() == EntityPotionEffectEvent.Cause.MILK) {
            e.setCancelled(true);
            Text.actionBar(p, "<#5ac8fa>⚡ <#9ca3af>Süt elektrik akımını kesmez <#4b5563>· <#e5e7eb>menzilden çık");
            p.getWorld().playSound(p.getLocation(), "block.fire.extinguish", 0.4f, 1.8f);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent e) {
        if (!e.getItem().getType().name().contains("MILK")) {
            return;
        }
        Player p = e.getPlayer();
        boolean akim = m.hasCurrent(p.getUniqueId()) || m.inHostileRange(p);
        if (m.ps(p).debug) {
            Text.send(p, "<#4b5563>[SÜT] <#9ca3af>kayıt=" + m.hasCurrent(p.getUniqueId()) + " menzil=" + m.inHostileRange(p) + " -> engel=" + akim);
        }
        if (!akim) {
            return;
        }
        e.setCancelled(true);
        Text.actionBar(p, "<#5ac8fa>⚡ <#9ca3af>Elektrik akımındayken süt içemezsin <#4b5563>· <#e5e7eb>menzilden çık");
        p.getWorld().playSound(p.getLocation(), "block.fire.extinguish", 0.4f, 1.8f);
        if (!m.hasCurrent(p.getUniqueId())) {
            m.startCurrent(p);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void death(PlayerDeathEvent e) {
        Player v = e.getEntity();
        PState s = m.ps(v);
        s.current = PState.NONE;
        s.combat = PState.NONE;
        s.immune = PState.NONE;
        s.immdur = PState.NONE;
        m.clearBeam(v);
        m.candClear(v.getUniqueId().toString());
    }

    /** P5 ustu koruma, PvP ve normal hasar (kule hasari ve akim HARIC). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protOver(EntityDamageEvent e) {
        if (!m.cfg.protOverPvp) {
            return;
        }
        if (!(e.getEntity() instanceof Player v)) {
            return;
        }
        switch (e.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE, ENTITY_EXPLOSION, BLOCK_EXPLOSION, MAGIC, THORNS -> {
            }
            default -> {
                return;
            }
        }
        int epf = TowerMath.epf(v);
        if (epf <= 16) {
            return;
        }
        double f = TowerMath.protFix(m.cfg, epf, m.cfg.protPvpP5, m.cfg.protPvpStep);
        if (f == 1) {
            return;
        }
        double d = e.getDamage();
        if (d > 0) {
            e.setDamage(d * f);
        }
    }

    /** Debug: sunucunun Koruma/Direnc sonrasi uyguladigi gercek hasar. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void debugDamage(EntityDamageEvent e) {
        if (!m.kdmgAny || !(e.getEntity() instanceof Player v) || !m.ps(v).kdmg) {
            return;
        }
        double bh = Math.round((e.getDamage() / 2) * 100) / 100.0;
        double fh = Math.round((e.getFinalDamage() / 2) * 100) / 100.0;
        Text.send(v, "<#4b5563>[ŞOK] <#9ca3af>sunucu: normal pay <#e5e7eb>" + Text.num(bh)
                + " <#4b5563>→ <#9ca3af>koruma/direnç/emilim sonrası <#f87171>" + Text.num(fh) + " kalp");
    }

    // ---------------- HASAR TESTI (sadece teshis) ----------------

    private boolean dmgTestOn() {
        if (!m.dmgTest) {
            return false;
        }
        if (PState.set(m.dmgTestAt) && now() - m.dmgTestAt > 300) {
            m.dmgTest = false;
            m.dmgTestAt = PState.NONE;
            Text.console(m.cfg.prefix + " <#9ca3af>Hasar testi 5 dk dolduğu için kapandı.");
            return false;
        }
        return true;
    }

    private void dmgBand(EntityDamageByEntityEvent e, String band) {
        if (!m.dmgTest || !(e.getEntity() instanceof Player v)) {
            return;
        }
        Text.console("[HASAR-TEST]   " + band + " sonrası · " + e.getDamager() + " -> " + v.getName()
                + " · hasar=" + Text.num(e.getDamage()) + " · iptal=" + e.isCancelled());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void dtLowest(EntityDamageByEntityEvent e) {
        if (!m.dmgTest || !dmgTestOn() || !(e.getEntity() instanceof Player v)) {
            return;
        }
        Text.console("[HASAR-TEST] OLAY-BAŞI " + e.getDamager() + " -> " + v.getName() + " · hasar=" + Text.num(e.getDamage())
                + " · iptal=" + e.isCancelled() + " · dokunulmazlık=" + v.getNoDamageTicks() + " · sonHasar="
                + Text.num(v.getLastDamage()) + " · kuleAkımı=" + m.hasCurrent(v.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void dtLow(EntityDamageByEntityEvent e) {
        dmgBand(e, "LOW");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void dtNormal(EntityDamageByEntityEvent e) {
        dmgBand(e, "NORMAL");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void dtHigh(EntityDamageByEntityEvent e) {
        dmgBand(e, "HIGH");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dtHighest(EntityDamageByEntityEvent e) {
        dmgBand(e, "HIGHEST");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void dtMonitor(EntityDamageByEntityEvent e) {
        if (!m.dmgTest || !(e.getEntity() instanceof Player v)) {
            return;
        }
        Text.console("[HASAR-TEST] OLAY-SONU " + e.getDamager() + " -> " + v.getName() + " · son hasar="
                + Text.num(e.getFinalDamage()) + " · iptal=" + e.isCancelled());
    }

    private void dmgTestSwing(Player a) {
        if (!dmgTestOn()) {
            return;
        }
        Entity t = a.getTargetEntity(4);
        if (!(t instanceof Player tp)) {
            return;
        }
        Text.console("[HASAR-TEST] SALLAMA " + a.getName() + " -> " + tp.getName() + " · dokunulmazlık="
                + tp.getNoDamageTicks() + "/" + tp.getMaximumNoDamageTicks() + " · sonHasar=" + Text.num(tp.getLastDamage())
                + " · kuleAkımı=" + m.hasCurrent(tp.getUniqueId()));
    }

    // ================================================================== //
    //  KOMUT ENGELI / CIKIS / GIRIS
    // ================================================================== //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent e) {
        if (m.pvpm) {
            return;
        }
        Player p = e.getPlayer();
        if (p.hasPermission("sok.combatbypass")) {
            return;
        }
        if (!m.inCombat(p)) {
            return;
        }
        String label = e.getMessage().split(" ")[0].replace("/", "").toLowerCase();
        if (m.cmdAllow.contains(label)) {
            return;
        }
        e.setCancelled(true);
        Text.send(p, "<#f87171>⚔ <#9ca3af>Savaş modundayken komut kullanamazsın <#4b5563>· <#e5e7eb>" + Math.round(m.combatLeft(p)) + " sn");
        p.getWorld().playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void quit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        PState s = m.ps(p);
        s.shopclick = PState.NONE;
        s.bypasslog = PState.NONE;
        s.shopPending = PState.NONE;
        // SAVASTA CIKIS = OLUM
        if (m.pvpm) {
            s.combat = PState.NONE;
        } else {
            if (m.cfg.combatKillOnQuit && m.inCombat(p)) {
                if (Bukkit.isStopping()) {
                    s.combat = PState.NONE;
                } else {
                    String name = p.getDisplayName();
                    p.setHealth(0);
                    Bukkit.broadcast(Text.color("<#f87171>⚔ <#e5e7eb>" + name + " <#9ca3af>savaştan kaçmaya çalıştı ve <#f87171>öldü"));
                }
            }
            s.combat = PState.NONE;
        }
        s.clearMine();
        m.clearBeam(p);
        m.candClear(id.toString());
        if (s.bar != null) {
            s.bar.removeAll();
        }
        m.players.remove(id);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void join(PlayerJoinEvent e) {
        ui.repairInv(e.getPlayer());
    }

    // ================================================================== //
    //  CEVRESEL ETKI VE DUPE KORUMALARI
    // ================================================================== //

    @EventHandler(priority = EventPriority.NORMAL)
    public void note(NotePlayEvent e) {
        if (m.isTowerPart(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) {
        if (m.store.nearby(e.getLocation()).isEmpty()) {
            return;
        }
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (TowerModule.isTowerMaterial(b) && m.isTowerPart(b.getLocation())) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void blockExplode(BlockExplodeEvent e) {
        if (m.store.nearby(e.getBlock().getLocation()).isEmpty()) {
            return;
        }
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (TowerModule.isTowerMaterial(b) && m.isTowerPart(b.getLocation())) {
                it.remove();
            }
        }
    }

    private void piston(org.bukkit.event.block.BlockPistonEvent e, List<Block> blocks) {
        if (m.store.nearby(e.getBlock().getLocation()).isEmpty()) {
            return;
        }
        for (Block b : blocks) {
            if (TowerModule.isTowerMaterial(b) && m.isTowerPart(b.getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void pistonExtend(BlockPistonExtendEvent e) {
        piston(e, e.getBlocks());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void pistonRetract(BlockPistonRetractEvent e) {
        piston(e, e.getBlocks());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void changeBlock(EntityChangeBlockEvent e) {
        Block b = e.getBlock();
        if (TowerModule.isTowerMaterial(b) && m.isTowerPart(b.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void fromTo(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (to.getType() != Material.CHORUS_PLANT) {
            return;
        }
        if (m.isTowerPart(to.getLocation())) {
            e.setCancelled(true);
        }
    }

    /** Hedef konum kule parcasi mi? Once sadece degisken; blok okumasi sadece kendi bolgede. */
    private boolean fluidGuard(Location loc) {
        if (m.store.isTowerKey(Tower.keyOf(loc))) {
            return true;
        }
        if (m.store.isTowerKey(Tower.keyOf(TowerModule.below(loc)))) {
            return true;
        }
        if (!Bukkit.isOwnedByCurrentRegion(loc)) {
            return false;
        }
        if (!TowerModule.isTowerMaterial(loc.getBlock())) {
            return false;
        }
        return m.isTowerPart(loc);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void bucket(PlayerBucketEmptyEvent e) {
        Block tb = e.getBlock();
        if (!fluidGuard(tb.getLocation())) {
            return;
        }
        e.setCancelled(true);
        Text.actionBar(e.getPlayer(), "<#f87171>Şok kulesine sıvı dökülemez");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void dispense(BlockDispenseEvent e) {
        String dn = e.getItem().getType().name();
        if (!dn.endsWith("_BUCKET") || dn.equals("MILK_BUCKET")) {
            return;
        }
        Block db = e.getBlock();
        if (db.getType() != Material.DISPENSER) {
            return;
        }
        BlockData bd = db.getBlockData();
        if (!(bd instanceof Directional d)) {
            return;
        }
        if (fluidGuard(db.getRelative(d.getFacing()).getLocation())) {
            e.setCancelled(true);
        }
    }

    /** Eski (note block) kuleler ve uzakta yukseltilen kuleler chunk yuklenince guncellenir. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void chunkLoad(ChunkLoadEvent e) {
        String ck = e.getWorld().getName() + "_" + e.getChunk().getX() + "_" + e.getChunk().getZ();
        Set<String> keys = TowerStore.idx(m.store.byChunk, ck);
        if (keys.isEmpty()) {
            return;
        }
        for (String k : List.copyOf(keys)) {
            Tower t = m.store.get(k);
            if (t == null) {
                continue;
            }
            Location tLoc = t.loc();
            if (!Tower.chunkId(tLoc).equals(ck) || !Bukkit.isOwnedByCurrentRegion(tLoc)) {
                continue;
            }
            String bm = TowerModule.mat(tLoc.getBlock());
            if (bm.equals("NOTE_BLOCK")) {
                m.towerBlockOk(tLoc);
            } else if (t.remodel && bm.equals("CHORUS_PLANT")) {
                t.remodel = false;
                m.applyModel(tLoc, t.level);
            }
        }
    }

    // ================================================================== //
    //  KULE ESYASI KORUMASI
    // ================================================================== //

    private boolean isTowerItemEntity(Entity en) {
        if (!(en instanceof Item it)) {
            return false;
        }
        ItemStack is = it.getItemStack();
        return is.getType() == Material.NOTE_BLOCK && m.items.level(is) > 0;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void itemSpawn(ItemSpawnEvent e) {
        Item ie = e.getEntity();
        ItemStack is = ie.getItemStack();
        if (is.getType() != Material.NOTE_BLOCK) {
            return;
        }
        int lv = m.items.level(is);
        if (lv <= 0) {
            return;
        }
        ie.setInvulnerable(true);
        boolean wasOk = TowerItems.cmdOk(is, lv);
        ItemStack fx = m.items.repair(is);
        if (fx != null) {
            ie.setItemStack(fx);
            if (!wasOk) {
                Text.console(m.cfg.prefix + " <#fbbf24>Bozulmuş kule eşyası onarıldı: <#e5e7eb>yerdeki eşya <#9ca3af>Sv" + lv
                        + " x" + fx.getAmount() + " <#4b5563>· <#9ca3af>" + ie.getLocation());
                m.claims.alog("KULE", "ESYA_ONARIM yerdeki eşya seviye=" + lv + " adet=" + fx.getAmount() + " yer=" + ie.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void itemDamage(EntityDamageEvent e) {
        if (!isTowerItemEntity(e.getEntity())) {
            return;
        }
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.KILL || c == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void itemDespawn(ItemDespawnEvent e) {
        if (m.cfg.itemNoDespawn && isTowerItemEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void itemCombust(EntityCombustEvent e) {
        if (isTowerItemEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    // ================================================================== //
    //  TESHIS
    // ================================================================== //

    static void listListeners(org.bukkit.command.CommandSender s, org.bukkit.event.HandlerList hl, String nm, String prefix) {
        Text.send(s, prefix + " <#9ca3af>" + nm + " dinleyicileri <#4b5563>(LOWEST önce, MONITOR en son çalışır)");
        for (RegisteredListener rl : hl.getRegisteredListeners()) {
            Text.send(s, "<#4b5563>› <#e5e7eb>" + rl.getPlugin().getName() + " <#4b5563>· <#fbbf24>" + rl.getPriority().name()
                    + " <#4b5563>· <#9ca3af>iptali yok say: " + rl.isIgnoringCancelled() + " <#4b5563>· <#9ca3af>"
                    + rl.getListener().getClass().getName());
        }
        Text.send(s, "<#9ca3af>Şüpheli: Skript DIŞINDAKİ ve <#e5e7eb>iptali yok say: false <#9ca3af>olan eklentiler (iptal edilmiş olayları da işliyorlar).");
    }
}
