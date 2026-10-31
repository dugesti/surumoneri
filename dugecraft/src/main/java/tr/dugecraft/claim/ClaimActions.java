package tr.dugecraft.claim;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import tr.dugecraft.util.Econ;
import tr.dugecraft.util.Gui;
import tr.dugecraft.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** claim.sk 3102-4309: bolge silme/satin alma, AFK satis, uye/devir/davet/yetki, isinlanma, enerji. */
@SuppressWarnings("deprecation")
final class ClaimActions {

    static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final ClaimModule m;

    ClaimActions(ClaimModule m) {
        this.m = m;
    }

    private void open(Player p, String menu, Inventory inv) {
        p.openInventory(inv);
        Session s = m.session(p);
        s.menu = menu;
        s.inv = inv;
    }

    static String offName(String uuid) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            String n = op.getName();
            return n == null ? uuid : n;
        } catch (RuntimeException e) {
            return uuid;
        }
    }

    static void title(Player p, String t, String sub, int seconds) {
        p.sendTitle(Gui.legacy(t), Gui.legacy(sub), 10, seconds * 20, 20);
    }

    // ------------------------------------------------------------------ //
    //  BOLGE SILME
    // ------------------------------------------------------------------ //

    String chunkDeleteBlockReason(String mainCid, String targetCid) {
        String w = ClaimModule.cidWorld(targetCid);
        Integer tx = ClaimModule.cidX(targetCid);
        Integer tz = ClaimModule.cidZ(targetCid);
        Set<String> own = new HashSet<>();
        for (String c : m.store.all) {
            Claim cl = m.store.get(c);
            if (cl != null && cl.owner != null && cl.mainOrSelf().equals(mainCid)) {
                own.add(c);
            }
        }
        if (tx != null && tz != null) {
            int nb = 0;
            if (own.contains(w + "_" + (tx + 1) + "_" + tz)) {
                nb++;
            }
            if (own.contains(w + "_" + (tx - 1) + "_" + tz)) {
                nb++;
            }
            if (own.contains(w + "_" + tx + "_" + (tz + 1))) {
                nb++;
            }
            if (own.contains(w + "_" + tx + "_" + (tz - 1))) {
                nb++;
            }
            if (nb >= 4) {
                return "Bu bölge claim'in ortasında kalıyor. Dıştan başlayarak silmelisin.";
            }
        }
        own.remove(targetCid);
        int total = own.size();
        if (total <= 0 || !own.contains(mainCid)) {
            return "";
        }
        Set<String> seen = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(mainCid);
        seen.add(mainCid);
        int i = 0;
        int found = 0;
        while (i < queue.size()) {
            String cur = queue.get(i++);
            found++;
            String cw = ClaimModule.cidWorld(cur);
            Integer cx = ClaimModule.cidX(cur);
            Integer cz = ClaimModule.cidZ(cur);
            if (cx == null || cz == null) {
                continue;
            }
            for (String nc : new String[]{cw + "_" + (cx + 1) + "_" + cz, cw + "_" + (cx - 1) + "_" + cz, cw + "_" + cx + "_" + (cz + 1), cw + "_" + cx + "_" + (cz - 1)}) {
                if (own.contains(nc) && seen.add(nc)) {
                    queue.add(nc);
                }
            }
        }
        if (found < total) {
            return "Bu bölgeyi silersen claim ikiye bölünür. Dıştan başlayarak silmelisin.";
        }
        return "";
    }

    void chunkDeleteMenu(Player p, String cid) {
        Claim c = m.store.get(cid);
        String owner = c == null ? null : c.owner;
        if (!ClaimModule.uuid(p).equals(owner) && !ClaimModule.isKurucu(p)) {
            m.pre(p, "<#ff4b1f>Bu bölge size ait değil!");
            return;
        }
        boolean isMain = c != null && c.main();
        String cName = c == null || c.claimName == null ? "Bilinmiyor" : c.claimName;
        Integer cz = ClaimModule.cidZ(cid);
        Integer cx = ClaimModule.cidX(cid);
        Inventory inv = Gui.chest(3, "&4Bölge Silme Onayı");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 26);
        Session s = m.session(p);
        String coord = "&7Koordinat: &f" + cx + ", " + cz;
        if (isMain) {
            int subs = m.countSubChunks(cid);
            if (subs > 0) {
                inv.setItem(13, Gui.item(Material.BARRIER, "&cAna Bölge Silinemez", "&7Claim: &e" + cName, coord, "", "&cBu ana bölgeye bağlı &e" + subs + " &cgenişletme var.", "&7Önce tüm genişletme bölgelerini silmelisin.", "", "&8Ana bölge en son silinir."));
                inv.setItem(22, Gui.item(Material.ARROW, "&7Geri", "&8Haritaya döner."));
                open(p, "chunk_delete", inv);
                s.target = cid;
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                return;
            }
            inv.setItem(13, Gui.item(Material.BLUE_WOOL, "&9Ana Bölge", "&7Claim: &e" + cName, coord, "", "&cDİKKAT: &7Bu son bölge. Silersen", "&7claim tamamen yok olur:", "&7üyeler, çiftçi deposu ve enerji", "&7kalıcı olarak silinir.", "", "&cBu işlem geri alınamaz!"));
        } else {
            String mc = c == null ? cid : c.mainOrSelf();
            String block = chunkDeleteBlockReason(mc, cid);
            if (!block.isEmpty()) {
                inv.setItem(13, Gui.item(Material.BARRIER, "&cBu Bölge Silinemez", "&7Claim: &e" + cName, coord, "", "&c" + block, "", "&8Claim tek parça kalmalı ve", "&8ortasında boşluk olmamalı."));
                inv.setItem(22, Gui.item(Material.ARROW, "&7Geri", "&8Haritaya döner."));
                open(p, "chunk_delete", inv);
                s.target = cid;
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                return;
            }
            inv.setItem(13, Gui.item(Material.LIGHT_BLUE_WOOL, "&bGenişletme Bölgesi", "&7Claim: &e" + cName, coord, "", "&7Sadece bu chunk claim'den çıkar.", "&7Claim'in geri kalanı etkilenmez.", "", "&7Chunk azalınca enerji tüketimi", "&7de düşer.", "", "&cÜcret iadesi yapılmaz."));
        }
        inv.setItem(11, Gui.item(Material.LIME_DYE, "&aSİL (Onayla)", "&7Bölgeyi kalıcı olarak siler."));
        inv.setItem(15, Gui.item(Material.RED_STAINED_GLASS_PANE, "&cİptal Et", "&7Haritaya geri döner."));
        open(p, "chunk_delete", inv);
        s.target = cid;
    }

    void deleteChunkArea(Player p, String cid) {
        Claim c = m.store.get(cid);
        String owner = c == null ? null : c.owner;
        if (!ClaimModule.uuid(p).equals(owner) && !ClaimModule.isKurucu(p)) {
            m.pre(p, "<#ff4b1f>Bu bölge artık size ait değil!");
            return;
        }
        if (owner == null) {
            return;
        }
        String cName = c.claimName == null ? "Bilinmiyor" : c.claimName;
        if (c.main()) {
            if (m.countSubChunks(cid) > 0) {
                m.pre(p, "<#ff4b1f>Önce genişletme bölgelerini silmelisin!");
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                return;
            }
            m.alog("CLAIM", "SILINDI " + p.getName() + "(" + p.getUniqueId() + ") claim=" + cid + " isim=" + cName + " (harita uzerinden)");
            broadcastExplosion("silindi", cName, cid);
            m.deleteClaim(cid);
            ClaimModule.sound(p, "entity.item.break", 1, 1);
            m.pre(p, "<#ff0844>Claim tamamen silindi.");
            return;
        }
        String mainCid = c.mainOrSelf();
        String block = chunkDeleteBlockReason(mainCid, cid);
        if (!block.isEmpty()) {
            m.pre(p, "<#ff4b1f>" + block);
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        m.syncEnergy(mainCid);
        m.clog(mainCid, "&e" + p.getName() + " &cbir bölgeyi claim'den çıkardı &8(chunk " + cid + ")");
        m.alog("CLAIM", "BOLGE_SILINDI " + p.getName() + "(" + p.getUniqueId() + ") claim=" + mainCid + " chunk=" + cid);
        m.deleteClaim(cid);
        Claim mc = m.store.get(mainCid);
        if (mc != null) {
            mc.chunkCount = Math.max(1, mc.chunkCount - 1);
            m.syncEnergy(mainCid);
            m.save(mainCid);
        }
        ClaimModule.sound(p, "block.stone.break", 1, 0.8f);
        m.pre(p, "&7Bölge claim'den çıkarıldı. &8(Ücret iadesi yoktur)");
    }

    // ------------------------------------------------------------------ //
    //  BOLGE SATIN ALMA
    // ------------------------------------------------------------------ //

    String buyMain(Player p, String targetCid) {
        String w = ClaimModule.cidWorld(targetCid);
        Integer tx = ClaimModule.cidX(targetCid);
        Integer tz = ClaimModule.cidZ(targetCid);
        if (tx == null || tz == null) {
            return "";
        }
        String u = ClaimModule.uuid(p);
        for (String a : new String[]{w + "_" + (tx - 1) + "_" + tz, w + "_" + (tx + 1) + "_" + tz, w + "_" + tx + "_" + (tz - 1), w + "_" + tx + "_" + (tz + 1)}) {
            Claim c = m.store.get(a);
            if (c != null && u.equals(c.owner)) {
                return c.mainOrSelf();
            }
        }
        return "";
    }

    double buyPrice(Player p, String mainT) {
        if (mainT.isEmpty()) {
            return m.cfg.initialClaimPrice;
        }
        return m.nextExpansionPrice(p, mainT);
    }

    String buyBlock(Player p, String targetCid, String mainT, double price) {
        String pf = m.cfg.prefix;
        if (!ClaimModule.cidWorld(targetCid).equals(m.cfg.claimWorld)) {
            return "close|" + pf + " <#ff4b1f>Bunu bu dünyada yapamazsın! &7Claim sadece &eAna Dünya&7'da kurulabilir.";
        }
        if (!p.getWorld().getName().equals(m.cfg.claimWorld)) {
            return "close|" + pf + " <#ff4b1f>Bunu bu dünyada yapamazsın! &7Claim işlemleri sadece &eAna Dünya&7'da yapılabilir.";
        }
        if (!m.chunkInBorder(targetCid)) {
            return "close|" + pf + " <#ff4b1f>Bu bölge dünya sınırının dışında! &7Claim sadece &e" + m.cfg.borderMin + " &7ile &e" + m.cfg.borderMax + " &7koordinatları arasında alınabilir.";
        }
        if (!mainT.isEmpty() && !m.inMapView(mainT, targetCid)) {
            return "keep|" + pf + " <#ff4b1f>Claim en fazla " + m.cfg.mapView + "x" + m.cfg.mapView + " alana yayılabilir! &7Bu bölge claim merkezinden çok uzak.";
        }
        if (Econ.balance(p) < price) {
            return "keep|" + pf + " <#ff4b1f>Bu işlem için <#f6d365>" + Text.num(price) + "$ <#ff4b1f>bakiyeye ihtiyacınız var.";
        }
        if (m.tooCloseToOtherClaims(targetCid, mainT)) {
            return "close|" + pf + " <#ff4b1f>Bu alan başka bir claime çok yakın! Arada en az 3 chunk boşluk bulunmalı.";
        }
        if (mainT.isEmpty()) {
            if (!ClaimModule.chunkId(p.getLocation()).equals(targetCid)) {
                return "keep|" + pf + " <#ff4b1f>Yeni claim kurmak için o bölgenin içinde durmalısın.";
            }
            int mx = m.maxMainClaims(p);
            if (m.playerMainClaimCount(p) >= mx) {
                return "keep|" + pf + " <#ff4b1f>Maksimum " + mx + " ayrı claim sınırına ulaştınız! &7(Normal " + m.cfg.maxMainClaims + " &8· &aVIP "
                        + m.cfg.maxMainClaimsVip + " &8· &bDVIP " + m.cfg.maxMainClaimsDvip + " &8· &dDVIP+ " + m.cfg.maxMainClaimsDvipPlus + "&7)";
            }
        }
        return "";
    }

    void buyFail(Player p, String reason, String src) {
        int bar = reason.indexOf('|');
        String mode = bar < 0 ? reason : reason.substring(0, bar);
        String msg = bar < 0 ? "" : reason.substring(bar + 1);
        if (mode.equals("close") || src.equals("cmd")) {
            p.closeInventory();
        }
        if (!msg.isEmpty()) {
            m.send(p, msg);
        }
        ClaimModule.sound(p, "entity.villager.no", 1, 1);
    }

    void buyChunk(Player p, String targetCid, String src) {
        if (m.store.owner(targetCid) != null) {
            if (src.equals("cmd")) {
                p.closeInventory();
            }
            m.pre(p, "<#ff4b1f>Bu alan siz tıklayana kadar başka birisi tarafından claimlendi!");
            return;
        }
        String mainT = buyMain(p, targetCid);
        double price = buyPrice(p, mainT);
        String reason = buyBlock(p, targetCid, mainT, price);
        if (!reason.isEmpty()) {
            buyFail(p, reason, src);
            return;
        }
        String u = ClaimModule.uuid(p);
        if (mainT.isEmpty()) {
            p.closeInventory();
            Session s = m.session(p);
            s.chatMode = "naming";
            s.chatCid = targetCid;
            s.chatPrice = price;
            s.chatAt = ClaimModule.now();
            ClaimModule.sound(p, "entity.experience_orb.pickup", 1, 1);
            title(p, "<#a8ff78>CLAIM İSMİ GİRİN", "&7Sohbete claiminizin adını yazın!", 5);
            m.pre(p, "&eLütfen yeni claiminiz için bir isim yazın. &7(İptal etmek için &c'iptal' &7yazın)");
            return;
        }
        if (!m.reserve(targetCid, u)) {
            m.pre(p, "<#ff4b1f>Bu bölge şu an başka biri tarafından alınıyor.");
            return;
        }
        if (m.store.owner(targetCid) != null) {
            m.release(targetCid);
            m.pre(p, "<#ff4b1f>Bu bölge az önce başkası tarafından alındı!");
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        m.syncEnergy(mainT);
        if (!Econ.withdraw(p, price)) {
            m.release(targetCid);
            m.pre(p, "<#ff4b1f>Bu işlem için <#f6d365>" + Text.num(price) + "$ <#ff4b1f>bakiyeye ihtiyacınız var.");
            return;
        }
        Claim mc = m.store.get(mainT);
        Claim t = new Claim(targetCid);
        t.owner = u;
        t.ownerName = p.getName();
        t.claimName = mc == null ? null : mc.claimName;
        t.expiry = ClaimModule.now() + 30 * 86400;
        t.isMain = false;
        t.mainCid = mainT;
        m.store.claims.put(targetCid, t);
        if (mc != null) {
            mc.chunkCount = mc.chunkCount + 1;
        }
        m.syncEnergy(mainT);
        m.markProtChunk(targetCid, mainT);
        if (src.equals("cmd")) {
            m.clog(mainT, "&e" + p.getName() + " &ayeni bölge satın aldı &8(komut, chunk " + targetCid + ")");
            m.alog("CLAIM", "GENISLETME " + p.getName() + "(" + u + ") claim=" + mainT + " chunk=" + targetCid + " ucret=" + Text.num(price) + " (komut)");
        } else {
            m.clog(mainT, "&e" + p.getName() + " &ayeni bölge satın aldı &8(chunk " + targetCid + ")");
            m.alog("CLAIM", "GENISLETME " + p.getName() + "(" + u + ") claim=" + mainT + " chunk=" + targetCid + " ucret=" + Text.num(price));
        }
        m.save(mainT);
        m.save(targetCid);
        m.release(targetCid);
        ClaimModule.sound(p, "ui.toast.challenge_complete", 1, 1);
        ClaimModule.sound(p, "entity.player.levelup", 1, 1.2f);
        Location eff = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, eff, 30);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, eff, 25);
        title(p, "<#a8ff78>BÖLGE GENİŞLETİLDİ!", "&7Bölge başarıyla ana claiminize bağlandı.", 3);
        m.pre(p, "<#a8ff78>Tebrikler! Seçtiğiniz chunk claiminize eklendi. <#f6d365>(Ödenen: " + Text.num(price) + "$)");
        if (src.equals("cmd")) {
            return;
        }
        Session s = m.session(p);
        String center = s.mapCenter == null ? "" : s.mapCenter;
        if (src.equals("new")) {
            m.menus.map(p, center, "new", "");
        } else {
            m.menus.map(p, center, "manage", mainT);
        }
    }

    // ------------------------------------------------------------------ //
    //  SATIS AFK KORUMASI (oyuncu basina, sadece bellek)
    // ------------------------------------------------------------------ //

    static final class Sac {
        volatile Long lock;
        volatile boolean need;
        volatile Long last;
        volatile Long sess;
        volatile int cnt;
        final List<Long> iv = new ArrayList<>();
        volatile int fail;
        volatile int slot = -1;
        volatile String cid;
        volatile String k;
    }

    final ConcurrentHashMap<String, Sac> sac = new ConcurrentHashMap<>();

    Sac sac(Player p) {
        return sac.computeIfAbsent(ClaimModule.uuid(p), u -> new Sac());
    }

    /** true -> satis yapilabilir. false -> satis YAPILMAZ. */
    boolean sellAfkCheck(Player p, String cid, String k) {
        Sac s = sac(p);
        long now = System.currentTimeMillis();
        if (s.lock != null) {
            if (now < s.lock) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&cÇiftçi satışın kilitli. &7Kalan: &e" + m.fmtDur((s.lock - now) / 1000.0));
                return false;
            }
            s.lock = null;
            s.fail = 0;
        }
        if (s.need) {
            sellAfkMenu(p, cid, k);
            return false;
        }
        long gap = -1;
        if (s.last != null) {
            gap = now - s.last;
            if (gap < m.cfg.sellMinMs && gap >= 0) {
                return false;
            }
        }
        if (s.sess == null || gap < 0 || gap > m.cfg.sellSessionGapMin * 60000) {
            s.sess = now;
        }
        synchronized (s.iv) {
            if (gap > 3600000) {
                s.iv.clear();
            } else if (gap > 0) {
                if (s.iv.size() >= 6) {
                    s.iv.remove(0);
                }
                s.iv.add(gap);
            }
        }
        s.last = now;
        s.cnt++;
        String why = "";
        synchronized (s.iv) {
            if (s.iv.size() >= 6) {
                double sum = 0;
                for (long v : s.iv) {
                    sum += v;
                }
                double mean = sum / 6;
                if (mean >= m.cfg.sellRhythmMinMs) {
                    double var = 0;
                    for (long v : s.iv) {
                        double d = v - mean;
                        var += d * d;
                    }
                    double sd = Math.sqrt(var / 6);
                    if (sd / mean < m.cfg.sellRhythmCv) {
                        why = "ritim ort=" + (long) Math.floor(mean) + "ms sapma=" + (long) Math.floor(sd) + "ms";
                    }
                }
            }
        }
        if (why.isEmpty() && s.cnt >= m.cfg.sellCheckCount) {
            why = "sayi=" + s.cnt;
        }
        if (why.isEmpty() && now - s.sess >= m.cfg.sellCheckMinutes * 60000) {
            why = "sure=" + (long) Math.floor((now - s.sess) / 60000.0) + "dk";
        }
        if (why.isEmpty()) {
            return true;
        }
        s.need = true;
        m.alog("AFK", "DOGRULAMA_ISTENDI " + p.getName() + "(" + p.getUniqueId() + ") sebep=" + why + " claim=" + cid + " urun=" + k);
        sellAfkMenu(p, cid, k);
        return false;
    }

    void sellAfkMenu(Player p, String cid, String k) {
        Inventory inv = Gui.chest(4, "&8AFK Kontrolü");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 35);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 27, 35);
        Material[] w = {Material.LIME_WOOL, Material.RED_WOOL, Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.ORANGE_WOOL, Material.PURPLE_WOOL};
        String[] wn = {"&aYEŞİL", "&cKIRMIZI", "&bMAVİ", "&eSARI", "&6TURUNCU", "&5MOR"};
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int t = r.nextInt(1, 7);
        Set<Integer> used = new HashSet<>();
        Sac sc = sac(p);
        for (int i = 1; i <= 6; i++) {
            int s = -1;
            for (int tr = 0; tr < 40; tr++) {
                int rr = r.nextInt(9, 27);
                if (!used.contains(rr)) {
                    s = rr;
                    break;
                }
            }
            if (s < 0) {
                for (int rr = 9; rr <= 26; rr++) {
                    if (!used.contains(rr)) {
                        s = rr;
                        break;
                    }
                }
            }
            used.add(s);
            inv.setItem(s, Gui.item(w[i - 1], wn[i - 1]));
            if (i == t) {
                sc.slot = s;
            }
        }
        int left = m.cfg.sellAfkFails - sc.fail;
        inv.setItem(4, Gui.item(Material.PAPER, "<#ff9a3c>AFK Kontrolü", "&7Satışa devam etmek için", "&7aşağıdaki " + wn[t - 1] + " &7yüne tıkla.", "",
                "&7Kalan hak &8· &e" + left, "&8Hakların biterse satış " + Text.num(m.cfg.sellAfkLockMin) + " dk kilitlenir."));
        inv.setItem(31, Gui.item(Material.ARROW, "&7Geri", "&8Ürün menüsüne döner.", "&8Doğrulamadan satış yapılamaz."));
        sc.cid = cid;
        sc.k = k;
        open(p, "sell_afk_menu", inv);
        ClaimModule.sound(p, "block.note_block.pling", 1, 0.8f);
    }

    void sellAfkBack(Player p, String cid, String k) {
        if (cid == null || k == null) {
            p.closeInventory();
            return;
        }
        String mc = m.mainOf(cid);
        Claim c = m.store.get(mc);
        if (!m.canUseFarmer(p, mc) || c == null || !c.farmerOn()) {
            p.closeInventory();
            return;
        }
        m.menus.farmerItem(p, cid, k);
    }

    void sellAfkCleanup() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Sac> e : new ArrayList<>(sac.entrySet())) {
            Sac s = e.getValue();
            if (s.last == null || now - s.last <= 7200000) {
                continue;
            }
            boolean keep = s.need || (s.lock != null && now < s.lock);
            if (!keep) {
                sac.remove(e.getKey());
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  /claim al  /claim sil
    // ------------------------------------------------------------------ //

    void chunkBuyMenu(Player p) {
        if (!m.inClaimWorld(p)) {
            return;
        }
        String cid = ClaimModule.chunkId(p.getLocation());
        String owner = m.store.owner(cid);
        if (owner != null) {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            if (owner.equals(ClaimModule.uuid(p))) {
                m.pre(p, "&7Bu bölge zaten senin claimin. &8(Silmek için &f/claim sil&8)");
            } else {
                m.pre(p, "<#ff4b1f>Bu bölge başka bir oyuncuya ait!");
            }
            return;
        }
        String mainT = buyMain(p, cid);
        double price = buyPrice(p, mainT);
        String reason = buyBlock(p, cid, mainT, price);
        if (!reason.isEmpty()) {
            buyFail(p, reason, "cmd");
            return;
        }
        Integer cx = ClaimModule.cidX(cid);
        Integer cz = ClaimModule.cidZ(cid);
        long bal = Math.round(Econ.balance(p));
        Inventory inv = Gui.chest(3, "&8Bölge Satın Al");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 26);
        String pr = Text.num(price);
        if (mainT.isEmpty()) {
            inv.setItem(13, Gui.item(Material.LIME_WOOL, "&aYeni Ana Claim Kur", "&7Tür: &eİsimli Ana Claim", "&7Koordinat: &f" + cx + ", " + cz,
                    "&7Mevcut Hak: &e" + m.playerMainClaimCount(p) + "/" + m.maxMainClaims(p), "", "&7Ücret: &a" + pr + "$", "&7Bakiyen: &f" + bal + "$", "",
                    "&8Onaylayınca sohbete claim", "&8ismini yazman istenir."));
        } else {
            Claim mc = m.store.get(mainT);
            String mName = mc == null || mc.claimName == null ? "Claim" : mc.claimName;
            inv.setItem(13, Gui.item(Material.LIME_WOOL, "&aClaim Genişletme", "&7Claim: &e" + mName, "&7Koordinat: &f" + cx + ", " + cz, "",
                    "&7Ücret: &a" + pr + "$", "&7Bakiyen: &f" + bal + "$", "", "&8Bu chunk claiminize bağlanır."));
        }
        inv.setItem(11, Gui.item(Material.LIME_DYE, "&aSATIN AL (Onayla)", "&7Ücret: &a" + pr + "$", "", "&eTıkla &8· &7Satın Al"));
        inv.setItem(15, Gui.item(Material.RED_STAINED_GLASS_PANE, "&cİptal", "&7Menüyü kapatır."));
        open(p, "chunk_buy", inv);
        Session s = m.session(p);
        s.target = cid;
        s.chatPrice = price;
    }

    void confirmChunkBuy(Player p) {
        Session s = m.session(p);
        String cid = s.target;
        double shown = s.chatPrice;
        p.closeInventory();
        if (cid == null) {
            return;
        }
        if (!ClaimModule.chunkId(p.getLocation()).equals(cid)) {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            m.pre(p, "<#ff4b1f>Bölgeden çıktığın için işlem iptal edildi. &7Tekrar &f/claim al &7yaz.");
            return;
        }
        if (m.store.owner(cid) == null) {
            double now = buyPrice(p, buyMain(p, cid));
            if (now != shown) {
                ClaimModule.sound(p, "entity.villager.no", 1, 1);
                m.pre(p, "&eFiyat değişti &7(" + Text.num(shown) + "$ → &f" + Text.num(now) + "$&7). Menü yenilendi, tekrar onayla.");
                chunkBuyMenu(p);
                return;
            }
        }
        buyChunk(p, cid, "cmd");
    }

    void chunkDeleteCmd(Player p) {
        if (!m.inClaimWorld(p)) {
            return;
        }
        String cid = ClaimModule.chunkId(p.getLocation());
        String owner = m.store.owner(cid);
        if (owner == null) {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            m.pre(p, "<#ff4b1f>Bulunduğun bölge claimli değil.");
            return;
        }
        if (!owner.equals(ClaimModule.uuid(p))) {
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            m.pre(p, "<#ff4b1f>Bu bölge size ait değil!");
            return;
        }
        chunkDeleteMenu(p, cid);
        m.session(p).chunkDeleteSrc = "cmd";
    }

    // ------------------------------------------------------------------ //
    //  ONAY MENULERI (5 sn geri sayim)
    // ------------------------------------------------------------------ //

    private void countdown(Player p, Inventory inv, String done, String doneLore1, String doneLore2, String doneLore3, String waitWhat) {
        AtomicInteger time = new AtomicInteger(5);
        p.getScheduler().runAtFixedRate(m.plugin, (ScheduledTask task) -> {
            Session s = m.session(p);
            if (s.inv != inv || p.getOpenInventory().getTopInventory() != inv) {
                task.cancel();
                return;
            }
            int t = time.get();
            if (t > 0) {
                inv.setItem(11, Gui.item(Material.GRAY_DYE, "&7Onayla (" + t + ")", "&c" + waitWhat.replace("%t%", Integer.toString(t))));
                time.decrementAndGet();
            } else {
                inv.setItem(11, Gui.item(Material.LIME_DYE, done, doneLore1, doneLore2, "", doneLore3));
                s.timerDone = true;
                task.cancel();
            }
        }, null, 1L, 20L);
    }

    void claimDeleteConfirm(Player p, String cid) {
        Inventory inv = Gui.chest(3, "&4Claim Silme Onayı");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 26);
        Claim c = m.store.get(cid);
        inv.setItem(13, Gui.item(Material.TNT, "<#ff6b6b>" + (c == null ? "<none>" : c.claimName), "&7Claim ve tüm bölgeleri silinecek.", "&7Bu işlem geri alınamaz."));
        inv.setItem(15, Gui.item(Material.RED_STAINED_GLASS_PANE, "&cİptal Et", "&7Silme işlemini iptal et", "&7ve geri dön."));
        inv.setItem(11, Gui.item(Material.GRAY_DYE, "&7Onayla (5)", "&cSilmek için 5 saniye bekle."));
        open(p, "claim_delete_confirm", inv);
        Session s = m.session(p);
        s.cid = cid;
        s.timerDone = false;
        countdown(p, inv, "&aSİL (Onayla)", "&cBu işlem geri alınamaz!", "&7Claimi ve tüm parçalarını siler.", "&aTıkla ve kalıcı olarak sil.", "Silmek için %t% saniye bekle.");
    }

    // ------------------------------------------------------------------ //
    //  UYE YONETIMI / DEVIR
    // ------------------------------------------------------------------ //

    void memberMenu(Player p, String cid) {
        Inventory inv = Gui.chest(6, "&8Üye Yönetimi");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 53);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 45, 53);
        Session s = m.session(p);
        s.slotTags.clear();
        Claim c = m.store.get(cid);
        int memCount = c == null ? 0 : c.members.size();
        int memMax = m.memberLimit(p);
        inv.setItem(4, Gui.item(Material.BOOK, "<#ff9a9e>Üyeler", "&7Üye sayısı &8· &f" + memCount + "&7/&f" + memMax, "", "&8Bir üyeye tıklayarak yetkilerini", "&8düzenleyebilirsin."));
        int gi = 0;
        if (c != null) {
            for (String uuid : c.members) {
                if (gi >= 21) {
                    break;
                }
                int slot = GRID[gi];
                inv.setItem(slot, Gui.item(Material.PLAYER_HEAD, "<#7dd3fc>" + offName(uuid), "&eTıkla &8· &7Yetkileri düzenle"));
                s.slotTags.put(slot, uuid);
                gi++;
            }
        }
        inv.setItem(47, Gui.item(Material.LIME_DYE, "<#a8ff78>Üye Davet Et", "&7Üye: &e" + memCount + "&7/&6" + memMax, "", "&7Tıkladıktan sonra sohbete",
                "&7eklemek istediğiniz oyuncunun adını yazın.", "", "&7Limit: &fNormal 6 &8| &aVIP 9 &8| &bDVIP 13 &8| &dDVIP+ 20"));
        inv.setItem(51, Gui.item(Material.NAME_TAG, "<#f6d365>Claim'i Devret", "&7Claim'i ekli bir üyeye devreder.", "&7Sen üye olarak kalırsın.", "",
                "&cDevralan kişinin claim sınırı", "&cdolu olmamalıdır.", "", "&eTıkla &8· &7Üye Seç"));
        inv.setItem(49, Gui.item(Material.ARROW, "&7Geri", "&8Claim menüsüne döner."));
        s.cid = cid;
        open(p, "claim_members", inv);
    }

    void transferSelect(Player p, String cid) {
        Inventory inv = Gui.chest(6, "&8Claim Devret: Üye Seç");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 53);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 45, 53);
        inv.setItem(4, Gui.item(Material.NAME_TAG, "<#f6d365>Claim'i Devret", "&7Claim'i devredeceğin üyeyi seç."));
        Session s = m.session(p);
        s.slotTags.clear();
        Claim c = m.store.get(cid);
        int gi = 0;
        if (c != null) {
            for (String uuid : c.members) {
                if (gi >= 21) {
                    break;
                }
                int slot = GRID[gi];
                inv.setItem(slot, Gui.item(Material.PLAYER_HEAD, "<#f6d365>" + offName(uuid), "&7Claim'i bu oyuncuya devret.", "", "&eTıkla &8· &7Onay Ekranı"));
                s.slotTags.put(slot, uuid);
                gi++;
            }
        }
        if (gi == 0) {
            inv.setItem(22, Gui.item(Material.BARRIER, "&cEkli üye yok", "&7Devretmek için önce üye ekleyin."));
        }
        inv.setItem(49, Gui.item(Material.ARROW, "&7Geri", "&8Üye menüsüne döner."));
        s.cid = cid;
        open(p, "claim_transfer_select", inv);
    }

    void transferConfirm(Player p, String cid, String target) {
        Inventory inv = Gui.chest(3, "&6Claim Devretme Onayı");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 26);
        String tName = offName(target);
        Claim c = m.store.get(cid);
        String cName = c == null || c.claimName == null ? "Claim" : c.claimName;
        inv.setItem(13, Gui.item(Material.PLAYER_HEAD, "<#f6d365>" + tName, "&7Claim: &e" + cName, "&7Yeni sahip: &f" + tName, "", "&7Tüm bölgeler, çiftçi deposu ve",
                "&7enerji bu oyuncuya geçer.", "&7Sen üye olarak kalırsın.", "", "&cBu işlem geri alınamaz!"));
        inv.setItem(15, Gui.item(Material.RED_STAINED_GLASS_PANE, "&cİptal Et", "&7Devretmeyi iptal et", "&7ve geri dön."));
        inv.setItem(11, Gui.item(Material.GRAY_DYE, "&7Onayla (5)", "&cDevretmek için 5 saniye bekle."));
        open(p, "claim_transfer_confirm", inv);
        Session s = m.session(p);
        s.cid = cid;
        s.target = target;
        s.timerDone = false;
        countdown(p, inv, "&aDEVRET (Onayla)", "&cBu işlem geri alınamaz!", "&7Yeni sahip: &f" + tName, "&aTıkla ve devret.", "Devretmek için %t% saniye bekle.");
    }

    void transferClaim(String mainCid, String oldUuid, String newUuid, String newName, String oldName) {
        List<String> parts = new ArrayList<>();
        for (String cid : m.store.all) {
            Claim c = m.store.get(cid);
            if (c != null && mainCid.equals(c.mainCid)) {
                c.owner = newUuid;
                c.ownerName = newName;
                parts.add(cid);
            }
        }
        Claim mc = m.store.get(mainCid);
        if (mc != null) {
            mc.members.remove(newUuid);
            mc.perms.remove(newUuid);
            if (!mc.members.contains(oldUuid)) {
                mc.members.add(oldUuid);
            }
            defaultPerms(mc, oldUuid);
        }
        m.asChk.remove(mainCid);
        m.validateAutoSell(mainCid);
        m.store.bumpVer(mainCid);
        m.clog(mainCid, "&6Claim devredildi: &e" + oldName + " &7› &e" + newName);
        m.alog("DEVIR", "claim=" + mainCid + " eski=" + oldName + "(" + oldUuid + ") yeni=" + newName + "(" + newUuid + ") parca=" + parts.size());
        for (String part : parts) {
            m.save(part);
        }
        Player np = ClaimModule.onlineByUuid(newUuid);
        if (np != null) {
            ClaimModule.sound(np, "ui.toast.challenge_complete", 1, 1);
            m.pre(np, "<#a8ff78>&e" + oldName + " <#a8ff78>size &e" + (mc == null ? "<none>" : mc.claimName) + " <#a8ff78>claim'ini devretti! Artık sahibi sizsiniz.");
        }
    }

    static void defaultPerms(Claim c, String u) {
        c.setPerm(u, "break", false);
        c.setPerm(u, "place", false);
        c.setPerm(u, "chest", false);
        c.setPerm(u, "interact", true);
        c.setPerm(u, "farmer", false);
        c.setPerm(u, "teleport", true);
        c.setPerm(u, "sethome", false);
        c.setPerm(u, "spawner_break", false);
    }

    // ------------------------------------------------------------------ //
    //  DAVET
    // ------------------------------------------------------------------ //

    static final class Invite {
        volatile String cid;
        volatile String by;
        volatile String byName;
        volatile String tName;
        volatile double time;
        volatile int limit;
        volatile String own;
    }

    final ConcurrentHashMap<String, Invite> invites = new ConcurrentHashMap<>();

    void clearInvite(String u) {
        invites.remove(u);
    }

    void acceptInvite(Player p) {
        String u = ClaimModule.uuid(p);
        Invite inv = invites.remove(u);
        if (inv == null || inv.cid == null) {
            m.pre(p, "&cBekleyen bir claim davetiniz yok.");
            return;
        }
        String own = inv.own == null ? inv.by : inv.own;
        if (ClaimModule.now() - inv.time > 60) {
            m.pre(p, "&cDavetin süresi dolmuş.");
            return;
        }
        Claim c = m.store.get(inv.cid);
        if (c == null || !own.equals(c.owner)) {
            m.pre(p, "&cDavet geçersiz: claim silinmiş veya sahibi değişmiş.");
            return;
        }
        if (u.equals(own)) {
            return;
        }
        if (c.members.contains(u)) {
            m.pre(p, "&cZaten bu claim'in üyesisin.");
            return;
        }
        if (c.members.size() >= inv.limit) {
            m.pre(p, "&cBu claim'in üye sınırı dolmuş, katılamadın.");
            Player byP = ClaimModule.onlineByUuid(inv.by);
            if (byP != null) {
                m.pre(byP, "&e" + p.getName() + " &cdaveti kabul etti ama claim'in üye sınırı dolu olduğu için eklenemedi.");
            }
            return;
        }
        c.members.add(u);
        defaultPerms(c, u);
        m.save(inv.cid);
        m.clog(inv.cid, "&e" + p.getName() + " &aclaim'e katıldı &8(davet: " + inv.byName + ")");
        m.alog("UYE", "KATILDI " + p.getName() + "(" + u + ") claim=" + inv.cid + " davet_eden=" + inv.byName + "(" + inv.by + ")");
        ClaimModule.sound(p, "entity.player.levelup", 1, 1.2f);
        m.pre(p, "<#a8ff78>&e" + c.claimName + " <#a8ff78>claim'ine üye oldun!");
        Player byP = ClaimModule.onlineByUuid(inv.by);
        if (byP != null) {
            ClaimModule.sound(byP, "entity.experience_orb.pickup", 1, 1);
            m.pre(byP, "<#a8ff78>&e" + p.getName() + " <#a8ff78>davetini kabul etti ve claim'ine eklendi! Yetkilerini menüden düzenleyebilirsin.");
        }
    }

    void declineInvite(Player p) {
        String u = ClaimModule.uuid(p);
        Invite inv = invites.remove(u);
        if (inv == null || inv.cid == null) {
            m.pre(p, "&cBekleyen bir claim davetiniz yok.");
            return;
        }
        m.alog("UYE", "DAVET_RED " + p.getName() + "(" + u + ") claim=" + inv.cid + " davet_eden=" + inv.by);
        m.pre(p, "&7Claim davetini &creddettin&7.");
        Player byP = ClaimModule.onlineByUuid(inv.by);
        if (byP != null) {
            m.pre(byP, "&e" + p.getName() + " &cclaim davetini reddetti.");
        }
    }

    /** Suresi dolan davetler (5 sn'de bir, global). */
    void expireInvites() {
        double now = ClaimModule.now();
        for (Map.Entry<String, Invite> e : new ArrayList<>(invites.entrySet())) {
            Invite inv = e.getValue();
            if (now - inv.time <= 60) {
                continue;
            }
            if (!invites.remove(e.getKey(), inv)) {
                continue;
            }
            Player tP = ClaimModule.onlineByUuid(e.getKey());
            if (tP != null) {
                m.pre(tP, "&7Claim davetinin süresi doldu.");
            }
            Player byP = ClaimModule.onlineByUuid(inv.by);
            if (byP != null) {
                m.pre(byP, "&e" + inv.tName + " &7adlı oyuncuya gönderdiğin claim davetinin süresi doldu.");
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  YETKI MENUSU
    // ------------------------------------------------------------------ //

    static final String[] PK = {"spawner_break", "farmer", "teleport", "sethome", "logs"};
    static final Map<String, String> PN = Map.of("spawner_break", "Spawner / Kule Sökme", "farmer", "Çiftçi Erişimi", "teleport", "Işınlanma",
            "sethome", "Sethome (Ev Kurma)", "logs", "Kayıtları Görme");
    static final Map<String, String> PD = Map.of("spawner_break", "Spawner kırabilir, şok kulesi sökebilir.", "farmer", "Çiftçi deposu; DVIP+ ise oto satış.",
            "teleport", "Menüden claime ışınlanabilir.", "sethome", "Claim içinde ev kurabilir.", "logs", "Claim işlem kayıtlarını görebilir.");

    void permMenu(Player p, String cid, String target) {
        Inventory inv = Gui.chest(4, "&8Yetkiler");
        Gui.fill(inv, Material.BLACK_STAINED_GLASS_PANE, 0, 35);
        Gui.fill(inv, Material.GRAY_STAINED_GLASS_PANE, 27, 35);
        inv.setItem(4, Gui.item(Material.PLAYER_HEAD, "<#7dd3fc>" + offName(target), "&7Bu üyenin yetkilerini düzenliyorsun."));
        Claim c = m.store.get(cid);
        int slot = 11;
        for (String k : PK) {
            if (c != null && c.perm(target, k)) {
                inv.setItem(slot, Gui.item(Material.LIME_DYE, "&a" + PN.get(k), "&7Durum: &aAktif", "&7" + PD.get(k), "", "&eTıkla &8· &7Değiştir"));
            } else {
                inv.setItem(slot, Gui.item(Material.GRAY_DYE, "&c" + PN.get(k), "&7Durum: &cPasif", "&7" + PD.get(k), "", "&eTıkla &8· &7Değiştir"));
            }
            slot++;
        }
        inv.setItem(29, Gui.item(Material.NETHER_STAR, "<#f6d365>Tüm Yetkiler", "&7Hepsini tek tıkla aç veya kapat.", "", "&eTıkla &8· &7Değiştir"));
        inv.setItem(31, Gui.item(Material.ARROW, "&7Geri", "&8Üye listesine döner."));
        inv.setItem(33, Gui.item(Material.RED_DYE, "<#ff6b6b>Claim'den At", "&7Bu üyeyi claim'den çıkarır.", "", "&cTıkla &8· &7At"));
        Session s = m.session(p);
        s.cid = cid;
        s.target = target;
        open(p, "claim_perms", inv);
    }

    // ------------------------------------------------------------------ //
    //  ISINLANMA
    // ------------------------------------------------------------------ //

    void teleportToClaim(Player p, String cid) {
        String mainCid = m.mainOf(cid);
        if (m.inSokCombat(p)) {
            m.pre(p, "&cSavaş modundayken ışınlanamazsın.");
            return;
        }
        Integer z = ClaimModule.cidZ(mainCid);
        Integer x = ClaimModule.cidX(mainCid);
        String wName = ClaimModule.cidWorld(mainCid);
        World world = Bukkit.getWorld(wName);
        if (world == null || x == null || z == null) {
            m.pre(p, "<#ff4b1f>Işınlanılacak dünya bulunamadı!");
            return;
        }
        Claim mc = m.store.get(mainCid);
        int startY = mc == null || mc.centerY < 1 ? 200 : mc.centerY;
        Location tpLocCenter = new Location(world, x * 16 + 8 + 0.5, startY, z * 16 + 8 + 0.5, 0, 0);
        p.closeInventory();
        m.pre(p, "<#f6d365>5 saniye içinde claim merkezinize ışınlanacaksınız. Lütfen hareket etmeyin!");
        ClaimModule.sound(p, "block.note_block.pling", 1, 1);
        Location start = p.getLocation();
        AtomicInteger step = new AtomicInteger(0);
        p.getScheduler().runAtFixedRate(m.plugin, task -> {
            int st = step.getAndIncrement();
            if (st % 10 == 0 && st < 50) {
                title(p, "<#f6d365>Işınlanıyorsun", "&e" + (5 - st / 10) + " saniye kaldı... (Hareket etmeyin)", 1);
            }
            if (st > 0) {
                if (!p.isOnline() || p.isDead() || !p.getWorld().equals(start.getWorld())) {
                    task.cancel();
                    return;
                }
                if (p.getLocation().distance(start) > 0.4) {
                    title(p, "<#ff6b6b>Işınlanma iptal edildi", "&cHareket ettiğiniz için ışınlanma iptal edildi!", 2);
                    m.pre(p, "<#ff4b1f>Hareket ettiğiniz için ışınlanma iptal edildi!");
                    ClaimModule.sound(p, "entity.villager.no", 1, 1);
                    task.cancel();
                    return;
                }
            }
            if (st < 50) {
                return;
            }
            task.cancel();
            finishTeleport(p, mainCid, wName, tpLocCenter);
        }, null, 1L, 2L);
    }

    private void finishTeleport(Player p, String mainCid, String wName, Location tpLoc) {
        Claim mc = m.store.get(mainCid);
        if (mc == null || mc.owner == null) {
            m.pre(p, "&cClaim artık mevcut değil, ışınlanma iptal edildi.");
            return;
        }
        if (m.inSokCombat(p)) {
            m.pre(p, "&cSavaş modundayken ışınlanamazsın.");
            return;
        }
        String u = ClaimModule.uuid(p);
        boolean ok = u.equals(mc.owner) || ClaimModule.isKurucu(p) || (mc.members.contains(u) && mc.perm(u, "teleport"));
        if (!ok) {
            m.pre(p, "&cIşınlanma yetkin alındı, işlem iptal edildi.");
            return;
        }
        Session s = m.session(p);
        Location sl = mc.stone;
        boolean toStone = sl != null && sl.getWorld().getName().equals(wName);
        if (toStone) {
            tpLoc = new Location(sl.getWorld(), Math.floor(sl.getX()) + 0.5, Math.floor(sl.getY()) + 1, Math.floor(sl.getZ()) + 0.5, 0, 0);
            s.tpLand = false;
            s.tpStone = sl;
            s.tpStoneT = ClaimModule.now();
        } else {
            s.tpLand = true;
        }
        p.teleportAsync(tpLoc);
        ClaimModule.sound(p, "entity.enderman.teleport", 1, 1);
        if (toStone) {
            title(p, "<#a8ff78>Işınlandın", "&7Claim taşına başarıyla ulaştınız.", 2);
            m.pre(p, "<#a8ff78>Claim taşına güvenle ışınlandınız.");
        } else {
            title(p, "<#a8ff78>Işınlandın", "&7Claim merkezinize başarıyla ulaştınız.", 2);
            m.pre(p, "<#a8ff78>Claim merkezinize güvenle ışınlandınız.");
        }
    }

    // ------------------------------------------------------------------ //
    //  ENERJI SATIN ALMA / PATLAMA DUYURUSU
    // ------------------------------------------------------------------ //

    void buyEnergy(Player p, String cid, double amount) {
        String mainCid = m.mainOf(cid);
        if (m.store.owner(mainCid) == null) {
            m.pre(p, "<#ff4b1f>Geçersiz claim!");
            return;
        }
        double cur = m.energyNow(mainCid);
        double space = m.energyMax(mainCid) - cur;
        if (space <= 0) {
            m.pre(p, "<#ff4b1f>Enerji deposu dolu! &7(Maksimum " + Text.num(m.cfg.energyMaxDays) + " günlük enerji tutulabilir)");
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        double add = Math.min(amount, space);
        double cost = m.energyCost(add);
        if (Econ.balance(p) < cost) {
            m.pre(p, "<#ff4b1f>Yetersiz bakiye! Gerekli: <#f6d365>" + Text.num(cost) + "$");
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        if (!Econ.withdraw(p, cost)) {
            m.pre(p, "<#ff4b1f>Yetersiz bakiye! Gerekli: <#f6d365>" + Text.num(cost) + "$");
            return;
        }
        double nw = m.addEnergy(mainCid, add);
        if (nw < 0) {
            Econ.deposit(p, cost);
            m.pre(p, "<#ff4b1f>İşlem yapılamadı, lütfen tekrar deneyin. &7(Ücret iade edildi)");
            return;
        }
        m.clog(mainCid, "&e" + p.getName() + " &benerji yükledi &8(+" + (long) Math.floor(add) + ")");
        m.alog("ENERJI", p.getName() + "(" + p.getUniqueId() + ") claim=" + mainCid + " +" + (long) Math.floor(add) + " enerji ucret=" + Text.num(cost) + " yeni=" + (long) Math.floor(nw));
        Claim mc = m.store.get(mainCid);
        Double exp = mc == null ? null : mc.expiry;
        for (String sub : m.store.all) {
            if (sub.equals(mainCid)) {
                continue;
            }
            Claim sc = m.store.get(sub);
            if (sc != null && mainCid.equals(sc.mainCid)) {
                sc.expiry = exp;
                sc.needsSave = true;
            }
        }
        m.save(mainCid);
        ClaimModule.sound(p, "block.beacon.power_select", 0.8f, 1.4f);
        ClaimModule.sound(p, "entity.experience_orb.pickup", 1, 1.2f);
        title(p, "<#a8ff78>Enerji yüklendi", "&f+" + (long) Math.floor(add) + " &7enerji &8(&a" + Text.num(cost) + "$&8)", 3);
        m.pre(p, "<#a8ff78>Claim enerjisi yüklendi: &b+" + (long) Math.floor(add) + " &7› &b" + (long) Math.floor(nw) + "&7/&b"
                + (long) Math.floor(m.energyMax(mainCid)) + " &8(&a-" + Text.num(cost) + "$&8)");
        m.pre(p, "&7Yeni kalan süre: &e" + m.energyTimeText(mainCid));
        m.menus.time(p, cid);
    }

    void broadcastExplosion(String reason, String cName, String cid) {
        Integer z = ClaimModule.cidZ(cid);
        Integer x = ClaimModule.cidX(cid);
        int bx = (x == null ? 0 : x) * 16 + 8;
        int bz = (z == null ? 0 : z) * 16 + 8;
        Claim c = m.store.get(cid);
        String ownerName = c == null || c.ownerName == null ? "Bilinmiyor" : c.ownerName;
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(" <#ff6b6b>Bir claim patladı");
        if (!reason.equals("silindi")) {
            lines.add(" &7Enerjisi bittiği için yıkıldı, bölge artık korumasız.");
        }
        lines.add(" &7Claim &8· &f" + cName);
        lines.add(" &7Sahibi &8· &f" + ownerName);
        lines.add(" &7Konum &8· &fX " + bx + ", Z " + bz);
        lines.add("");
        for (String l : lines) {
            Bukkit.broadcast(Text.color(l));
        }
        for (Player pl : Bukkit.getOnlinePlayers()) {
            // Folia: ses oyuncunun kendi bolge thread'inde calinir
            pl.getScheduler().run(m.plugin, t -> {
                pl.playSound(pl.getLocation(), "entity.generic.explode", 1f, 0.8f);
                pl.playSound(pl.getLocation(), "entity.ender_dragon.growl", 0.5f, 1.5f);
            }, null);
        }
    }

    static Map<String, Object> unused() {
        return new HashMap<>();
    }
}
