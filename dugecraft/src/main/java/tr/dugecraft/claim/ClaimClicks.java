package tr.dugecraft.claim;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import tr.dugecraft.util.Econ;
import tr.dugecraft.util.Text;

import java.util.Map;

/**
 * claim.sk "on inventory click / drag / close" karsiligi.
 * Menu tanima: Session.menu + Session.inv (Skript "metadata tag X of player").
 * Tum menulerde tiklama iptal edilir; esya alinamaz / konulamaz.
 */
final class ClaimClicks implements Listener {

    private final ClaimModule m;

    ClaimClicks(ClaimModule m) {
        this.m = m;
    }

    /** Skript: {_click} contains "shift" or "number" or "drop" or "swap" or "double" */
    private static boolean badClick(ClickType t) {
        return t.isShiftClick() || t == ClickType.NUMBER_KEY || t == ClickType.DROP || t == ClickType.CONTROL_DROP
                || t == ClickType.SWAP_OFFHAND || t == ClickType.DOUBLE_CLICK;
    }

    /** shift'e izin veren varyant (ciftci menusu urunleri / 47) */
    private static boolean badClickNoShift(ClickType t) {
        return t == ClickType.NUMBER_KEY || t == ClickType.DROP || t == ClickType.CONTROL_DROP
                || t == ClickType.SWAP_OFFHAND || t == ClickType.DOUBLE_CLICK;
    }

    private static boolean is(ItemStack it, Material... mats) {
        if (it == null) {
            return false;
        }
        for (Material mt : mats) {
            if (it.getType() == mt) {
                return true;
            }
        }
        return false;
    }

    private Session menuOf(Player p, Inventory top) {
        Session s = m.sessions.get(p.getUniqueId());
        if (s == null || s.menu == null || s.inv == null || s.inv != top) {
            return null;
        }
        return s;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Session s = menuOf(p, e.getView().getTopInventory());
        if (s == null) {
            return;
        }
        e.setCancelled(true);
        Inventory clicked = e.getClickedInventory();
        if (clicked == null || clicked instanceof PlayerInventory || clicked != s.inv) {
            return;
        }
        ClickType ct = e.getClick();
        int slot = e.getSlot();
        ItemStack item = e.getCurrentItem();
        String menu = s.menu;
        boolean shiftOk = "farmer_menu".equals(menu);
        if (!shiftOk && badClick(ct)) {
            return;
        }
        try {
            switch (menu) {
                case "my_claims_menu" -> myClaims(p, s, slot);
                case "claim_map_menu" -> map(p, s, slot, item);
                case "claim_menu" -> claimMenu(p, s, slot);
                case "claim_upg_menu" -> upgMenu(p, s, slot);
                case "claim_upg_confirm" -> upgConfirm(p, s, slot);
                case "claim_log_menu" -> logMenu(p, s, slot, item);
                case "claim_leave_confirm" -> leave(p, s, slot);
                case "farmer_buy_menu" -> farmerBuy(p, s, slot);
                case "farmer_menu" -> farmerMenu(p, s, slot, ct);
                case "farmer_as_menu" -> farmerAs(p, s, slot);
                case "farmer_item_menu" -> farmerItem(p, s, slot);
                case "claim_time_menu" -> timeMenu(p, s, slot);
                case "claim_delete_confirm" -> deleteConfirm(p, s, slot);
                case "chunk_delete" -> chunkDelete(p, s, slot);
                case "chunk_buy" -> chunkBuy(p, s, slot);
                case "claim_transfer_select" -> transferSelect(p, s, slot);
                case "claim_transfer_confirm" -> transferConfirm(p, s, slot);
                case "claim_members" -> members(p, s, slot, item);
                case "claim_perms" -> perms(p, s, slot);
                case "sell_afk_menu" -> sellAfk(p, s, slot);
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            m.plugin.getLogger().warning("[claim] menu tiklama hatasi (" + menu + "): " + ex);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (menuOf(p, e.getView().getTopInventory()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void close(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        Session s = m.sessions.get(p.getUniqueId());
        if (s == null || s.inv == null || s.inv != e.getInventory()) {
            return;
        }
        String menu = s.menu;
        s.menu = null;
        s.inv = null;
        if (menu == null) {
            return;
        }
        switch (menu) {
            case "farmer_menu", "farmer_item_menu" -> m.menus.viewClear(s);
            case "farmer_as_menu" -> {
                m.menus.viewClear(s);
                String dirty = s.fasDirty;
                s.fasDirty = null;
                if (dirty != null && m.store.owner(dirty) != null) {
                    m.save(dirty);
                }
            }
            case "claim_upg_confirm" -> {
                s.upgKey = null;
                s.upgAct = null;
            }
            case "claim_transfer_confirm" -> {
                s.timerDone = false;
                s.target = null;
            }
            case "claim_delete_confirm" -> s.timerDone = false;
            case "chunk_delete" -> {
                s.target = null;
                s.chunkDeleteSrc = null;
            }
            case "chunk_buy", "claim_perms" -> s.target = null;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ //

    private void myClaims(Player p, Session s, int slot) {
        String t = s.slotTags.get(slot);
        if (t == null) {
            return;
        }
        if ("new".equals(t)) {
            p.closeInventory();
            m.menus.map(p, "", "new", "");
        } else {
            m.menus.claimMenu(p, t);
        }
    }

    private void map(Player p, Session s, int raw, ItemStack item) {
        String mode = s.mapMode == null ? "manage" : s.mapMode;
        String base = s.mapBase == null ? "" : s.mapBase;
        if (raw == 37) {
            s.mapMark = !s.mapMark;
            ClaimModule.sound(p, "ui.button.click", 0.6f, 1.4f);
            m.menus.map(p, s.mapCenter == null ? "" : s.mapCenter, mode, base);
            return;
        }
        String center = s.mapCenter;
        if (s.mapMark) {
            int col = raw % 9;
            int row = raw / 9;
            if (col >= 4 && row <= 4 && !is(item, Material.BLACK_STAINED_GLASS_PANE) && center != null) {
                Integer pz = ClaimModule.cidZ(center);
                Integer px = ClaimModule.cidX(center);
                if (pz == null || px == null) {
                    return;
                }
                String w = ClaimModule.cidWorld(center);
                int r = 230;
                int g = 230;
                int b = 230;
                String cn = "WHITE";
                if (is(item, Material.LIME_WOOL)) {
                    r = 110;
                    g = 255;
                    b = 120;
                    cn = "LIME";
                } else if (is(item, Material.BLUE_WOOL, Material.LIGHT_BLUE_WOOL, Material.CYAN_WOOL)) {
                    r = 80;
                    g = 170;
                    b = 255;
                    cn = "LIGHT_BLUE";
                } else if (is(item, Material.RED_WOOL)) {
                    r = 255;
                    g = 70;
                    b = 70;
                    cn = "RED";
                } else if (is(item, Material.ORANGE_WOOL)) {
                    r = 255;
                    g = 160;
                    b = 40;
                    cn = "ORANGE";
                }
                p.closeInventory();
                m.menus.markChunk(p, w, px + (col - 6), pz + (row - 2), r, g, b, cn);
                return;
            }
        }
        if (raw == 10 || raw == 28 || raw == 18 || raw == 20 || raw == 19 || raw == 46) {
            if (raw == 46) {
                p.closeInventory();
                return;
            }
            if ("new".equals(mode) || center == null) {
                return;
            }
            Integer pz0 = ClaimModule.cidZ(center);
            Integer px0 = ClaimModule.cidX(center);
            if (pz0 == null || px0 == null) {
                return;
            }
            int pz = pz0;
            int px = px0;
            String w = ClaimModule.cidWorld(center);
            if (raw == 10) {
                pz--;
            } else if (raw == 28) {
                pz++;
            } else if (raw == 18) {
                px--;
            } else if (raw == 20) {
                px++;
            }
            boolean useBase = !base.isEmpty() && m.store.owner(base) != null;
            int origX;
            int origZ;
            int maxStep;
            if (useBase) {
                Integer bx = ClaimModule.cidX(base);
                Integer bz = ClaimModule.cidZ(base);
                if (bx == null || bz == null) {
                    return;
                }
                origX = bx;
                origZ = bz;
                maxStep = m.mapRange();
            } else {
                origX = (int) Math.floor(p.getLocation().getX() / 16);
                origZ = (int) Math.floor(p.getLocation().getZ() / 16);
                maxStep = 2;
            }
            if (raw == 19) {
                px = origX;
                pz = origZ;
            }
            if (Math.abs(px - origX) > maxStep || Math.abs(pz - origZ) > maxStep) {
                if (useBase) {
                    m.pre(p, "&cHaritada claim merkezinden en fazla " + maxStep + " bölge uzağa gidebilirsin!");
                } else {
                    m.pre(p, "&cHaritada kendi konumunuzdan en fazla 2 adım uzağa gidebilirsiniz!");
                }
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                return;
            }
            m.menus.map(p, w + "_" + px + "_" + pz, mode, base);
            return;
        }
        boolean own = is(item, Material.BLUE_WOOL, Material.LIGHT_BLUE_WOOL, Material.CYAN_WOOL);
        boolean free = is(item, Material.LIME_WOOL);
        if (!own && !free) {
            return;
        }
        int row = raw / 9;
        int col = raw % 9;
        if (col < 4 || center == null) {
            return;
        }
        Integer pz = ClaimModule.cidZ(center);
        Integer px = ClaimModule.cidX(center);
        if (pz == null || px == null) {
            return;
        }
        String target = ClaimModule.cidWorld(center) + "_" + (px + (col - 6)) + "_" + (pz + (row - 2));
        if (own) {
            if (!ClaimModule.uuid(p).equals(m.store.owner(target))) {
                m.pre(p, "<#ff4b1f>Bu bölge size ait değil!");
                return;
            }
            m.actions.chunkDeleteMenu(p, target);
            return;
        }
        m.actions.buyChunk(p, target, mode);
    }

    private void claimMenu(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        Claim c = m.store.get(cid);
        String mainCid = c == null ? null : c.mainCid;
        String u = ClaimModule.uuid(p);
        boolean isOwner = (c != null && u.equals(c.owner)) || ClaimModule.isKurucu(p);
        Claim mcl = mainCid == null ? null : m.store.get(mainCid);
        boolean hasFarmer = mcl != null && mcl.perm(u, "farmer");
        boolean hasTeleport = mcl != null && mcl.perm(u, "teleport");
        String act = s.slotTags.get(slot);
        if (act == null) {
            return;
        }
        switch (act) {
            case "back" -> m.menus.myClaims(p);
            case "logs" -> m.menus.logMenu(p, cid, 1);
            case "chat" -> {
                m.chat.toggle(p, mainCid == null ? cid : mainCid);
                m.menus.claimMenu(p, cid);
            }
            case "upgrades" -> {
                if (isOwner) {
                    m.upgrades.menu(p, cid);
                }
            }
            case "tp" -> {
                if (isOwner || hasTeleport) {
                    m.actions.teleportToClaim(p, cid);
                }
            }
            case "members" -> {
                if (isOwner) {
                    m.actions.memberMenu(p, cid);
                }
            }
            case "map" -> {
                if (isOwner) {
                    p.closeInventory();
                    m.menus.map(p, cid, "manage", "");
                }
            }
            case "energy" -> {
                if (isOwner) {
                    m.menus.time(p, cid);
                }
            }
            case "stone" -> {
                if (!isOwner || mainCid == null) {
                    if (isOwner) {
                        ClaimModule.sound(p, "entity.villager.no", 1, 1);
                        m.pre(p, "&cClaim taşı sadece &eana claim chunk'ının &ciçine konulabilir!");
                    }
                    return;
                }
                Claim st = m.store.get(mainCid);
                double mt = st == null ? Double.NaN : st.moveTime;
                if (!Double.isNaN(mt)) {
                    double diff = ClaimModule.now() - mt;
                    if (diff < 600) {
                        long minLeft = (long) Math.ceil((600 - diff) / 60);
                        ClaimModule.sound(p, "entity.villager.no", 1, 1);
                        m.pre(p, "&cClaim taşını 10 dakikada bir taşıyabilirsin. &7(" + minLeft + " dakika kaldı)");
                        return;
                    }
                }
                if (!ClaimModule.chunkId(p.getLocation()).equals(mainCid)) {
                    ClaimModule.sound(p, "entity.villager.no", 1, 1);
                    m.pre(p, "&cClaim taşı sadece &eana claim chunk'ının &ciçine konulabilir!");
                    return;
                }
                Block b = p.getLocation().getBlock();
                Location nl = b.getLocation();
                if (!b.getType().isAir()) {
                    ClaimModule.sound(p, "entity.villager.no", 1, 1);
                    m.pre(p, "&cDurduğun yer boş değil, taş buraya konulamaz.");
                    return;
                }
                if (m.towerNearLoc(nl)) {
                    ClaimModule.sound(p, "entity.villager.no", 1, 1);
                    m.pre(p, "&cClaim taşı şok kulesinin dibine konulamaz. &7Arada en az 1 blok boşluk bırak.");
                    return;
                }
                m.moveStone(p, mainCid, nl);
                p.closeInventory();
            }
            case "farmer" -> {
                if (isOwner || hasFarmer) {
                    m.menus.farmer(p, cid);
                }
            }
            case "delete" -> {
                if (isOwner) {
                    m.actions.claimDeleteConfirm(p, cid);
                }
            }
            case "leave" -> {
                String mc = mainCid == null ? cid : mainCid;
                Claim mm = m.store.get(mc);
                if (mm != null && mm.members.contains(u)) {
                    m.menus.leaveConfirm(p, cid);
                }
            }
            default -> {
            }
        }
    }

    private void upgMenu(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        if (slot == 31) {
            m.menus.claimMenu(p, cid);
            return;
        }
        String u = slot == 11 ? "farm2x" : slot == 15 ? "night" : "";
        if (u.isEmpty()) {
            return;
        }
        String mc = m.mainOf(cid);
        if (!m.upgrades.canManage(p, mc)) {
            p.closeInventory();
            return;
        }
        Claim c = m.store.get(mc);
        if (c == null || !c.upgOwn(u)) {
            m.upgrades.confirm(p, cid, u, "buy");
        } else if (c.upgOn(u)) {
            m.upgrades.confirm(p, cid, u, "off");
        } else {
            double cd = m.upgrades.reopenLeft(mc, u);
            if (cd > 0) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&c" + Upgrades.name(u) + " kapatıldıktan sonra " + Text.num(m.cfg.upgradeReopenHours) + " saat açılamaz. &7Kalan: &e" + m.fmtDur(cd));
                return;
            }
            m.upgrades.confirm(p, cid, u, "on");
        }
    }

    private void upgConfirm(Player p, Session s, int slot) {
        String cid = s.cid;
        String u = s.upgKey;
        String ua = s.upgAct;
        if (slot == 15) {
            if (cid != null) {
                m.upgrades.menu(p, cid);
            }
            return;
        }
        if (slot == 11) {
            s.upgKey = null;
            s.upgAct = null;
            p.closeInventory();
            if (u == null || ua == null || cid == null) {
                return;
            }
            m.upgrades.action(p, cid, u, ua);
        }
    }

    private void logMenu(Player p, Session s, int slot, ItemStack item) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        int pg = s.logPage == null ? 1 : s.logPage;
        if (slot == 49) {
            m.menus.claimMenu(p, cid);
        } else if (slot == 45) {
            if (pg > 1) {
                m.menus.logMenu(p, cid, pg - 1);
            }
        } else if (slot == 53) {
            if (is(item, Material.ARROW)) {
                m.menus.logMenu(p, cid, pg + 1);
            }
        }
    }

    private void leave(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        if (slot == 15) {
            m.menus.claimMenu(p, cid);
            return;
        }
        if (slot != 11) {
            return;
        }
        p.closeInventory();
        String mc = m.mainOf(cid);
        String u = ClaimModule.uuid(p);
        Claim c = m.store.get(mc);
        if (c == null || !c.members.contains(u)) {
            m.pre(p, "&cZaten bu claim'in üyesi değilsin.");
            return;
        }
        if (u.equals(c.owner)) {
            return;
        }
        c.members.remove(u);
        c.perms.remove(u);
        m.validateAutoSell(mc);
        m.save(mc);
        String cName = c.claimName == null ? "<none>" : c.claimName;
        m.clog(mc, "&e" + p.getName() + " &cclaim'den ayrıldı");
        m.alog("UYE", "AYRILDI " + p.getName() + "(" + u + ") claim=" + mc);
        ClaimModule.sound(p, "block.wooden_door.close", 1, 1);
        m.pre(p, "&7&e" + cName + " &7claim'inden ayrıldın.");
        String ownU = c.owner;
        String lMsg = m.cfg.prefix + " &e" + p.getName() + " &7adlı üye &e" + cName + " &7claim'inden ayrıldı.";
        Player ownP = ownU == null ? null : ClaimModule.onlineByUuid(ownU);
        if (ownP != null) {
            m.send(ownP, lMsg);
        } else if (ownU != null) {
            m.addKickNotice(ownU, lMsg);
        }
    }

    private void farmerBuy(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        String mc = m.mainOf(cid);
        if (slot == 22) {
            m.menus.claimMenu(p, cid);
            return;
        }
        if (slot != 13) {
            return;
        }
        if (!m.canUseFarmer(p, mc)) {
            p.closeInventory();
            m.pre(p, "&cBu claimde çiftçi yetkin yok.");
            return;
        }
        Claim c = m.store.get(mc);
        if (c == null) {
            return;
        }
        if (c.farmerOn()) {
            m.menus.farmer(p, cid);
            return;
        }
        if (!m.reserve("fa|" + mc, ClaimModule.uuid(p))) {
            return;
        }
        boolean ok;
        try {
            ok = Econ.balance(p) >= 1500 && Econ.withdraw(p, 1500);
            if (ok) {
                c.farmerStatus = true;
                c.farmerLevel = 1;
                for (String k : Farmer.KEYS) {
                    m.store.fStock(mc, k);
                }
                m.clog(mc, "&e" + p.getName() + " &açiftçiyi aktif etti");
                m.alog("CIFTCI", "AKTIF " + p.getName() + "(" + p.getUniqueId() + ") claim=" + mc + " ucret=1500");
                c.needsSave = false;
                m.save(mc);
            }
        } finally {
            m.release("fa|" + mc);
        }
        if (ok) {
            ClaimModule.sound(p, "entity.player.levelup", 1, 1);
            m.pre(p, "<#a8ff78>Tebrikler! 1500$ karşılığında Çiftçiyi aktif ettiniz.");
            m.menus.farmer(p, cid);
        } else {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            m.pre(p, "<#ff4b1f>Yetersiz bakiye! Gereken miktar: 1500$");
        }
    }

    private void farmerMenu(Player p, Session s, int slot, ClickType ct) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        String mc = m.mainOf(cid);
        if (slot == 49) {
            m.menus.claimMenu(p, cid);
            return;
        }
        if (!m.canUseFarmer(p, mc)) {
            p.closeInventory();
            m.pre(p, "&cBu claimde çiftçi yetkin yok.");
            return;
        }
        Claim c = m.store.get(mc);
        if (c == null || !c.farmerOn()) {
            p.closeInventory();
            return;
        }
        if (slot == 47) {
            if (badClickNoShift(ct)) {
                return;
            }
            m.menus.farmerAs(p, cid);
            return;
        }
        if (slot == 51) {
            if (badClick(ct)) {
                return;
            }
            if (!m.reserve("fu|" + mc, ClaimModule.uuid(p))) {
                return;
            }
            int lvl;
            double cost;
            boolean done = false;
            try {
                lvl = c.farmerLevel;
                if (lvl >= 8) {
                    return;
                }
                cost = 1000 * Math.pow(2, lvl);
                if (Econ.balance(p) >= cost && Econ.withdraw(p, cost)) {
                    c.farmerLevel = lvl + 1;
                    done = true;
                }
            } finally {
                m.release("fu|" + mc);
            }
            if (done) {
                m.clog(mc, "&e" + p.getName() + " &açiftçiyi yükseltti &8(Seviye " + (lvl + 1) + ")");
                m.alog("CIFTCI", "YUKSELTME " + p.getName() + "(" + p.getUniqueId() + ") claim=" + mc + " seviye=" + (lvl + 1) + " ucret=" + Text.num(cost));
                c.needsSave = false;
                m.save(mc);
                ClaimModule.sound(p, "entity.player.levelup", 1, 1);
                m.pre(p, "<#a8ff78>Çiftçiniz başarıyla Seviye " + (lvl + 1) + " değerine yükseltildi!");
                m.menus.farmer(p, cid);
            } else {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "<#ff4b1f>Yetersiz bakiye! Gereken miktar: " + Text.num(cost) + "$");
            }
            return;
        }
        String k = s.slotTags.get(slot);
        if (k != null) {
            if (badClickNoShift(ct)) {
                return;
            }
            m.menus.farmerItem(p, cid, k);
        }
    }

    private void farmerAs(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        String mc = m.mainOf(cid);
        if (slot == 49) {
            m.menus.farmer(p, cid);
            return;
        }
        Claim c = m.store.get(mc);
        if (c == null || !c.farmerOn()) {
            p.closeInventory();
            return;
        }
        if (!m.canUseFarmer(p, mc) && !m.asCanTurnOff(p, mc)) {
            p.closeInventory();
            return;
        }
        String mode;
        String k = null;
        if (slot == 48) {
            mode = "all_on";
        } else if (slot == 50) {
            mode = "all_off";
        } else {
            k = s.slotTags.get(slot);
            if (k == null) {
                return;
            }
            mode = c.asOn.containsKey(k) ? "one_off" : "one_on";
        }
        String u = ClaimModule.uuid(p);
        if (mode.endsWith("_on")) {
            if (!p.hasPermission("group.dvip+")) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cOto satışı açmak için &dDVIP+ &cyetkisi gerekir!");
                return;
            }
            if (!m.autoSellMemberValid(mc, u)) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cBu claimde çiftçi yetkiniz bulunmuyor!");
                return;
            }
        } else if (!m.asCanTurnOff(p, mc)) {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            m.pre(p, "&cOto satışı sadece claim sahibi veya DVIP+ yetkili üyeler kapatabilir!");
            return;
        }
        switch (mode) {
            case "all_on" -> {
                for (String key : Farmer.KEYS) {
                    if (!c.asOn.containsKey(key)) {
                        c.asOn.put(key, u);
                        c.asOnName.put(key, p.getName());
                    }
                }
                m.pre(p, "&aTüm ürünler için otomatik satış açıldı. &7(Para claim sahibine gider)");
            }
            case "all_off" -> {
                for (String key : Farmer.KEYS) {
                    c.asOn.remove(key);
                    c.asOnName.remove(key);
                }
                m.pre(p, "&cTüm ürünler için otomatik satış kapatıldı. &7(Hepsi depolanacak)");
            }
            case "one_on" -> {
                c.asOn.put(k, u);
                c.asOnName.put(k, p.getName());
            }
            default -> {
                c.asOn.remove(k);
                c.asOnName.remove(k);
            }
        }
        String asTxt = switch (mode) {
            case "all_on" -> "tüm ürünlerde oto satışı &aaçtı";
            case "all_off" -> "tüm ürünlerde oto satışı &ckapattı";
            case "one_on" -> "&f" + Farmer.name(k) + " &7için oto satışı &aaçtı";
            default -> "&f" + Farmer.name(k) + " &7için oto satışı &ckapattı";
        };
        String kTxt = k == null ? "HEPSI" : k;
        m.clog(mc, "&e" + p.getName() + " &7" + asTxt);
        m.alog("OTOSATIS", p.getName() + "(" + u + ") claim=" + mc + " islem=" + mode + " urun=" + kTxt);
        c.needsSave = true;
        s.fasDirty = mc;
        m.store.bumpVer(mc);
        Inventory inv = s.inv;
        if (inv != null) {
            m.menus.fillFarmerAs(p, inv, mc);
        }
        s.fviewVer = m.menus.viewVer(mc);
        ClaimModule.sound(p, "ui.button.click", 0.6f, 1.4f);
    }

    /** Skript "player has enough space for X" */
    static boolean hasSpace(Player p, ItemStack it) {
        int need = it.getAmount();
        int max = it.getMaxStackSize();
        ItemStack[] st = p.getInventory().getStorageContents();
        for (ItemStack c : st) {
            if (c == null || c.getType().isAir()) {
                need -= max;
            } else if (c.isSimilar(it)) {
                need -= Math.max(0, c.getMaxStackSize() - c.getAmount());
            }
            if (need <= 0) {
                return true;
            }
        }
        return need <= 0;
    }

    private void farmerItem(Player p, Session s, int slot) {
        String cid = s.cid;
        String k = s.farmerItemKey;
        if (cid == null) {
            return;
        }
        String mc = m.mainOf(cid);
        if (slot == 31) {
            m.menus.farmer(p, cid);
            return;
        }
        if (!m.canUseFarmer(p, mc)) {
            p.closeInventory();
            m.pre(p, "&cBu claimde çiftçi yetkin yok.");
            return;
        }
        Claim c = m.store.get(mc);
        if (c == null || !c.farmerOn()) {
            p.closeInventory();
            return;
        }
        if (k == null) {
            return;
        }
        long current = m.store.fGet(mc, k);
        double price = m.price(k);
        String name = Farmer.name(k);
        if (slot == 10 || slot == 11 || slot == 12) {
            int amt = slot == 10 ? 1 : slot == 11 ? 32 : 64;
            if (current < amt) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cDepoda yeterli miktarda " + name + " yok!");
                return;
            }
            ItemStack give = ClaimMenus.farmItem(k);
            give.setAmount(amt);
            if (!hasSpace(p, give)) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cEnvanterinizde yeterli yer yok!");
                return;
            }
            if (!m.store.fTake(mc, k, amt)) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cDepo bu sırada boşaldı, işlem iptal edildi.");
                return;
            }
            Map<Integer, ItemStack> left = p.getInventory().addItem(give);
            for (ItemStack l : left.values()) {
                // Olmamasi gerekir (yer kontrol edildi); yine de esya kaybolmasin
                p.getWorld().dropItem(p.getLocation(), l);
            }
            m.clog(mc, "&e" + p.getName() + " &7depodan &f" + amt + " " + name + " &7aldı");
            m.alog("CIFTCI", "ALMA " + p.getName() + "(" + p.getUniqueId() + ") claim=" + mc + " urun=" + k + " adet=" + amt + " once=" + current + " sonra=" + m.store.fGet(mc, k));
            c.needsSave = false;
            m.save(mc);
            ClaimModule.sound(p, "entity.item.pickup", 1, 1);
            m.pre(p, "<#a8ff78>" + amt + " adet " + name + " envanterinize alındı.");
            m.menus.farmerItem(p, cid, k);
            return;
        }
        if (slot == 14 || slot == 15 || slot == 16 || slot == 22) {
            long amt = slot == 14 ? 1 : slot == 15 ? 32 : slot == 16 ? 64 : current;
            if (amt <= 0) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cDepoda satılacak " + name + " bulunmuyor!");
                return;
            }
            if (current < amt) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cDepoda yeterli miktarda " + name + " yok!");
                return;
            }
            if (price <= 0) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&c" + name + " şu anda markette satılamıyor!");
                return;
            }
            if (!m.actions.sellAfkCheck(p, cid, k)) {
                return;
            }
            double total = amt * price;
            if (!m.store.fTake(mc, k, amt)) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cDepo bu sırada boşaldı, işlem iptal edildi.");
                return;
            }
            if (!Econ.deposit(p, total)) {
                // Ekonomi yanit vermediyse urun depoya geri konur (kayip olmasin)
                m.store.fStock(mc, k).addAndGet(amt);
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cİşlem yapılamadı, lütfen tekrar dene.");
                return;
            }
            m.clog(mc, "&e" + p.getName() + " &7depodan &f" + amt + " " + name + " &7sattı &8(+" + ClaimModule.fmtPrice(total) + "$)");
            m.alog("CIFTCI", "SATIS " + p.getName() + "(" + p.getUniqueId() + ") claim=" + mc + " urun=" + k + " adet=" + amt + " kazanc=" + Text.num(total) + " once=" + current + " sonra=" + m.store.fGet(mc, k));
            c.needsSave = false;
            m.save(mc);
            ClaimModule.sound(p, "entity.experience_orb.pickup", 1, 1);
            m.pre(p, "<#a8ff78>" + amt + " adet " + name + " satıldı ve <#f6d365>" + ClaimModule.fmtPrice(total) + "$ <#a8ff78>kazanıldı.");
            m.menus.farmerItem(p, cid, k);
        }
    }

    private void timeMenu(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        if (slot == 31) {
            m.menus.claimMenu(p, cid);
            return;
        }
        double now = ClaimModule.now();
        if (!Double.isNaN(s.energyClick) && now - s.energyClick < 0.4) {
            return;
        }
        s.energyClick = now;
        String mc = m.mainOf(cid);
        double buy = 0;
        if (slot == 19) {
            buy = m.drainPerHour(mc);
        } else if (slot == 21) {
            buy = m.drainPerHour(mc) * 24;
        } else if (slot == 23) {
            buy = m.drainPerHour(mc) * 168;
        } else if (slot == 25) {
            buy = m.energyMax(mc) - m.energyNow(mc);
        }
        if (buy > 0) {
            m.actions.buyEnergy(p, cid, buy);
        }
    }

    private void deleteConfirm(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        if (slot == 15) {
            s.timerDone = false;
            m.menus.claimMenu(p, cid);
            return;
        }
        if (slot != 11) {
            return;
        }
        if (!s.timerDone) {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            m.pre(p, "&cLütfen sürenin dolmasını bekleyin.");
            return;
        }
        p.closeInventory();
        Claim c = m.store.get(cid);
        if (c == null || c.owner == null) {
            return;
        }
        if (!c.owner.equals(ClaimModule.uuid(p)) && !ClaimModule.isKurucu(p)) {
            m.pre(p, "<#ff4b1f>Bu claim'in sahibi değilsiniz, silme iptal edildi.");
            return;
        }
        String cName = c.claimName == null ? "Bilinmiyor" : c.claimName;
        m.alog("CLAIM", "SILINDI " + p.getName() + "(" + p.getUniqueId() + ") claim=" + cid + " isim=" + cName + " (menu)");
        m.actions.broadcastExplosion("silindi", cName, cid);
        m.deleteClaim(cid);
        ClaimModule.sound(p, "entity.item.break", 1, 1);
        m.pre(p, "<#ff0844>Claim ve tüm bağlı parçaları silindi!");
    }

    private void chunkDelete(Player p, Session s, int slot) {
        String cid = s.target;
        String src = s.chunkDeleteSrc;
        if (slot == 15 || slot == 22) {
            p.closeInventory();
            if ("cmd".equals(src) || cid == null) {
                return;
            }
            m.menus.map(p, cid, "manage", "");
            return;
        }
        if (slot == 11) {
            double now = ClaimModule.now();
            if (!Double.isNaN(s.chunkDelClick) && now - s.chunkDelClick < 1) {
                return;
            }
            s.chunkDelClick = now;
            p.closeInventory();
            if (cid != null) {
                m.actions.deleteChunkArea(p, cid);
            }
        }
    }

    private void chunkBuy(Player p, Session s, int slot) {
        if (slot == 15) {
            p.closeInventory();
            return;
        }
        if (slot == 11) {
            double now = ClaimModule.now();
            if (!Double.isNaN(s.chunkBuyClick) && now - s.chunkBuyClick < 1) {
                return;
            }
            s.chunkBuyClick = now;
            m.actions.confirmChunkBuy(p);
        }
    }

    private void transferSelect(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        if (slot == 49) {
            m.actions.memberMenu(p, cid);
            return;
        }
        String t = s.slotTags.get(slot);
        if (t == null) {
            return;
        }
        if (!ClaimModule.uuid(p).equals(m.store.owner(cid)) && !ClaimModule.isKurucu(p)) {
            p.closeInventory();
            return;
        }
        m.actions.transferConfirm(p, cid, t);
    }

    private void transferConfirm(Player p, Session s, int slot) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        if (slot == 15) {
            s.timerDone = false;
            s.target = null;
            m.actions.memberMenu(p, cid);
            return;
        }
        if (slot != 11) {
            return;
        }
        if (!s.timerDone) {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            m.pre(p, "&cLütfen sürenin dolmasını bekleyin.");
            return;
        }
        String t = s.target;
        if (t == null) {
            return;
        }
        s.target = null;
        p.closeInventory();
        String mc = m.mainOf(cid);
        Claim c = m.store.get(mc);
        String owner = c == null ? null : c.owner;
        if (!ClaimModule.uuid(p).equals(owner) && !ClaimModule.isKurucu(p)) {
            m.pre(p, "<#ff4b1f>Bu claim'in sahibi değilsiniz, devir iptal edildi.");
            return;
        }
        if (owner == null || t.equals(owner)) {
            return;
        }
        if (!c.members.contains(t)) {
            m.pre(p, "<#ff4b1f>Bu oyuncu artık claim üyesi değil, devir iptal edildi.");
            return;
        }
        int tLim = m.cfg.maxMainClaims;
        Player tPl = ClaimModule.onlineByUuid(t);
        if (tPl != null) {
            tLim = m.maxMainClaims(tPl);
        }
        if (m.mainClaimCountByUuid(t) >= tLim) {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            m.pre(p, "<#ff4b1f>Bu oyuncunun claim sınırı dolu (&e" + tLim + "<#ff4b1f>), devir yapılamaz.");
            if (tPl == null) {
                m.send(p, "&8› &7Oyuncu çevrimdışı olduğu için normal hak sayıldı. Rütbesi varsa çevrimiçiyken tekrar dene.");
            }
            return;
        }
        String tName = ClaimActions.offName(t);
        String oldU = owner;
        String oldN = c.ownerName == null ? "<none>" : c.ownerName;
        m.actions.transferClaim(mc, oldU, t, tName, oldN);
        if (!oldU.equals(ClaimModule.uuid(p))) {
            m.alog("KURUCU", p.getName() + "(" + p.getUniqueId() + ") claim devretti claim=" + mc + " eski=" + oldN + " yeni=" + tName);
        }
        ClaimModule.sound(p, "entity.player.levelup", 1, 1);
        m.pre(p, "<#a8ff78>Claim başarıyla &e" + tName + " <#a8ff78>adlı oyuncuya devredildi. Artık üyesiniz.");
    }

    private void members(Player p, Session s, int slot, ItemStack item) {
        String cid = s.cid;
        if (cid == null) {
            return;
        }
        if (slot == 47) {
            p.closeInventory();
            double at = ClaimModule.now();
            s.chatMode = "member";
            s.chatCid = cid;
            s.chatAt = at;
            m.pre(p, "&eDavet etmek istediğiniz oyuncunun ismini sohbete yazın. &a(60 Saniyeniz var) &7(İptal için &c'iptal' &7yazın)");
            m.send(p, " &7Sadece &fgüvendiğin &7kişileri ekle. Davet 60 saniye içinde onaylanmalıdır.");
            Runnable expire = () -> {
                if ("member".equals(s.chatMode) && cid.equals(s.chatCid) && s.chatAt == at) {
                    s.chatMode = null;
                    s.chatCid = null;
                    s.chatAt = Double.NaN;
                    if (p.isOnline()) {
                        m.pre(p, "&c60 saniye içinde oyuncu ismi girmediğiniz için üye ekleme iptal edildi.");
                    }
                }
            };
            p.getScheduler().runDelayed(m.plugin, t -> expire.run(), () -> {
                if ("member".equals(s.chatMode) && cid.equals(s.chatCid) && s.chatAt == at) {
                    s.chatMode = null;
                    s.chatCid = null;
                    s.chatAt = Double.NaN;
                }
            }, 1200L);
            return;
        }
        if (slot == 49) {
            m.menus.claimMenu(p, cid);
            return;
        }
        if (slot == 51) {
            if (!ClaimModule.uuid(p).equals(m.store.owner(cid)) && !ClaimModule.isKurucu(p)) {
                p.closeInventory();
                return;
            }
            m.actions.transferSelect(p, cid);
            return;
        }
        if (is(item, Material.PLAYER_HEAD)) {
            String t = s.slotTags.get(slot);
            if (t != null) {
                m.actions.permMenu(p, cid, t);
            }
        }
    }

    private void perms(Player p, Session s, int slot) {
        String cid = s.cid;
        String target = s.target;
        if (cid == null) {
            return;
        }
        if (slot == 31) {
            m.actions.memberMenu(p, cid);
            return;
        }
        Claim c = m.store.get(cid);
        String u = ClaimModule.uuid(p);
        if ((c == null || !u.equals(c.owner)) && !p.hasPermission("claim.admin") && !ClaimModule.isKurucu(p)) {
            p.closeInventory();
            return;
        }
        if (c == null || target == null || !c.members.contains(target)) {
            m.actions.memberMenu(p, cid);
            return;
        }
        String tName = ClaimActions.offName(target);
        if (slot == 33) {
            c.members.remove(target);
            c.perms.remove(target);
            m.save(cid);
            String kCName = c.claimName == null ? "<none>" : c.claimName;
            m.clog(cid, "&e" + p.getName() + " &c" + tName + " adlı üyeyi claim'den attı");
            m.alog("UYE", "ATILDI " + tName + "(" + target + ") claim=" + cid + " atan=" + p.getName() + "(" + u + ")");
            String kMsg = m.cfg.prefix + " &e" + p.getName() + " &cseni &e" + kCName + " &cclaim'inden çıkardı.";
            Player kp = ClaimModule.onlineByUuid(target);
            if (kp != null) {
                m.send(kp, kMsg);
                ClaimModule.sound(kp, "entity.villager.no", 1, 1);
            } else {
                m.addKickNotice(target, kMsg);
            }
            m.validateAutoSell(c.mainOrSelf());
            m.actions.memberMenu(p, cid);
            m.pre(p, "&cOyuncu claimden atıldı.");
            return;
        }
        String perm = switch (slot) {
            case 11 -> "spawner_break";
            case 12 -> "farmer";
            case 13 -> "teleport";
            case 14 -> "sethome";
            case 15 -> "logs";
            default -> "";
        };
        if (!perm.isEmpty()) {
            String stTxt;
            if (c.perm(target, perm)) {
                c.setPerm(target, perm, false);
                stTxt = "&ckapattı";
            } else {
                c.setPerm(target, perm, true);
                stTxt = "&aaçtı";
            }
            m.clog(cid, "&e" + p.getName() + " &7› &e" + tName + " &7için &f" + perm + " &7yetkisini " + stTxt);
            m.alog("YETKI", p.getName() + "(" + u + ") claim=" + cid + " hedef=" + tName + "(" + target + ") yetki=" + perm + " yeni=" + c.perm(target, perm));
            m.save(cid);
            m.actions.permMenu(p, cid, target);
            return;
        }
        if (slot == 29) {
            boolean allOn = true;
            for (String k : ClaimActions.PK) {
                if (!c.perm(target, k)) {
                    allOn = false;
                }
            }
            boolean nv = !allOn;
            for (String k : ClaimActions.PK) {
                c.setPerm(target, k, nv);
            }
            String stTxt = nv ? "&aaçtı" : "&ckapattı";
            m.clog(cid, "&e" + p.getName() + " &7› &e" + tName + " &7için tüm yetkileri " + stTxt);
            m.alog("YETKI", p.getName() + "(" + u + ") claim=" + cid + " hedef=" + tName + "(" + target + ") TUM_YETKILER=" + nv);
            m.save(cid);
            m.actions.permMenu(p, cid, target);
        }
    }

    private void sellAfk(Player p, Session s, int slot) {
        ClaimActions.Sac sc = m.actions.sac(p);
        String cid = sc.cid;
        String k = sc.k;
        int ok = sc.slot;
        if (slot == 4) {
            return;
        }
        if (slot == 31) {
            m.actions.sellAfkBack(p, cid, k);
            return;
        }
        if (ok < 0) {
            return;
        }
        sc.slot = -1;
        String u = ClaimModule.uuid(p);
        if (slot == ok) {
            sc.need = false;
            sc.fail = 0;
            sc.cnt = 0;
            sc.sess = System.currentTimeMillis();
            synchronized (sc.iv) {
                sc.iv.clear();
            }
            m.alog("AFK", "DOGRULANDI " + p.getName() + "(" + u + ")");
            ClaimModule.sound(p, "entity.experience_orb.pickup", 1, 1.4f);
            m.pre(p, "<#a8ff78>Doğrulandı! &7Satışa devam edebilirsin.");
            m.actions.sellAfkBack(p, cid, k);
            return;
        }
        sc.fail++;
        int f = sc.fail;
        String lockMin = Text.num(m.cfg.sellAfkLockMin);
        if (f >= m.cfg.sellAfkFails) {
            sc.lock = System.currentTimeMillis() + (long) (m.cfg.sellAfkLockMin * 60000);
            p.closeInventory();
            ClaimModule.sound(p, "entity.villager.no", 1, 0.8f);
            m.pre(p, "<#ff4b1f>AFK kontrolü başarısız! &7Çiftçi satışı &e" + lockMin + " dakika &7kilitlendi.");
            m.alog("AFK", "KILIT " + p.getName() + "(" + u + ") " + m.cfg.sellAfkFails + " yanlis dogrulama, satis " + lockMin + " dk kilitlendi");
            return;
        }
        ClaimModule.sound(p, "entity.villager.no", 1, 1);
        m.pre(p, "&cYanlış seçim! &7Kalan hak: &e" + (m.cfg.sellAfkFails - f));
        if (cid != null && k != null) {
            m.actions.sellAfkMenu(p, cid, k);
        }
    }
}
