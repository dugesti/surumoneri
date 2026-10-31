package tr.dugecraft.claim;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import tr.dugecraft.util.Econ;
import tr.dugecraft.util.Gui;
import tr.dugecraft.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * claim.sk v15.8 claim yukseltmeleri (2x Farm Hizi / Gece Korumasi).
 * Veri ana claim'de: upg::<ad>::own / on / offt (Claim.upgOwn/upgOn/upgOffT).
 */
final class Upgrades {

    private final ClaimModule m;

    Upgrades(ClaimModule m) {
        this.m = m;
    }

    static String name(String u) {
        return "farm2x".equals(u) ? "2x Farm Hızı" : "Gece Koruması";
    }

    boolean canManage(Player p, String mc) {
        Claim c = m.store.get(mc);
        if (c == null || c.owner == null) {
            return false;
        }
        return c.owner.equals(ClaimModule.uuid(p)) || ClaimModule.isKurucu(p);
    }

    double reopenLeft(String mc, String u) {
        Claim c = m.store.get(mc);
        Double t = c == null ? null : c.upgOffT.get(u);
        if (t == null) {
            return 0;
        }
        double left = (t + m.cfg.upgradeReopenHours * 3600) - ClaimModule.now();
        return Math.max(0, left);
    }

    String stateTxt(String mc, String u) {
        Claim c = m.store.get(mc);
        if (c == null || !c.upgOwn(u)) {
            return "&8yok";
        }
        if (!c.upgOn(u)) {
            return "&cKapalı";
        }
        if ("night".equals(u)) {
            double nl = m.nightWindowLeft();
            if (nl > 0) {
                return "&aŞu an koruyor &8(" + m.fmtDur(nl) + ")";
            }
            return "&aAktif &8(" + m.nightHours() + ")";
        }
        return "&aAktif";
    }

    ItemStack item(String mc, String u) {
        Claim c = m.store.get(mc);
        List<String> l = new ArrayList<>();
        if ("farm2x".equals(u)) {
            l.add("&7Çiftçinin topladığı ürünler &a2 kat&7:");
            l.add("&7mob dropları, kaktüs, bambu, su yosunu,");
            l.add("&7balkabağı ve karpuz farmları.");
            l.add("&8Çiftçi aktif olmalıdır.");
        } else {
            l.add("&7Her gün &e" + m.nightHours() + " &7arası claim");
            l.add("&7yabancılar tarafından &araidlenemez&7.");
            l.add("&7Sahip ve üyeler etkilenmez.");
        }
        l.add("");
        l.add("&7Aktifken enerji tüketimi &cx" + Text.num(m.cfg.upgradeEnergyMult));
        l.add("");
        String title = "<#f6d365>" + name(u);
        if (c == null || !c.upgOwn(u)) {
            l.add("&7Fiyat &8· &a" + ClaimModule.fmtShort(m.cfg.upgradePrice) + "$ &8(tek seferlik)");
            l.add("");
            l.add("&eTıkla &8· &7Satın Al");
        } else if (c.upgOn(u)) {
            l.add("&7Durum &8· " + stateTxt(mc, u));
            l.add("");
            l.add("&eTıkla &8· &7Kapat");
            title = "<#a8ff78>" + name(u);
        } else {
            l.add("&7Durum &8· &cKapalı &8(satın alındı)");
            l.add("");
            double cd = reopenLeft(mc, u);
            if (cd > 0) {
                l.add("&7Tekrar açılabilir &8· &e" + m.fmtDur(cd));
            } else {
                l.add("&eTıkla &8· &7Aç &8(ücretsiz)");
            }
        }
        return Gui.item("farm2x".equals(u) ? Material.GOLDEN_HOE : Material.PHANTOM_MEMBRANE, title, l);
    }

    void menu(Player p, String cid) {
        String mc = m.mainOf(cid);
        if (!canManage(p, mc)) {
            return;
        }
        Inventory inv = Gui.chest(4, "&8Claim Yükseltmeleri");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 35);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 27, 35);
        Claim c = m.store.get(mc);
        double eLeft = (c == null || c.expiry == null ? 0 : c.expiry) - ClaimModule.now();
        inv.setItem(4, Gui.item(Material.BEACON, "<#f6d365>Yükseltmeler", "&7Tüketim çarpanı &8· &cx" + Text.num(m.upgMult(mc)),
                "&7Kalan enerji &8· &e" + m.fmtDur(eLeft), "", "&8Her yükseltme bir kez satın alınır,", "&8sonra istediğin zaman kapatılabilir.",
                "&8Kapattıktan sonra tekrar açmak için", "&8" + Text.num(m.cfg.upgradeReopenHours) + " saat beklenir."));
        inv.setItem(11, item(mc, "farm2x"));
        inv.setItem(15, item(mc, "night"));
        inv.setItem(31, Gui.item(Material.ARROW, "&7Geri", "&8Claim menüsüne döner."));
        Session s = m.session(p);
        s.cid = cid;
        p.openInventory(inv);
        s.menu = "claim_upg_menu";
        s.inv = inv;
    }

    void confirm(Player p, String cid, String u, String act) {
        String mc = m.mainOf(cid);
        double e = m.energyNow(mc);
        double cur = m.drainPerHour(mc);
        double nw = "off".equals(act) ? cur / m.cfg.upgradeEnergyMult : cur * m.cfg.upgradeEnergyMult;
        double lc = (e / cur) * 3600;
        double ln = (e / nw) * 3600;
        List<String> l = new ArrayList<>();
        if ("buy".equals(act)) {
            l.add("&7Ücret &8· &a" + ClaimModule.fmtShort(m.cfg.upgradePrice) + "$ &8(tek seferlik)");
            l.add("&7Satın alınca hemen &aaktif &7olur.");
        } else if ("on".equals(act)) {
            l.add("&7Yükseltme &aaçılacak&7.");
        } else {
            l.add("&7Yükseltme &ckapatılacak&7.");
            l.add("&cKapattıktan sonra " + Text.num(m.cfg.upgradeReopenHours) + " saat açılamaz.");
        }
        l.add("");
        l.add("&7Tüketim &8· &f" + (long) Math.floor(cur) + "&7/saat &8› &c" + (long) Math.floor(nw) + "&7/saat");
        l.add("&7Kalan süre &8· &e" + m.fmtDur(lc) + " &8› &e" + m.fmtDur(ln));
        if (!"off".equals(act) && ln < m.cfg.upgradeMinHours * 3600) {
            l.add("");
            l.add("&cEnerji yetersiz! Açılınca en az");
            l.add("&c" + Text.num(m.cfg.upgradeMinHours) + " saat yetmeli. Önce enerji yükle.");
        }
        Inventory inv = Gui.chest(3, "&8Onay: " + name(u));
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 26);
        inv.setItem(13, Gui.item(Material.PAPER, "<#f6d365>" + name(u), l));
        inv.setItem(11, Gui.item(Material.LIME_DYE, "&aOnayla"));
        inv.setItem(15, Gui.item(Material.RED_STAINED_GLASS_PANE, "&cVazgeç", "&7Yükseltme menüsüne döner."));
        Session s = m.session(p);
        s.cid = cid;
        p.openInventory(inv);
        s.menu = "claim_upg_confirm";
        s.inv = inv;
        s.upgKey = u;
        s.upgAct = act;
    }

    /** Durumu enerji kilidi altinda degistirir. false -> kilit alinamadi (degisiklik YOK). */
    boolean setState(String mc, String u, boolean val) {
        if (!m.reserve("en|" + mc, "upg")) {
            return false;
        }
        Claim c = m.store.get(mc);
        try {
            m.syncEnergyRaw(mc);
            if (c != null) {
                c.upgOn.put(u, val);
            }
            m.syncEnergyRaw(mc);
        } finally {
            m.release("en|" + mc);
        }
        Double exp = c == null ? null : c.expiry;
        for (String cid : m.store.all) {
            if (cid.equals(mc)) {
                continue;
            }
            Claim sc = m.store.get(cid);
            if (sc != null && mc.equals(sc.mainCid)) {
                sc.expiry = exp;
                sc.needsSave = true;
            }
        }
        return true;
    }

    void action(Player p, String cid, String u, String act) {
        String mc = m.mainOf(cid);
        if (m.store.owner(mc) == null) {
            return;
        }
        if (!m.reserve("up|" + mc, ClaimModule.uuid(p))) {
            m.pre(p, "&cBu claim'de şu an başka bir yükseltme işlemi sürüyor, tekrar dene.");
            return;
        }
        try {
            run(p, mc, u, act);
        } finally {
            m.release("up|" + mc);
        }
    }

    private void run(Player p, String mc, String u, String act) {
        if (!canManage(p, mc)) {
            m.pre(p, "&cBu claim'in sahibi değilsin.");
            return;
        }
        if (!"farm2x".equals(u) && !"night".equals(u)) {
            return;
        }
        Claim c = m.store.get(mc);
        if (c == null) {
            return;
        }
        boolean own = c.upgOwn(u);
        boolean on = c.upgOn(u);
        switch (act) {
            case "buy" -> {
                if (own) {
                    m.pre(p, "&cBu yükseltme zaten satın alınmış.");
                    return;
                }
                if ("farm2x".equals(u) && !c.farmerOn()) {
                    ClaimModule.sound(p, "entity.villager.no", 1, 1);
                    m.pre(p, "&c2x Farm Hızı için önce bu claim'de &eÇiftçi&c'yi aktif etmelisin.");
                    return;
                }
            }
            case "on" -> {
                if (!own) {
                    return;
                }
                if (on) {
                    m.pre(p, "&7Bu yükseltme zaten açık.");
                    return;
                }
                double cd = reopenLeft(mc, u);
                if (cd > 0) {
                    m.pre(p, "&cTekrar açmak için beklemelisin. &7Kalan: &e" + m.fmtDur(cd));
                    return;
                }
            }
            case "off" -> {
                if (!on) {
                    m.pre(p, "&7Bu yükseltme zaten kapalı.");
                    return;
                }
            }
            default -> {
                return;
            }
        }
        if (!"off".equals(act)) {
            double newRate = m.drainPerHour(mc) * m.cfg.upgradeEnergyMult;
            double ln = (m.energyNow(mc) / newRate) * 3600;
            if (ln < m.cfg.upgradeMinHours * 3600) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cEnerji yetersiz! &7Açılınca enerjin sadece &e" + m.fmtDur(ln) + " &7yeter. En az &e"
                        + Text.num(m.cfg.upgradeMinHours) + " saat &7olmalı; önce &fClaim Enerjisi&7'nden yükle.");
                return;
            }
        }
        double price = m.cfg.upgradePrice;
        if ("buy".equals(act)) {
            if (Econ.balance(p) < price) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "<#ff4b1f>Yetersiz bakiye! Gerekli: <#f6d365>" + ClaimModule.fmtShort(price) + "$");
                return;
            }
            if (!Econ.withdraw(p, price)) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "<#ff4b1f>Yetersiz bakiye! Gerekli: <#f6d365>" + ClaimModule.fmtShort(price) + "$");
                return;
            }
        }
        boolean val = !"off".equals(act);
        if (!setState(mc, u, val)) {
            if ("buy".equals(act)) {
                Econ.deposit(p, price);
                m.pre(p, "&cİşlem yapılamadı, lütfen tekrar dene. &7(Ücret iade edildi)");
            } else {
                m.pre(p, "&cİşlem yapılamadı, lütfen tekrar dene.");
            }
            return;
        }
        if ("buy".equals(act)) {
            c.upgOwn.put(u, true);
            c.upgOffT.remove(u);
        } else if ("on".equals(act)) {
            c.upgOffT.remove(u);
        } else {
            c.upgOffT.put(u, ClaimModule.now());
        }
        m.save(mc);
        String mult = Text.num(m.upgMult(mc));
        String pn = p.getName() + "(" + p.getUniqueId() + ")";
        if ("buy".equals(act)) {
            m.clog(mc, "&e" + p.getName() + " &a" + name(u) + " yükseltmesini satın aldı");
            m.alog("YUKSELTME", "SATIN_ALMA " + pn + " claim=" + mc + " yukseltme=" + u + " ucret=" + Text.num(price) + " carpan=" + mult);
            ClaimModule.sound(p, "ui.toast.challenge_complete", 1, 1);
            m.pre(p, "<#a8ff78>" + name(u) + " satın alındı ve aktif! &8(-" + ClaimModule.fmtShort(price) + "$)");
        } else if ("on".equals(act)) {
            m.clog(mc, "&e" + p.getName() + " &a" + name(u) + " yükseltmesini açtı");
            m.alog("YUKSELTME", "AC " + pn + " claim=" + mc + " yukseltme=" + u + " carpan=" + mult);
            ClaimModule.sound(p, "block.beacon.activate", 1, 1.4f);
            m.pre(p, "<#a8ff78>" + name(u) + " açıldı.");
        } else {
            m.clog(mc, "&e" + p.getName() + " &c" + name(u) + " yükseltmesini kapattı");
            m.alog("YUKSELTME", "KAPAT " + pn + " claim=" + mc + " yukseltme=" + u + " carpan=" + mult);
            ClaimModule.sound(p, "block.beacon.deactivate", 1, 1.2f);
            m.pre(p, "&7" + name(u) + " &ckapatıldı&7. Tekrar açmak için &e" + Text.num(m.cfg.upgradeReopenHours) + " saat &7beklemelisin.");
        }
        m.pre(p, "&7Enerji tüketimi &cx" + mult + " &8· &7Yeni kalan süre: &e" + m.energyTimeText(mc));
        if ("farm2x".equals(u)) {
            m.store.bumpVer(mc);
        }
        menu(p, mc);
    }
}
