package tr.dugecraft.claim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import tr.dugecraft.util.Gui;
import tr.dugecraft.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

/** claim.sk 3. MENU OLUSTURMA FONKSIYONLARI (1879-3100). */
final class ClaimMenus {

    static final int[] FARM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 40};

    private final ClaimModule m;

    ClaimMenus(ClaimModule m) {
        this.m = m;
    }

    private Session open(Player p, String menu, Inventory inv) {
        Session s = m.session(p);
        s.menu = menu;
        s.inv = inv;
        return s;
    }

    static String n(Number v) {
        return Text.num(v);
    }

    // ------------------------------------------------------------------ //
    //  CLAIMLERIM
    // ------------------------------------------------------------------ //

    void myClaims(Player p) {
        if (!m.inClaimWorld(p)) {
            return;
        }
        Inventory inv = Gui.chest(3, "&8Claimlerim ve Üyeliklerim");
        Session s = m.session(p);
        s.slotTags.clear();
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 26);
        String u = ClaimModule.uuid(p);
        List<String> mains = new ArrayList<>();
        List<String> memberOf = new ArrayList<>();
        for (String cid : m.store.all) {
            Claim c = m.store.get(cid);
            if (c == null || !c.main()) {
                continue;
            }
            if (u.equals(c.owner)) {
                mains.add(cid);
            } else if (c.members.contains(u)) {
                memberOf.add(cid);
            }
        }
        double now = ClaimModule.now();
        int maxMain = m.maxMainClaims(p);
        int ownN = mains.size();
        int show = Math.min(9, Math.max(maxMain, ownN));
        List<Integer> os = new ArrayList<>();
        if (show <= 1) {
            os.add(13);
        } else if (show == 2) {
            os.addAll(List.of(11, 15));
        } else if (show == 3) {
            os.addAll(List.of(11, 13, 15));
        } else if (show == 4) {
            os.addAll(List.of(10, 12, 14, 16));
        } else if (show == 5) {
            os.addAll(List.of(9, 11, 13, 15, 17));
        } else {
            int s0 = 9 + (9 - show) / 2;
            for (int i = 0; i < show; i++) {
                os.add(s0 + i);
            }
        }
        int i = 1;
        for (int oslot : os) {
            if (i <= ownN) {
                String ocid = mains.get(i - 1);
                Claim c = m.store.get(ocid);
                double diff = (c.expiry == null ? 0 : c.expiry) - now;
                String oTime;
                if (diff > 0) {
                    oTime = "&e" + (long) Math.floor(diff / 86400) + " Gün " + (long) Math.floor((diff % 86400) / 3600) + " Saat";
                } else {
                    oTime = "&cSüresi Doldu";
                }
                inv.setItem(oslot, Gui.item(Material.PAPER, "<#a8ff78>" + c.claimName, "&7Tür: &eKurucu", "&7Kalan Süre: " + oTime, "", "&eTıkla &8· &7Yönetim Menüsünü Aç"));
                s.slotTags.put(oslot, ocid);
            } else {
                inv.setItem(oslot, Gui.item(Material.PAPER, "&a" + i + ". Claim (Boş Slot)", "&7" + i + ". claim hakkın boş.",
                        "&7Hak &8· &f" + ownN + "&7/&f" + maxMain, "", "&eTıkla &8· &7Yeni Claim Satın Al"));
                s.slotTags.put(oslot, "new");
            }
            i++;
        }
        inv.setItem(4, Gui.item(Material.NAME_TAG, "<#f6d365>Claimlerim", "&7Sahip olduğun &f" + ownN + "&7/&f" + maxMain + " &7claim",
                "&7Üyesi olduğun &f" + memberOf.size() + " &7claim", "",
                "&7Claim hakkı &8· &fNormal " + m.cfg.maxMainClaims + " &8| &aVIP " + m.cfg.maxMainClaimsVip + " &8| &bDVIP "
                        + m.cfg.maxMainClaimsDvip + " &8| &dDVIP+ " + m.cfg.maxMainClaimsDvipPlus));
        int mN = Math.min(9, memberOf.size());
        int slot = 18 + (9 - mN) / 2;
        for (String mcid : memberOf) {
            Claim c = m.store.get(mcid);
            inv.setItem(slot, Gui.item(Material.MAP, "&b" + c.claimName, "&7Tür: &3Üye", "&7Sahibi: &e" + c.ownerName, "", "&eTıkla &8· &7Menüyü Aç"));
            s.slotTags.put(slot, mcid);
            slot++;
            if (slot > 26) {
                break;
            }
        }
        p.openInventory(inv);
        open(p, "my_claims_menu", inv);
    }

    // ------------------------------------------------------------------ //
    //  CLAIM MENUSU
    // ------------------------------------------------------------------ //

    ItemStack cmItem(String act, Player p, String mainCid) {
        switch (act) {
            case "tp":
                return Gui.item(Material.ENDER_PEARL, "<#a8ff78>Claime Işınlan", "&7Claim merkezine ışınlanırsın.", "&75 saniye hareket etmemelisin.", "", "&eTıkla &8· &7Işınlan");
            case "members":
                return Gui.item(Material.BOOK, "<#ff9a9e>Üye Yönetimi", "&7Üyeleri gör, yetkilerini düzenle", "&7veya yeni üye davet et.", "", "&eTıkla &8· &7Aç");
            case "map":
                return Gui.item(Material.MAP, "<#f6d365>Bölge Satın Al", "&7Çevredeki bölgeleri haritada gör,", "&7satın al veya sil.", "", "&eTıkla &8· &7Haritayı Aç");
            case "energy":
                return Gui.item(Material.REDSTONE, "<#00d2ff>Claim Enerjisi", "&7Enerji bitince claim patlar.", "&7Chunk arttıkça tüketim de artar.", "", "&eTıkla &8· &7Aç");
            case "farmer":
                return Gui.item(Material.HAY_BLOCK, "<#f6d365>Çiftçi", "&7Claimdeki ürünleri otomatik", "&7toplar ve depolar.", "", "&eTıkla &8· &7Aç");
            case "upgrades":
                return Gui.item(Material.BEACON, "<#f6d365>Yükseltmeler", "&72x Farm Hızı ve Gece Koruması", "&7satın al, aç veya kapat.", "", "&7Aktif olan her yükseltme",
                        "&7enerji tüketimini &cx" + n(m.cfg.upgradeEnergyMult) + " &7yapar.", "", "&eTıkla &8· &7Aç");
            case "logs":
                return Gui.item(Material.CLOCK, "<#f6d365>Claim Kayıtları", "&7Sandık, spawner, şok kulesi,", "&7patlama, üye ve çiftçi işlemleri.", "", "&eTıkla &8· &7Aç");
            case "stone": {
                double moveLeft = 0;
                Claim c = m.store.get(mainCid);
                if (c != null && !Double.isNaN(c.moveTime)) {
                    moveLeft = 600 - (ClaimModule.now() - c.moveTime);
                }
                if (moveLeft > 0) {
                    return Gui.item(Material.GRAY_DYE, "&7Claim Taşını Taşı", "&7Taşı bulunduğun noktaya taşır.", "", "&7Bekleme &8· &f" + (long) Math.ceil(moveLeft / 60) + " dakika");
                }
                return Gui.item(Material.LODESTONE, "<#fbbf24>Claim Taşını Taşı", "&7Taşı bulunduğun noktaya taşır.", "&7Sadece ana claim chunk'ı içinde,", "&710 dakikada bir.", "", "&eTıkla &8· &7Buraya Taşı");
            }
            case "perms": {
                String u = ClaimModule.uuid(p);
                Map<String, String> pv = new HashMap<>();
                for (String k : new String[]{"spawner_break", "farmer", "teleport", "sethome", "logs"}) {
                    pv.put(k, m.permOf(mainCid, u, k) ? "&aAçık" : "&8Kapalı");
                }
                return Gui.item(Material.PAPER, "<#ff9a9e>Yetkilerim", "&7Spawner / Kule Sökme &8· " + pv.get("spawner_break"), "&7Çiftçi Erişimi &8· " + pv.get("farmer"),
                        "&7Işınlanma &8· " + pv.get("teleport"), "&7Sethome &8· " + pv.get("sethome"), "&7Kayıtları Görme &8· " + pv.get("logs"), "",
                        "&8Kırma, koyma, sandık ve kapılar", "&8baskın açık olduğu için herkese açık.", "&8Yetkileri claim sahibi değiştirir.");
            }
            case "delete":
                return Gui.item(Material.TNT, "<#ff6b6b>Claim'i Sil", "&7Claim'i ve tüm bölgelerini siler.", "&7Bu işlem geri alınamaz.", "", "&cTıkla &8· &7Onay Ekranı");
            case "leave":
                return Gui.item(Material.RED_DYE, "<#ff6b6b>Claim'den Ayrıl", "&7Üyeliğin ve yetkilerin silinir.", "&7Tekrar katılmak için davet gerekir.", "", "&cTıkla &8· &7Onay Ekranı");
            case "chat":
                if (mainCid.equals(m.session(p).cchat)) {
                    return Gui.item(Material.LIME_DYE, "<#ff9a3c>Claim Sohbeti &8· &aAçık", "&7Yazdığın mesajlar sadece bu", "&7claim'in sahibine ve üyelerine gider.", "", "&7Kısayol &8· &f/claim sohbet", "", "&eTıkla &8· &7Kapat");
                }
                return Gui.item(Material.GRAY_DYE, "<#ff9a3c>Claim Sohbeti &8· &cKapalı", "&7Açınca yazdığın mesajlar sadece bu", "&7claim'in sahibine ve üyelerine gider.", "", "&7Kısayol &8· &f/claim sohbet", "", "&eTıkla &8· &7Aç");
            default:
                return Gui.item(Material.ARROW, "&7Claimlerim", "&8Claim listesine döner.");
        }
    }

    private void cmPlace(Player p, Inventory inv, Session s, int center, List<String> acts, String mainCid) {
        int nn = acts.size();
        if (nn < 1) {
            return;
        }
        int slot = center - (nn - 1);
        for (String a : acts) {
            inv.setItem(slot, cmItem(a, p, mainCid));
            s.slotTags.put(slot, a);
            slot += 2;
        }
    }

    void claimMenu(Player p, String cid) {
        if (!m.inClaimWorld(p)) {
            return;
        }
        Claim c = m.store.get(cid);
        String cName = c == null || c.claimName == null ? "Claim" : c.claimName;
        String mainCid = m.mainOf(cid);
        Claim mc = m.store.get(mainCid);
        String u = ClaimModule.uuid(p);
        boolean isOwner = false;
        boolean kurucuView = false;
        if (c != null && u.equals(c.owner)) {
            isOwner = true;
        } else if (ClaimModule.isKurucu(p)) {
            isOwner = true;
            kurucuView = true;
            m.alog("KURUCU", p.getName() + "(" + u + ") baskasinin claim menusunu acti claim=" + mainCid + " sahip=" + (mc == null ? "<none>" : mc.ownerName));
        }
        boolean isMember = !isOwner && m.isMemberUuid(mainCid, u);
        boolean hasTeleport = m.permOf(mainCid, u, "teleport");
        boolean hasFarmer = m.permOf(mainCid, u, "farmer");

        Inventory inv = Gui.chest(4, "&8" + cName);
        Session s = m.session(p);
        s.slotTags.clear();
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 35);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 27, 35);

        String ownName = mc == null || mc.ownerName == null ? "Bilinmiyor" : mc.ownerName;
        int chunks = mc == null ? 1 : mc.chunkCount;
        double eLeft = (mc == null || mc.expiry == null ? 0 : mc.expiry) - ClaimModule.now();
        String role = "&bÜye";
        if (kurucuView) {
            role = "&dKurucu yetkisi";
        } else if (isOwner) {
            role = "&aSahip";
        } else if (!isMember) {
            role = "&7Ziyaretçi";
        }
        double pLeft = m.claimProtectLeft(mainCid);
        String protTxt = pLeft > 0 ? "&a" + m.fmtDur(pLeft) : "&8yok";
        List<String> hl = new ArrayList<>(List.of("&7Sahip &8· &f" + ownName, "&7Rolün &8· " + role, "", "&7Boyut &8· &f" + chunks + " chunk",
                "&7Kalan enerji &8· &e" + m.fmtDur(eLeft), "&7Yeni claim koruması &8· " + protTxt));
        if (mc != null && mc.upgOwn("night")) {
            hl.add("&7Gece koruması &8· " + m.upgrades.stateTxt(mainCid, "night"));
        }
        if (mc != null && mc.upgOwn("farm2x")) {
            hl.add("&72x Farm Hızı &8· " + m.upgrades.stateTxt(mainCid, "farm2x"));
        }
        inv.setItem(4, Gui.item(Material.NAME_TAG, "<#f6d365>" + cName, hl));

        List<String> r1 = new ArrayList<>();
        if (isOwner) {
            r1.addAll(List.of("tp", "members", "map", "energy"));
        } else {
            if (hasTeleport) {
                r1.add("tp");
            }
            if (isMember) {
                r1.add("perms");
            }
        }
        cmPlace(p, inv, s, 13, r1, mainCid);
        List<String> r2 = new ArrayList<>();
        if (isOwner || hasFarmer) {
            r2.add("farmer");
        }
        if (isOwner) {
            r2.add("upgrades");
        }
        if (m.canViewLogs(p, mainCid)) {
            r2.add("logs");
        }
        if (isOwner) {
            r2.add("stone");
        }
        if (m.chat.canUse(u, mainCid)) {
            r2.add("chat");
        }
        cmPlace(p, inv, s, 22, r2, mainCid);
        List<String> r3 = new ArrayList<>();
        r3.add("back");
        if (isOwner) {
            r3.add("delete");
        } else if (isMember) {
            r3.add("leave");
        }
        cmPlace(p, inv, s, 31, r3, mainCid);
        s.cid = cid;
        p.openInventory(inv);
        open(p, "claim_menu", inv);
    }

    void leaveConfirm(Player p, String cid) {
        Claim c = m.store.get(cid);
        String cName = c == null || c.claimName == null ? "Claim" : c.claimName;
        Inventory inv = Gui.chest(3, "&4Claim'den Ayrıl");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 26);
        inv.setItem(13, Gui.item(Material.PAPER, "&e" + cName, "&7Bu claim'den ayrılmak", "&7üzeresin. Emin misin?"));
        inv.setItem(11, Gui.item(Material.LIME_DYE, "&aEvet, Ayrıl", "&7Üyeliğin ve yetkilerin silinir."));
        inv.setItem(15, Gui.item(Material.RED_STAINED_GLASS_PANE, "&cVazgeç", "&7Claim menüsüne döner."));
        m.session(p).cid = cid;
        p.openInventory(inv);
        open(p, "claim_leave_confirm", inv);
    }

    // ------------------------------------------------------------------ //
    //  KAYITLAR
    // ------------------------------------------------------------------ //

    void logMenu(Player p, String cid, int page) {
        String mainCid = m.mainOf(cid);
        if (!m.canViewLogs(p, mainCid)) {
            m.pre(p, "&cBu claim'in kayıtlarını görme yetkin yok.");
            return;
        }
        if (page < 1) {
            page = 1;
        }
        Inventory inv = Gui.chest(6, "&8Claim Kayıtları &7(Sayfa " + page + ")");
        for (int i = 45; i <= 53; i++) {
            inv.setItem(i, Gui.item(Material.GRAY_STAINED_GLASS_PANE, "&f"));
        }
        LinkedBlockingDeque<String> dq = m.store.clog.get(mainCid);
        List<String> all = dq == null ? List.of() : new ArrayList<>(dq);
        int total = all.size();
        int i = total - (page - 1) * 45;
        int slot = 0;
        while (i >= 1) {
            if (slot >= 45) {
                break;
            }
            inv.setItem(slot, Gui.item(Material.PAPER, all.get(i - 1)));
            slot++;
            i--;
        }
        if (total == 0) {
            inv.setItem(22, Gui.item(Material.BARRIER, "&7Henüz kayıt yok"));
        }
        if (page > 1) {
            inv.setItem(45, Gui.item(Material.ARROW, "&e« Önceki Sayfa"));
        }
        if (i >= 1) {
            inv.setItem(53, Gui.item(Material.ARROW, "&eSonraki Sayfa ›"));
        }
        inv.setItem(49, Gui.item(Material.ARROW, "&7Geri", "&8Claim menüsüne döner.", "", "&7Toplam kayıt &8· &f" + total, "&8Claim başına son 150 işlem tutulur."));
        Session s = m.session(p);
        s.cid = cid;
        s.logPage = page;
        p.openInventory(inv);
        open(p, "claim_log_menu", inv);
    }

    // ------------------------------------------------------------------ //
    //  CIFTCI MENULERI
    // ------------------------------------------------------------------ //

    static ItemStack farmItem(String k) {
        Material mt = Farmer.material(k);
        return new ItemStack(mt == null ? Material.BARRIER : mt);
    }

    String viewVer(String mc) {
        return m.store.ver(mc) + "-" + m.fpriceVer.get();
    }

    private long fviewSeq;

    void viewRegister(Player p, String mc, String mode) {
        Session s = m.session(p);
        synchronized (this) {
            fviewSeq++;
            s.fviewTok = fviewSeq;
        }
        s.fview = mc;
        s.fviewMode = mode;
        s.fviewVer = viewVer(mc);
    }

    void viewClear(Session s) {
        s.fview = null;
        s.fviewMode = null;
        s.fviewTok = 0;
        s.fviewVer = null;
    }

    void fillFarmer(Player p, Inventory inv, String mc) {
        Claim c = m.store.get(mc);
        int lvl = c == null ? 1 : c.farmerLevel;
        double maxCap = m.farmerCap(lvl);
        Session s = m.session(p);
        double sumM = 0;
        int sumN = 0;
        int i = 0;
        for (String k : Farmer.KEYS) {
            int slot = FARM_SLOTS[i];
            long amt = m.store.fGet(mc, k);
            double price = m.price(k);
            List<String> pl = m.prodLore(mc, k, price);
            if (amt >= maxCap) {
                pl.add("&cDepo dolu! &7Fazlası kaybolur.");
            }
            double pr = m.prodRate(mc, k);
            if (pr > 0 && price > 0) {
                sumM += pr * price;
                sumN++;
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Depo: &e" + amt + " &8/ &6" + n(maxCap));
            lore.add("&7Satış Fiyatı (1x): " + ClaimModule.farmerPriceText(price));
            lore.addAll(pl);
            lore.add("");
            lore.add("&eTıkla &8· &7&fİşlem Menüsünü Aç");
            inv.setItem(slot, Gui.item(farmItem(k), "&a" + Farmer.name(k), lore));
            s.slotTags.put(slot, k);
            i++;
        }
        if (sumM > 0) {
            inv.setItem(4, Gui.item(Material.SUNFLOWER, "<#f6d365>Çiftçi Getirisi", "&7Üreten ürün &8· &f" + sumN, "",
                    "&7Dakikalık &8· &a" + ClaimModule.fmtShort(sumM) + "$", "&7Saatlik &8· &a" + ClaimModule.fmtShort(sumM * 60) + "$",
                    "&7Günlük &8· &a" + ClaimModule.fmtShort(sumM * 1440) + "$", "", "&8Son ~10 dakikanın üretimine ve",
                    "&8güncel market fiyatına göre tahmin.", "&8Depoya giren ve oto satılanlar sayılır."));
        } else {
            inv.setItem(4, Gui.item(Material.SUNFLOWER, "<#f6d365>Çiftçi Getirisi", "&7Şu an üretim yok.", "", "&8Ürün toplandıkça birkaç dakika", "&8içinde getiri burada görünür."));
        }
        if (lvl >= 8) {
            inv.setItem(51, Gui.item(Material.NETHER_STAR, "&aMaksimum Seviye (8)", "&7Çiftçiniz en yüksek kapasitededir!"));
        } else {
            double cost = 1000 * Math.pow(2, lvl);
            double nextCap = m.cfg.farmerBaseCap * Math.pow(2, lvl);
            inv.setItem(51, Gui.item(Material.NETHER_STAR, "&eÇiftçiyi Yükselt (Seviye " + (lvl + 1) + ")", "&7Yükseltme Ücreti: &a" + n(cost) + "$",
                    "&7Yeni Kapasite: &e" + n(nextCap), "", "&aTıkla &8· &7Satın Al"));
        }
        int onCount = 0;
        if (c != null) {
            for (String k : Farmer.KEYS) {
                if (c.asOn.containsKey(k)) {
                    onCount++;
                }
            }
        }
        inv.setItem(47, Gui.item(Material.HOPPER, "&eOtomatik Satış", "&7Satılan ürün: &a" + onCount + "&7/&f" + Farmer.KEYS.size(),
                "&7Para claim sahibinin hesabına gider.", "", "&7Açmak için &dDVIP+ &7gerekir.", "&7Claim sahibi her zaman kapatabilir.", "", "&eTıkla &8· &7Menüyü Aç"));
        inv.setItem(49, Gui.item(Material.ARROW, "&7Geri", "&8Claim menüsüne döner."));
    }

    void fillFarmerAs(Player p, Inventory inv, String mc) {
        boolean canOn = m.asCanTurnOn(p, mc);
        boolean canOff = m.asCanTurnOff(p, mc);
        Claim c = m.store.get(mc);
        Session s = m.session(p);
        int i = 0;
        int onC = 0;
        for (String k : Farmer.KEYS) {
            int slot = FARM_SLOTS[i];
            String nm = Farmer.name(k);
            if (c != null && c.asOn.containsKey(k)) {
                onC++;
                String byName = c.asOnName.getOrDefault(k, "Bilinmiyor");
                String hint = canOff ? "&eTıkla &8· &7Kapat" : "&8Değiştirme yetkiniz yok";
                inv.setItem(slot, Gui.item(farmItem(k), "&a" + nm, List.of("&7Oto Satış: &aAÇIK", "&7Açan: &f" + byName, "&7Düştüğü an satılır, para", "&7claim sahibine gider.", "", hint)));
            } else {
                String hint = canOn ? "&eTıkla &8· &7Aç" : "&cAçmak için &dDVIP+ &cgerekir";
                inv.setItem(slot, Gui.item(farmItem(k), "&c" + nm, List.of("&7Oto Satış: &cKAPALI", "&7Bu ürün depoya eklenir.", "", hint)));
            }
            s.slotTags.put(slot, k);
            i++;
        }
        inv.setItem(4, Gui.item(Material.HOPPER, "<#f6d365>Otomatik Satış", "&7Satılan ürün &8· &a" + onC + "&7/&f" + Farmer.KEYS.size(), "",
                "&7Yeşil ürünler düştüğü an satılır,", "&7kırmızılar depolanır. Para claim", "&7sahibinin hesabına gider."));
        inv.setItem(48, Gui.item(Material.LIME_DYE, "&aTümünü Aç", "&7Tüm ürünler otomatik satılır.", "&7Para claim sahibine gider.", "", "&dDVIP+ &7gerekir."));
        inv.setItem(50, Gui.item(Material.GRAY_DYE, "&cTümünü Kapat", "&7Hiçbir ürün satılmaz, hepsi depolanır.", "", "&7Claim sahibi her zaman kapatabilir."));
        inv.setItem(49, Gui.item(Material.ARROW, "&7Geri", "&8Çiftçi deposuna döner."));
    }

    void farmerAs(Player p, String cid) {
        String mc = m.mainOf(cid);
        Claim c = m.store.get(mc);
        if (c == null || !c.farmerOn()) {
            return;
        }
        m.validateAutoSell(mc);
        Inventory inv = Gui.chest(6, "&8Otomatik Satış");
        m.session(p).slotTags.clear();
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 53);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 45, 53);
        fillFarmerAs(p, inv, mc);
        m.session(p).cid = cid;
        p.openInventory(inv);
        open(p, "farmer_as_menu", inv);
        viewRegister(p, mc, "as_menu");
    }

    void fillFarmerItem(Player p, Inventory inv, String mc, String k) {
        String nm = Farmer.name(k);
        long amt = m.store.fGet(mc, k);
        Claim c = m.store.get(mc);
        int lvl = c == null ? 1 : c.farmerLevel;
        double maxCap = m.farmerCap(lvl);
        double price = m.price(k);
        List<String> lore = new ArrayList<>();
        lore.add("&7Mevcut Miktar: &e" + amt + " &8/ &6" + n(maxCap));
        lore.add("&7Birim Fiyatı: " + ClaimModule.farmerPriceText(price));
        lore.addAll(m.prodLore(mc, k, price));
        inv.setItem(4, Gui.item(farmItem(k), "&a" + nm + " Bilgisi", lore));
        inv.setItem(10, Gui.item(Material.CHEST, "&bEnvantere Al (1x)", "&7Depodan 1 adet " + nm + " alır."));
        inv.setItem(11, Gui.item(Material.CHEST, "&bEnvantere Al (32x)", "&7Depodan 32 adet " + nm + " alır."));
        inv.setItem(12, Gui.item(Material.CHEST, "&bEnvantere Al (64x)", "&7Depodan 64 adet " + nm + " alır."));
        inv.setItem(14, Gui.item(Material.GOLD_NUGGET, "&eSat (1x)", "&71 adet satar.", "&7Kazanç: &a" + ClaimModule.fmtPrice(price) + "$"));
        inv.setItem(15, Gui.item(Material.GOLD_INGOT, "&eSat (32x)", "&732 adet satar.", "&7Kazanç: &a" + ClaimModule.fmtPrice(price * 32) + "$"));
        inv.setItem(16, Gui.item(Material.GOLD_BLOCK, "&eSat (64x)", "&764 adet satar.", "&7Kazanç: &a" + ClaimModule.fmtPrice(price * 64) + "$"));
        inv.setItem(22, Gui.item(Material.EMERALD, "&aHepsini Sat", "&7Tümünü satar.", "&7Tahmini Kazanç: &a" + ClaimModule.fmtPrice(amt * price) + "$"));
        inv.setItem(31, Gui.item(Material.ARROW, "&7Geri", "&8Çiftçi deposuna döner."));
    }

    void farmer(Player p, String cid) {
        String mc = m.mainOf(cid);
        Claim c = m.store.get(mc);
        Session s = m.session(p);
        if (c == null || !c.farmerOn()) {
            Inventory inv = Gui.chest(3, "&8Çiftçi Satın Al");
            Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 26);
            inv.setItem(13, Gui.item(Material.HAY_BLOCK, "&aÇiftçiyi Aktif Et", "&7Tüm claimdeki desteklenen ekinleri", "&7ve dropları otomatik toplar.", "", "&7Açılış Ücreti: &e1500$", "", "&aTıkla &8· &7Satın Al"));
            inv.setItem(22, Gui.item(Material.ARROW, "&7Geri", "&8Claim menüsüne döner."));
            s.cid = cid;
            p.openInventory(inv);
            open(p, "farmer_buy_menu", inv);
            return;
        }
        Inventory inv = Gui.chest(6, "&8Çiftçi Deposu");
        s.slotTags.clear();
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 53);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 45, 53);
        fillFarmer(p, inv, mc);
        s.cid = cid;
        p.openInventory(inv);
        open(p, "farmer_menu", inv);
        viewRegister(p, mc, "main");
    }

    void farmerItem(Player p, String cid, String k) {
        String mc = m.mainOf(cid);
        Inventory inv = Gui.chest(4, "&8Ürün İşlemleri: " + Farmer.name(k));
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 35);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 27, 35);
        fillFarmerItem(p, inv, mc, k);
        Session s = m.session(p);
        s.cid = cid;
        s.farmerItemKey = k;
        p.openInventory(inv);
        open(p, "farmer_item_menu", inv);
        viewRegister(p, mc, k);
    }

    // ------------------------------------------------------------------ //
    //  CHUNK ISARETI
    // ------------------------------------------------------------------ //

    void markChunk(Player p, String w, int cx, int cz, int r, int g, int b, String col) {
        Session s = m.session(p);
        revertMark(p);
        s.cmarkW = w;
        s.cmarkX = cx;
        s.cmarkZ = cz;
        s.cmarkC = new Particle.DustOptions(Color.fromRGB(r, g, b), 2.5f);
        s.cmarkUntil = ClaimModule.now() + 20;
        drawMark(p, s);
        showOutline(p, cx, cz, col);
        ClaimModule.sound(p, "block.note_block.bell", 0.8f, 1.6f);
        m.pre(p, "&aSeçtiğin bölge dünyada işaretlendi! &7(Chunk &f" + cx + ", " + cz + "&7 · 20 saniye)");
        m.send(p, "&8› &7Blok sınırları: &fX " + (cx * 16) + " ~ " + (cx * 16 + 15) + " &8| &fZ " + (cz * 16) + " ~ " + (cz * 16 + 15));
    }

    void drawMark(Player p, Session s) {
        Particle.DustOptions dust = s.cmarkC;
        if (dust == null || !p.getWorld().getName().equals(s.cmarkW)) {
            return;
        }
        int bx = s.cmarkX * 16;
        int bz = s.cmarkZ * 16;
        Location pl = p.getLocation();
        if (Math.abs(pl.getX() - (bx + 8)) > 64 || Math.abs(pl.getZ() - (bz + 8)) > 64) {
            return;
        }
        World wo = pl.getWorld();
        double y = pl.getY() + 1.2;
        for (int o = 0; o <= 16; o++) {
            p.spawnParticle(Particle.DUST, new Location(wo, bx + o, y, bz), 1, dust);
            p.spawnParticle(Particle.DUST, new Location(wo, bx + o, y, bz + 16), 1, dust);
            p.spawnParticle(Particle.DUST, new Location(wo, bx, y, bz + o), 1, dust);
            p.spawnParticle(Particle.DUST, new Location(wo, bx + 16, y, bz + o), 1, dust);
        }
        double h = y - 1.2;
        for (int i = 0; i < 8; i++) {
            p.spawnParticle(Particle.DUST, new Location(wo, bx, h, bz), 1, dust);
            p.spawnParticle(Particle.DUST, new Location(wo, bx + 16, h, bz), 1, dust);
            p.spawnParticle(Particle.DUST, new Location(wo, bx, h, bz + 16), 1, dust);
            p.spawnParticle(Particle.DUST, new Location(wo, bx + 16, h, bz + 16), 1, dust);
            h += 0.5;
        }
    }

    void clearMark(Session s) {
        s.cmarkW = null;
        s.cmarkC = null;
        s.cmarkUntil = Double.NaN;
    }

    void showOutline(Player p, int cx, int cz, String col) {
        Session s = m.session(p);
        Material cm = Material.valueOf(col + "_CONCRETE");
        BlockData bd = cm.createBlockData();
        World wo = p.getWorld();
        int bx = cx * 16;
        int bz = cz * 16;
        List<int[]> cols = new ArrayList<>();
        for (int o = 0; o < 16; o++) {
            cols.add(new int[]{bx + o, bz});
            cols.add(new int[]{bx + o, bz + 15});
            if (o > 0 && o < 15) {
                cols.add(new int[]{bx, bz + o});
                cols.add(new int[]{bx + 15, bz + o});
            }
        }
        for (int[] xz : cols) {
            Block hb = wo.getHighestBlockAt(xz[0], xz[1]);
            boolean ok = false;
            for (int i = 0; i < 5 && !ok; i++) {
                if (hb.getType().isOccluding()) {
                    ok = true;
                } else {
                    hb = hb.getRelative(0, -1, 0);
                }
            }
            if (ok) {
                Location bl = hb.getLocation();
                synchronized (s.cmarkFb) {
                    s.cmarkFb.add(bl);
                }
                p.sendBlockChange(bl, bd);
            }
        }
        s.cmarkFbt = ClaimModule.now() + 20;
    }

    void revertMark(Player p) {
        Session s = m.session(p);
        s.cmarkFbt = Double.NaN;
        List<Location> fb;
        synchronized (s.cmarkFb) {
            if (s.cmarkFb.isEmpty()) {
                return;
            }
            fb = new ArrayList<>(s.cmarkFb);
            s.cmarkFb.clear();
        }
        Location pl = p.getLocation();
        for (Location l : fb) {
            if (l.getWorld().equals(pl.getWorld()) && l.distance(pl) < 96) {
                p.sendBlockChange(l, l.getBlock().getBlockData());
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  HARITA
    // ------------------------------------------------------------------ //

    void map(Player p, String centerCid, String mode, String base) {
        if (!m.inClaimWorld(p)) {
            return;
        }
        boolean newMode = "new".equals(mode);
        String u = ClaimModule.uuid(p);
        if (newMode) {
            base = "";
            centerCid = "";
        } else {
            mode = "manage";
            if (base == null || base.isEmpty()) {
                base = "";
                if (!centerCid.isEmpty() && m.store.owner(centerCid) != null) {
                    base = m.mainOf(centerCid);
                }
            }
            if (!base.isEmpty() && m.store.owner(base) == null) {
                base = "";
            }
        }
        Session s = m.session(p);
        s.slotTags.clear();
        s.mapMode = mode;
        s.mapBase = base;
        Inventory inv = Gui.chest(6, newMode ? "&8Yeni Claim Kur" : "&8Bölge Satın Al (Harita)");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 53);
        int px;
        int pz;
        String w;
        if (centerCid.isEmpty()) {
            Location pl = p.getLocation();
            px = (int) Math.floor(pl.getX() / 16);
            pz = (int) Math.floor(pl.getZ() / 16);
            w = pl.getWorld().getName();
            centerCid = w + "_" + px + "_" + pz;
        } else {
            Integer zz = ClaimModule.cidZ(centerCid);
            Integer xx = ClaimModule.cidX(centerCid);
            pz = zz == null ? 0 : zz;
            px = xx == null ? 0 : xx;
            w = ClaimModule.cidWorld(centerCid);
        }
        if (!newMode && !base.isEmpty()) {
            Integer bX = ClaimModule.cidX(base);
            Integer bZ = ClaimModule.cidZ(base);
            if (bX != null && bZ != null) {
                int st = m.mapRange();
                boolean clamped = false;
                if (px > bX + st) {
                    px = bX + st;
                    clamped = true;
                }
                if (px < bX - st) {
                    px = bX - st;
                    clamped = true;
                }
                if (pz > bZ + st) {
                    pz = bZ + st;
                    clamped = true;
                }
                if (pz < bZ - st) {
                    pz = bZ - st;
                    clamped = true;
                }
                if (clamped) {
                    w = ClaimModule.cidWorld(base);
                    centerCid = w + "_" + px + "_" + pz;
                }
            }
        }
        s.mapCenter = centerCid;
        if (newMode) {
            inv.setItem(19, Gui.item(Material.PAPER, "<#f6d365>Yeni Claim", "&7Ortada bulunduğun bölge gösterilir.", "&7Yeşil ise buraya claim kurabilirsin.", "", "&8Başka yere kurmak için o bölgeye", "&8gidip menüyü tekrar aç."));
        } else {
            inv.setItem(10, Gui.item(Material.ARROW, "&eKuzey", "&7Haritayı yukarı kaydırır."));
            inv.setItem(28, Gui.item(Material.ARROW, "&eGüney", "&7Haritayı aşağı kaydırır."));
            inv.setItem(18, Gui.item(Material.ARROW, "&eBatı", "&7Haritayı sola kaydırır."));
            inv.setItem(20, Gui.item(Material.ARROW, "&eDoğu", "&7Haritayı sağa kaydırır."));
            inv.setItem(19, Gui.item(Material.COMPASS, "&bMerkeze Dön", base.isEmpty() ? "&7Kendi bulunduğunuz konuma döner." : "&7Claim merkezine döner."));
        }
        for (int i = 0; i < 6; i++) {
            inv.setItem(i * 9 + 3, Gui.item(Material.GRAY_STAINED_GLASS_PANE, "&f"));
        }
        inv.setItem(46, Gui.item(Material.BARRIER, "&7Kapat", "&8Menüyü kapatır."));
        if (s.mapMark) {
            inv.setItem(37, Gui.item(Material.LIME_DYE, "&aİşaretleme Modu: AÇIK", "&7Şimdi haritadan bir bölgeye tıkla,", "&7sınırları dünyada &e20 saniye", "&7boyunca renkli gösterilir.", "", "&7Bu mod açıkken tıklamak", "&asatın alma/silme yapmaz&7.", "", "&eTıkla &8· &7Modu Kapat"));
            inv.setItem(36, Gui.item(Material.LIME_STAINED_GLASS_PANE, "&aİşaretleme Modu Açık"));
            inv.setItem(38, Gui.item(Material.LIME_STAINED_GLASS_PANE, "&aİşaretleme Modu Açık"));
        } else {
            inv.setItem(37, Gui.item(Material.ENDER_PEARL, "&eBölgeyi Dünyada Göster", "&7Hangi chunk olduğunu anlamak için", "&7bu modu aç, sonra haritadan", "&7bir bölgeye tıkla.", "", "&aYeşil&7: alınabilir &9Mavi&7: senin", "&cKırmızı&7: başkasının &6Turuncu&7: engel", "", "&eTıkla &8· &7Modu Aç"));
        }
        Map<String, Long> pc = new HashMap<>();
        int mainCount = m.playerMainClaimCount(p);
        int maxMain = m.maxMainClaims(p);
        Map<String, String> mine = new HashMap<>();
        for (String c : m.store.all) {
            Claim cl = m.store.get(c);
            if (cl != null && u.equals(cl.owner)) {
                mine.put(c, cl.mainOrSelf());
            }
        }
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (newMode && (dx != 0 || dz != 0)) {
                    continue;
                }
                int tx = px + dx;
                int tz = pz + dz;
                String targetCid = w + "_" + tx + "_" + tz;
                Claim tc = m.store.get(targetCid);
                String owner = tc == null ? null : tc.owner;
                int slot = (dz + 2) * 9 + (dx + 6);
                if (owner != null) {
                    if (!owner.equals(u)) {
                        String cName = tc.claimName == null ? "Bilinmiyor" : tc.claimName;
                        inv.setItem(slot, Gui.item(Material.RED_WOOL, "&cBaşkasının Claimi", "&7İsim: &e" + cName, "&7Sahibi: &e" + tc.ownerName, "", "&cBu alan başkası tarafından korunuyor."));
                    } else {
                        String cName = tc.claimName;
                        if (tc.main()) {
                            inv.setItem(slot, Gui.item(Material.BLUE_WOOL, "&9Senin Ana Claimin", "&7Claim İsim: &e" + cName, "&7Koordinat: &f" + tx + ", " + tz, "", "&aBu alan sizin ana korumanızdır.", "", "&cTıkla &8· &7Bölgeyi Sil"));
                        } else {
                            String tMain = tc.mainOrSelf();
                            int nbn = 0;
                            if (tMain.equals(mine.get(w + "_" + (tx + 1) + "_" + tz))) {
                                nbn++;
                            }
                            if (tMain.equals(mine.get(w + "_" + (tx - 1) + "_" + tz))) {
                                nbn++;
                            }
                            if (tMain.equals(mine.get(w + "_" + tx + "_" + (tz + 1)))) {
                                nbn++;
                            }
                            if (tMain.equals(mine.get(w + "_" + tx + "_" + (tz - 1)))) {
                                nbn++;
                            }
                            if (nbn < 4) {
                                inv.setItem(slot, Gui.item(Material.LIGHT_BLUE_WOOL, "&bSenin Claimin (Genişletme)", "&7Claim İsim: &e" + cName, "&7Koordinat: &f" + tx + ", " + tz, "", "&aBu alan sizin korumanız altında.", "", "&cTıkla &8· &7Bölgeyi Sil"));
                            } else {
                                inv.setItem(slot, Gui.item(Material.CYAN_WOOL, "&3Senin Claimin (Genişletme)", "&7Claim İsim: &e" + cName, "&7Koordinat: &f" + tx + ", " + tz, "", "&aBu alan sizin korumanız altında.", "", "&cŞu an silinemez", "&8Ortada kalan bölge silinemez."));
                            }
                        }
                    }
                } else if (!m.chunkInBorder(targetCid)) {
                    inv.setItem(slot, Gui.item(Material.ORANGE_WOOL, "&cDünya Sınırı Dışı", "&7Tür: &eClaim Engellendi", "", "&cBu bölge dünya sınırının dışında!",
                            "&7Claim sadece &e" + m.cfg.borderMin + " &7ile &e" + m.cfg.borderMax, "&7koordinatları arasında alınabilir."));
                } else {
                    String target = "";
                    String[] adj = {w + "_" + (tx - 1) + "_" + tz, w + "_" + (tx + 1) + "_" + tz, w + "_" + tx + "_" + (tz - 1), w + "_" + tx + "_" + (tz + 1)};
                    for (String a : adj) {
                        Claim ac = m.store.get(a);
                        if (ac != null && u.equals(ac.owner)) {
                            target = ac.mainCid == null ? "<none>" : ac.mainCid;
                            break;
                        }
                    }
                    if (!target.isEmpty()) {
                        if (!m.inMapView(target, targetCid)) {
                            inv.setItem(slot, Gui.item(Material.ORANGE_WOOL, "&cClaim Sınırı", "&7Tür: &eGenişletme Engellendi", "",
                                    "&cClaim en fazla " + m.cfg.mapView + "x" + m.cfg.mapView + " alana yayılabilir.",
                                    "&7Claim merkezinden en fazla &e" + (m.mapRange() + 2) + " &7bölge uzağa", "&7genişletebilirsin."));
                        } else if (m.tooCloseToOtherClaims(targetCid, target)) {
                            inv.setItem(slot, Gui.item(Material.ORANGE_WOOL, "&cMesafe Engeli", "&7Tür: &eGenişletme Engellendi", "", "&cBaşka bir claime çok yakın!", "&7Aralarında en az 3 chunk boşluk olmalıdır."));
                        } else {
                            Long exp = pc.get(target);
                            if (exp == null) {
                                exp = m.nextExpansionPrice(p, target);
                                pc.put(target, exp);
                            }
                            inv.setItem(slot, Gui.item(Material.LIME_WOOL, "&aSatın Alınabilir Bölge", "&7Tür: &eClaim Genişletme", "&7Ücret: &a" + exp + "$", "", "&eSatın almak için tıklayın!"));
                        }
                    } else if (dx == 0 && dz == 0) {
                        if (mainCount < maxMain) {
                            if (m.tooCloseToOtherClaims(targetCid, "")) {
                                inv.setItem(slot, Gui.item(Material.ORANGE_WOOL, "&cMesafe Engeli", "&7Tür: &eYeni Claim Engellendi", "", "&cÇevrede başka bir claim var!", "&7Farklı claimler arası en az &e3 chunk &cboşluk olmalı."));
                            } else {
                                inv.setItem(slot, Gui.item(Material.LIME_WOOL, "&aYeni Ana Claim Kur", "&7Tür: &eİsimli Ana Claim", "&7Mevcut Hak: &e" + mainCount + "/" + maxMain,
                                        "&7Ücret: &a" + n(m.cfg.initialClaimPrice) + "$", "", "&eTıkla ve sohbete claim ismini gir!"));
                            }
                        } else {
                            inv.setItem(slot, Gui.item(Material.WHITE_WOOL, "&fSatın Alınamaz Bölge", "&cMaksimum " + maxMain + " ayrı claim hakkınızı doldurdunuz!",
                                    "&7Sadece mevcut claimlerinize bitişik alanları alabilirsiniz.", "",
                                    "&7Claim hakkı &8· &fNormal " + m.cfg.maxMainClaims + " &8| &aVIP " + m.cfg.maxMainClaimsVip + " &8| &bDVIP " + m.cfg.maxMainClaimsDvip + " &8| &dDVIP+ " + m.cfg.maxMainClaimsDvipPlus));
                        }
                    } else {
                        inv.setItem(slot, Gui.item(Material.WHITE_WOOL, "&fSatın Alınamaz Bölge", "&7Sebep: &cClaiminizle birleşik değil.", "&7Önce claiminize bitişik bölgeleri genişletmelisiniz."));
                    }
                }
            }
        }
        p.openInventory(inv);
        open(p, "claim_map_menu", inv);
    }

    // ------------------------------------------------------------------ //
    //  ENERJI MENUSU
    // ------------------------------------------------------------------ //

    private ItemStack energyPack(String label, double hours, double rate, double space, Material icon) {
        double amt = rate * hours;
        List<String> l = new ArrayList<>();
        l.add("&7Süre &8· &f+" + label);
        l.add("&7Enerji &8· &b+" + (long) Math.floor(amt));
        if (space < 1) {
            l.add("");
            l.add("&cEnerji deposu dolu.");
            return Gui.item(icon, "&7+" + label + " Enerji", l);
        }
        if (amt > space) {
            l.add("&7Ücret &8· &a" + ClaimModule.fmtShort(m.energyCost(space)) + "$");
            l.add("");
            l.add("&eDepo tavanı yüzünden sadece");
            l.add("&e+" + (long) Math.floor(space) + " enerji eklenir.");
        } else {
            l.add("&7Ücret &8· &a" + ClaimModule.fmtShort(m.energyCost(amt)) + "$");
        }
        l.add("");
        l.add("&8Süre, claim'in şu anki tüketimine");
        l.add("&8göre hesaplanır (chunk + yükseltme).");
        l.add("");
        l.add("&eTıkla &8· &7Satın Al");
        return Gui.item(icon, "<#a8ff78>+" + label + " Enerji", l);
    }

    void time(Player p, String cid) {
        String mc = m.mainOf(cid);
        Claim c = m.store.get(cid);
        String cName = c == null || c.claimName == null ? "Claim" : c.claimName;
        double energy = m.energyNow(mc);
        double rate = m.drainPerHour(mc);
        Claim mcl = m.store.get(mc);
        int chunks = mcl == null ? 1 : mcl.chunkCount;
        Inventory inv = Gui.chest(4, "&8Claim Enerjisi (" + cName + ")");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 35);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 27, 35);
        String timeStr = energy <= 0 ? "&cEnerji bitti!" : m.energyTimeText(mc);
        double daily = rate * 24;
        double dailyCost = m.energyCost(daily);
        String mTxt = m.upgMult(mc) > 1 ? " &8· &cx" + n(m.upgMult(mc)) + " yükseltme" : "";
        double cap = m.energyMax(mc);
        if (cap <= 0) {
            cap = 1;
        }
        long pct = Math.min(100, (long) Math.floor((energy / cap) * 100));
        String barCol = pct <= 33 ? "&c" : pct <= 66 ? "&e" : "&a";
        long filled = pct / 5;
        StringBuilder bar = new StringBuilder(barCol);
        for (int bi = 0; bi < 20; bi++) {
            if (bi == filled) {
                bar.append("&8");
            }
            bar.append('|');
        }
        inv.setItem(13, Gui.item(Material.REDSTONE, "<#00d2ff>Claim Enerjisi", "&7Enerji &8· &b" + (long) Math.floor(energy) + "&7/&b" + (long) Math.floor(cap),
                "&7Doluluk &8· " + bar + " &f" + pct + "%", "&7Kalan süre &8· &e" + timeStr, "", "&7Chunk &8· &f" + chunks,
                "&7Tüketim &8· &c" + n(rate) + "&7/saat &8(&c" + n(daily) + "&7/gün)" + mTxt, "&7Günlük maliyet &8· &a" + n(dailyCost) + "$", "",
                "&7Depo tavanı &8· &f" + n(m.cfg.energyMaxDays) + " günlük", "&8&oChunk aldıkça tüketim de tavan da artar."));
        double space = Math.max(0, cap - energy);
        inv.setItem(19, energyPack("1 Saat", 1, rate, space, Material.LIME_DYE));
        inv.setItem(21, energyPack("1 Gün", 24, rate, space, Material.GREEN_DYE));
        inv.setItem(23, energyPack("7 Gün", 168, rate, space, Material.EMERALD));
        double fill = Math.max(0, cap - energy);
        double cFull = m.energyCost(fill);
        if (fill < 1) {
            inv.setItem(25, Gui.item(Material.NETHER_STAR, "<#ff9a9e>Depo Dolu", "&7Enerji deposu maksimumda."));
        } else {
            inv.setItem(25, Gui.item(Material.NETHER_STAR, "<#f6d365>Depoyu Doldur &8(+" + (long) Math.floor(fill) + ")",
                    "&7Enerjiyi tavana (&f" + n(m.cfg.energyMaxDays) + " gün&7) tamamlar.", "&7Ücret: &a" + n(cFull) + "$", "", "&eTıkla &8· &7Doldur"));
        }
        inv.setItem(31, Gui.item(Material.ARROW, "&7Geri", "&7Yönetim menüsüne döner."));
        m.session(p).cid = cid;
        p.openInventory(inv);
        open(p, "claim_time_menu", inv);
    }

    // ------------------------------------------------------------------ //
    //  BOLGE GIRIS ACTIONBAR / PLACEHOLDER
    // ------------------------------------------------------------------ //

    boolean noticeVisible(String u, String mc) {
        Claim c = m.store.get(mc);
        if (c == null || c.owner == null) {
            return false;
        }
        return u.equals(c.owner) || c.members.contains(u);
    }

    final java.util.concurrent.ConcurrentHashMap<String, Double> abarSnd = new java.util.concurrent.ConcurrentHashMap<>();

    void enterNotice(Player p, String cid) {
        String u = ClaimModule.uuid(p);
        Session s = m.session(p);
        Claim c = m.store.get(cid);
        String mc = "wild";
        if (c != null && c.owner != null) {
            String cm = c.mainOrSelf();
            if (noticeVisible(u, cm)) {
                mc = cm;
            }
        }
        String prev = s.abarLast;
        if (prev != null && !prev.equals("wild") && !noticeVisible(u, prev)) {
            prev = "wild";
        }
        s.abarLast = mc;
        if (prev == null || prev.equals(mc)) {
            return;
        }
        MiniMessage mm = MiniMessage.miniMessage();
        String txt;
        int dur;
        if (mc.equals("wild")) {
            txt = "<#9ca3af>Sahipsiz bölgeye çıktın";
            dur = 2;
        } else {
            Claim mcl = m.store.get(mc);
            String name = mcl.claimName == null ? "Claim" : mcl.claimName;
            name = mm.escapeTags(ChatColor.stripColor(Gui.legacy(name)));
            if (u.equals(mcl.owner)) {
                txt = "<#a8ff78>" + name + " <#6b7280>· <#d1d5db>kendi bölgene giriş yaptın";
            } else {
                txt = "<#7dd3fc>" + name + " <#6b7280>· <#d1d5db>üyesi olduğun bölgeye giriş yaptın";
            }
            dur = 3;
            double nowS = ClaimModule.now();
            double lastS = abarSnd.getOrDefault(u, 0.0);
            if (nowS - lastS >= 5) {
                abarSnd.put(u, nowS);
                ClaimModule.sound(p, "block.amethyst_block.chime", 0.6f, 1.4f);
            }
        }
        Component comp = mm.deserialize(txt);
        p.sendActionBar(comp);
        s.abar = txt;
        s.abarUntil = ClaimModule.now() + dur;
    }

    void updatePlaceholder(Player p, String cid) {
        String u = ClaimModule.uuid(p);
        Claim c = m.store.get(cid);
        if (c == null || c.owner == null) {
            m.plc("name", u, "-");
            m.plc("owner", u, "-");
            m.plc("status", u, "&7Boş Alan");
            m.plc("chunks", u, "-");
            m.plc("energy", u, "-");
            m.plc("left", u, "-");
            return;
        }
        String mc = c.mainOrSelf();
        Claim mcl = m.store.get(mc);
        m.plc("name", u, mcl == null || mcl.claimName == null ? "-" : mcl.claimName);
        m.plc("owner", u, mcl == null || mcl.ownerName == null ? "-" : mcl.ownerName);
        if (u.equals(c.owner)) {
            m.plc("status", u, "&aKendi Claim'in");
        } else if (m.isMemberUuid(mc, u)) {
            m.plc("status", u, "&bÜyesisin");
        } else {
            m.plc("status", u, "&cYabancı Claim");
        }
        m.plc("chunks", u, Integer.toString(mcl == null ? 1 : mcl.chunkCount));
        m.plc("energy", u, Long.toString((long) Math.floor(m.energyNow(mc))));
        m.plc("left", u, m.energyTimeText(mc));
    }

    static String stripTags(String s) {
        return ChatColor.stripColor(Gui.legacy(s));
    }

    static void unused(Bukkit b) {
    }
}
