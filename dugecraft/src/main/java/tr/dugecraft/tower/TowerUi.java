package tr.dugecraft.tower;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import tr.dugecraft.util.Econ;
import tr.dugecraft.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** sok.sk 4. YUKSELTME MENUSU + NPC MAGAZASI + esya onarimi. */
@SuppressWarnings("deprecation")
final class TowerUi {

    private final TowerModule m;

    TowerUi(TowerModule m) {
        this.m = m;
    }

    static ItemStack named(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(TowerItems.legacy(name));
        if (lore.length > 0) {
            List<String> l = new ArrayList<>();
            for (String s : lore) {
                l.add(TowerItems.legacy(s));
            }
            meta.setLore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** 1500 -> "1.500" */
    static String fmtMoney(double n) {
        String s = Long.toString((long) Math.floor(n));
        StringBuilder out = new StringBuilder();
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            out.insert(0, s.charAt(i));
            c++;
            if (c % 3 == 0 && i > 0) {
                out.insert(0, '.');
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ //
    //  KULE MENUSU
    // ------------------------------------------------------------------ //

    void towerMenu(Player p, Location loc) {
        String key = Tower.keyOf(loc);
        Tower t = m.store.get(key);
        if (t == null) {
            return;
        }
        PState s = m.ps(p);
        double n = TowerModule.now();
        if (PState.set(s.menucd) && n - s.menucd < 0.3) {
            return;
        }
        s.menucd = n;

        if (t.orphan) {
            Text.send(p, m.cfg.prefix + " <#9ca3af>Bu kule sahipsiz <#4b5563>· <#9ca3af>saldırmaz, kırıp alabilirsin");
            p.getWorld().playSound(p.getLocation(), "block.note_block.bass", 0.6f, 0.7f);
            return;
        }
        String mainCid = t.cid;
        if (!m.claims.canManageTower(p, mainCid)) {
            Text.send(p, m.cfg.prefix + " <#f87171>Bu kuleyi sadece claim sahibi ve üyeleri yönetebilir");
            return;
        }
        int lvl = t.level;
        int hp = m.curHp(t);
        Inventory inv = Bukkit.createInventory(null, 27, TowerItems.legacy("&8Şok Kulesi · Seviye " + lvl));
        ItemStack gray = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack black = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, gray);
        }
        for (int i : new int[]{10, 12, 14, 16}) {
            inv.setItem(i, black);
        }
        String raidText = m.claims.isRaidActive()
                ? "<#f87171>● Aktif <#4b5563>· <#9ca3af>kule kırılabilir"
                : "<#4ade80>● Kapalı <#4b5563>· <#9ca3af>kule korumalı";
        if (m.claims.chunkNightLeft(Tower.chunkId(loc)) > 0) {
            raidText = "<#7dd3fc>● Gece koruması <#4b5563>· <#9ca3af>kule korumalı";
        }
        int max = m.cfg.maxHp();
        String canLine = max > 1
                ? "<#9ca3af>Can          <#e5e7eb>" + hp + "<#4b5563>/<#9ca3af>" + max
                : "<#9ca3af>Kırılma      <#e5e7eb>tek seferde";
        int towerCount = m.store.byCid(mainCid).size();
        String dmg = Text.num(m.cfg.hearts(lvl));
        String rng = Text.num(m.cfg.range(lvl));
        String cd = Text.num(m.cfg.cooldown(lvl));

        inv.setItem(11, m.items.item(lvl, "<#5ac8fa>⚡ <#e5e7eb>Şok Kulesi",
                "<#9ca3af>Seviye <#e5e7eb>" + lvl + "<#4b5563>/<#9ca3af>6",
                "<#4b5563>────────────────────",
                "<#9ca3af>Hasar        <#e5e7eb>" + dmg + " <#9ca3af>kalp",
                "<#9ca3af>Mesafe       <#e5e7eb>" + rng + " <#9ca3af>blok",
                "<#9ca3af>Atış hızı    <#e5e7eb>" + cd + " <#9ca3af>sn",
                canLine,
                "<#9ca3af>Duvar arkası " + m.cfg.wallText(lvl),
                "<#4b5563>────────────────────",
                "<#9ca3af>Claim'deki kule <#e5e7eb>" + towerCount,
                "<#9ca3af>Baskın  " + raidText));
        inv.setItem(13, named(Material.LIGHTNING_ROD, "<#5ac8fa>Enerji durumu",
                "<#4b5563>────────────────────",
                "<#9ca3af>Kule çalışıyor ve düşmanları",
                "<#9ca3af>menzile girdiği an vuruyor.",
                "",
                "<#9ca3af>Hareketsiz hedef de akımla",
                "<#9ca3af>vurulmaya devam eder."));
        int next = lvl + 1;
        if (next > 6) {
            inv.setItem(15, named(Material.NETHER_STAR, "<#fbbf24>✦ <#e5e7eb>Maksimum seviye",
                    "<#4b5563>────────────────────",
                    "<#9ca3af>Bu kule en yüksek seviyede.",
                    "<#9ca3af>Daha fazla yükseltilemez."));
        } else {
            inv.setItem(15, named(Material.NETHER_STAR, "<#4ade80>▲ <#e5e7eb>Seviye " + next + "'e yükselt",
                    "<#4b5563>────────────────────",
                    "<#9ca3af>Hasar        <#e5e7eb>" + dmg + " <#4b5563>→ <#4ade80>" + Text.num(m.cfg.hearts(next)) + " <#9ca3af>kalp",
                    "<#9ca3af>Mesafe       <#e5e7eb>" + rng + " <#4b5563>→ <#4ade80>" + Text.num(m.cfg.range(next)),
                    "<#9ca3af>Atış hızı    <#e5e7eb>" + cd + "sn <#4b5563>→ <#4ade80>" + Text.num(m.cfg.cooldown(next)) + "sn",
                    "<#9ca3af>Duvar arkası " + m.cfg.wallText(lvl) + " <#4b5563>→ " + m.cfg.wallText(next),
                    "<#4b5563>────────────────────",
                    "<#9ca3af>Ücret  <#fbbf24>" + fmtMoney(m.cfg.cost(next)) + "$",
                    "",
                    "<#5ac8fa>› <#e5e7eb>Yükseltmek için tıkla"));
        }
        // KRITIK SIRA: once ac, SONRA isaretle (eski menunun close olayi silmesin)
        p.openInventory(inv);
        s.towerMenu = inv;
        s.towerLoc = key;
    }

    /** Menude yukseltme tiklamasi (ham slot 15). */
    void upgradeClick(Player p) {
        PState s = m.ps(p);
        double n = TowerModule.now();
        if (PState.set(s.upclick) && n - s.upclick < 0.4) {
            return;
        }
        s.upclick = n;
        String key = s.towerLoc;
        Tower t = m.store.get(key);
        if (t == null) {
            return;
        }
        if (!m.claims.canManageTower(p, t.cid)) {
            p.closeInventory();
            return;
        }
        String lockKey = "tu|" + key;
        if (!m.claims.reserve(lockKey, p.getUniqueId().toString())) {
            return;
        }
        int lvl = t.level;
        int next = lvl + 1;
        if (next > 6) {
            m.claims.release(lockKey);
            Text.send(p, m.cfg.prefix + " <#f87171>Bu kule zaten maksimum seviyede!");
            return;
        }
        double cost = m.cfg.cost(next);
        if (Econ.balance(p) < cost) {
            m.claims.release(lockKey);
            Text.send(p, m.cfg.prefix + " <#f87171>Yetersiz bakiye! <#9ca3af>Gereken: <#fbbf24>" + Text.num(cost) + "$");
            p.getWorld().playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
            return;
        }
        if (!Econ.withdraw(p, cost)) {
            m.claims.release(lockKey);
            Text.send(p, m.cfg.prefix + " <#f87171>Yetersiz bakiye! <#9ca3af>Gereken: <#fbbf24>" + Text.num(cost) + "$");
            return;
        }
        t.level = next;
        t.hp = m.cfg.maxHp();
        m.store.reindexRange(t);
        m.claims.release(lockKey);
        m.store.markDirty(t.fcid);

        Location tLoc = t.loc();
        if (Bukkit.isOwnedByCurrentRegion(tLoc)) {
            m.applyModel(tLoc, next);
        } else {
            t.remodel = true;
        }
        p.getWorld().playSound(p.getLocation(), "entity.player.levelup", 1f, 1f);
        Text.send(p, m.cfg.prefix + " <#4ade80>Şok Kulesi <#fbbf24>Seviye " + next + "'ye <#4ade80>yükseltildi! <#9ca3af>(-" + Text.num(cost) + "$)");
        p.closeInventory();
    }

    // ------------------------------------------------------------------ //
    //  MAGAZA
    // ------------------------------------------------------------------ //

    static int shopSlotAmount(int slot) {
        return switch (slot) {
            case 19 -> 1;
            case 21 -> 16;
            case 23 -> 32;
            case 25 -> 64;
            default -> 0;
        };
    }

    private ItemStack shopOption(int n, double bal) {
        double cost = m.cfg.shopPrice * n;
        String state = bal >= cost ? "<#5ac8fa>› <#e5e7eb>Satın almak için tıkla" : "<#f87171>✖ <#9ca3af>Yetersiz bakiye";
        ItemStack it = m.items.item(1, "<#5ac8fa>⚡ <#e5e7eb>Şok Kulesi <#4b5563>· <#fbbf24>" + n + " adet",
                "<#4b5563>────────────────────",
                "<#9ca3af>Claim'ine giren düşmanlara",
                "<#9ca3af>elektrik şoku uygulayan",
                "<#9ca3af>otomatik savunma kulesi.",
                "<#4b5563>────────────────────",
                "<#9ca3af>Hasar        <#e5e7eb>" + Text.num(m.cfg.hearts(1)) + " <#9ca3af>kalp",
                "<#9ca3af>Mesafe       <#e5e7eb>" + Text.num(m.cfg.range(1)) + " <#9ca3af>blok",
                "<#9ca3af>Duvar arkası " + m.cfg.wallText(1),
                "<#4b5563>────────────────────",
                "<#9ca3af>Yerleştirme  <#e5e7eb>claim içi",
                "<#9ca3af>Yükseltme    <#e5e7eb>sağ tık",
                "<#4b5563>────────────────────",
                "<#9ca3af>Adet     <#e5e7eb>" + n,
                "<#9ca3af>Birim    <#e5e7eb>" + fmtMoney(m.cfg.shopPrice) + "$",
                "<#9ca3af>Toplam   <#fbbf24>" + fmtMoney(cost) + "$",
                "",
                state);
        it.setAmount(n);
        return it;
    }

    void refreshShop(Inventory inv, Player p) {
        double bal = Econ.balance(p);
        for (int sl : new int[]{19, 21, 23, 25}) {
            inv.setItem(sl, shopOption(shopSlotAmount(sl), bal));
        }
        inv.setItem(49, named(Material.SUNFLOWER, "<#fbbf24>◆ <#e5e7eb>Bakiye",
                "<#4b5563>────────────────────",
                "<#e5e7eb>" + fmtMoney(bal) + "<#9ca3af>$"));
    }

    /** SADECE oyuncunun kendi bolge thread'inde; degilse siraya alinir. */
    void openShop(Player p) {
        if (!Bukkit.isOwnedByCurrentRegion(p)) {
            m.ps(p).shopPending = TowerModule.now();
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, TowerItems.legacy("&8Şok Kulesi · Mağaza"));
        ItemStack black = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i <= 53; i++) {
            inv.setItem(i, black);
        }
        refreshShop(inv, p);
        p.openInventory(inv);
        m.ps(p).shop = inv;
        p.getWorld().playSound(p.getLocation(), "block.note_block.harp", 0.6f, 1.8f);
    }

    /** Envantere (ana 36 slot) bu kuleden en az want adet sigar mi? Sigan adet. */
    private int shopRoom(Player p, ItemStack i, int want) {
        PlayerInventory pinv = p.getInventory();
        int room = 0;
        for (int n = 0; n <= 35; n++) {
            ItemStack s = pinv.getItem(n);
            if (s == null || s.getType() == Material.AIR) {
                room += 64;
            } else if (s.isSimilar(i) && s.getAmount() < 64) {
                room += 64 - s.getAmount();
            }
            if (room >= want) {
                return room;
            }
        }
        return room;
    }

    void shopClick(Player p, Inventory shopInv, int raw, ItemStack cursor) {
        int adet = shopSlotAmount(raw);
        if (adet <= 0) {
            return;
        }
        PState s = m.ps(p);
        double n = TowerModule.now();
        if (PState.set(s.shopclick) && n - s.shopclick < 0.4) {
            return;
        }
        s.shopclick = n;
        if (cursor != null && cursor.getType() != Material.AIR) {
            Text.send(p, m.cfg.prefix + " <#f87171>Önce imlecindeki eşyayı bırak.");
            return;
        }
        repairInv(p);
        ItemStack item = m.items.item(1);
        int room = shopRoom(p, item, adet);
        if (room < adet) {
            if (room > 0) {
                Text.send(p, m.cfg.prefix + " <#f87171>Envanterinde sadece <#fbbf24>" + room + " <#f87171>kule için yer var!");
            } else {
                Text.send(p, m.cfg.prefix + " <#f87171>Envanterinde yer yok!");
            }
            p.getWorld().playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
            return;
        }
        double cost = m.cfg.shopPrice * adet;
        if (Econ.balance(p) < cost || !Econ.withdraw(p, cost)) {
            Text.send(p, m.cfg.prefix + " <#f87171>Yetersiz bakiye! <#9ca3af>Gereken: <#4ade80>" + fmtMoney(cost) + "$");
            p.getWorld().playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
            return;
        }
        item.setAmount(adet);
        HashMap<Integer, ItemStack> kalan = p.getInventory().addItem(item);
        int artan = 0;
        for (ItemStack k : kalan.values()) {
            artan += k.getAmount();
        }
        // GUVENLIK AGI: sigmayan kisim yere dusurulmez, parasi iade edilir
        if (artan > 0) {
            Econ.deposit(p, artan * m.cfg.shopPrice);
            Text.send(p, m.cfg.prefix + " <#fbbf24>" + artan + " adet sığmadı, parası iade edildi.");
            Text.console(m.cfg.prefix + " <#fbbf24>Mağaza: " + p.getName() + " için " + artan + " adet sığmadı, iade edildi.");
        }
        int alinan = adet - artan;
        if (alinan > 0) {
            double odenen = alinan * m.cfg.shopPrice;
            Text.send(p, m.cfg.prefix + " <#4ade80>" + alinan + " adet Şok Kulesi satın alındı! <#9ca3af>(-" + fmtMoney(odenen) + "$)");
            p.getWorld().playSound(p.getLocation(), "entity.experience_orb.pickup", 1f, 1.2f);
            m.claims.alog("KULE", "MAGAZA_ALIM " + p.getName() + "(" + p.getUniqueId() + ") adet=" + alinan + " ucret=" + Text.num(odenen));
        }
        refreshShop(shopInv, p);
    }

    // ------------------------------------------------------------------ //
    //  ESYA ONARIMI (oyuncunun kendi thread'inde)
    // ------------------------------------------------------------------ //

    private void repairLog(String who, int lv, int amt, String where) {
        Text.console(m.cfg.prefix + " <#fbbf24>Bozulmuş kule eşyası onarıldı: <#e5e7eb>" + who + " <#9ca3af>Sv" + lv + " x" + amt + " <#4b5563>· <#9ca3af>" + where);
        m.claims.alog("KULE", "ESYA_ONARIM " + who + " seviye=" + lv + " adet=" + amt + " yer=" + where);
    }

    void repairInv(Player p) {
        PlayerInventory pinv = p.getInventory();
        for (int n = 0; n <= 40; n++) {
            ItemStack s = pinv.getItem(n);
            if (s == null || s.getType() != Material.NOTE_BLOCK) {
                continue;
            }
            int lv = m.items.level(s);
            if (lv <= 0) {
                continue;
            }
            boolean wasOk = TowerItems.cmdOk(s, lv);
            ItemStack fx = m.items.repair(s);
            if (fx != null) {
                pinv.setItem(n, fx);
                if (!wasOk) {
                    repairLog(p.getName(), lv, fx.getAmount(), "envanter slot " + n);
                }
            }
        }
    }

    void repairTop(Player p, Inventory inv) {
        String tn = inv.getType().name();
        boolean ok = false;
        if (tn.equals("ENDER_CHEST")) {
            ok = true;
        } else if (tn.equals("CHEST") || tn.equals("BARREL") || tn.equals("SHULKER_BOX")) {
            Location il = inv.getLocation();
            if (il != null && Bukkit.isOwnedByCurrentRegion(il)) {
                ok = true;
            }
        }
        if (!ok) {
            return;
        }
        for (int n = 0; n < inv.getSize(); n++) {
            ItemStack s = inv.getItem(n);
            if (s == null || s.getType() != Material.NOTE_BLOCK) {
                continue;
            }
            int lv = m.items.level(s);
            if (lv <= 0) {
                continue;
            }
            boolean wasOk = TowerItems.cmdOk(s, lv);
            ItemStack fx = m.items.repair(s);
            if (fx != null) {
                inv.setItem(n, fx);
                if (!wasOk) {
                    repairLog(p.getName(), lv, fx.getAmount(), tn + " slot " + n);
                }
            }
        }
    }
}
