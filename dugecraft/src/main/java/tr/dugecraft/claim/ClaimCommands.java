package tr.dugecraft.claim;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import tr.dugecraft.util.DynCommands;
import tr.dugecraft.util.Gui;
import tr.dugecraft.util.Text;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * claim.sk komutlari: /claim, /claimtp, /claimadmin ve /sethome /home /delhome
 * (Homes) kaydi + asenkron TAB tamamlama.
 */
final class ClaimCommands implements CommandExecutor, Listener {

    private final ClaimModule m;

    ClaimCommands(ClaimModule m) {
        this.m = m;
    }

    void register() {
        DynCommands.register("claim", "Claim menüsü", this);
        DynCommands.register("claimtp", "Claim'e ışınlan (kurucu)", this);
        DynCommands.register("claimadmin", "Claim yönetici komutları", this);
        DynCommands.register("sethome", "Ev kur", m.homes);
        DynCommands.register("home", "Eve ışınlan", m.homes);
        DynCommands.register("homes", "Eve ışınlan", m.homes);
        DynCommands.register("delhome", "Ev sil", m.homes);
    }

    private static boolean eq(String a, String... opts) {
        if (a == null) {
            return false;
        }
        for (String o : opts) {
            if (a.equalsIgnoreCase(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(s instanceof Player p)) {
            Text.send(s, "&cThis command can only be used by players");
            return true;
        }
        try {
            switch (cmd.getName().toLowerCase(Locale.ROOT)) {
                case "claim" -> claim(p, args.length == 0 ? null : String.join(" ", args));
                case "claimtp" -> claimTp(p, args.length == 0 ? null : String.join(" ", args));
                case "claimadmin" -> claimAdmin(p, args);
                default -> {
                    return false;
                }
            }
        } catch (RuntimeException ex) {
            m.plugin.getLogger().warning("[claim] komut hatasi (/" + cmd.getName() + "): " + ex);
        }
        return true;
    }

    // ------------------------------------------------------------------ //

    private void claim(Player p, String a) {
        if (eq(a, "onay", "kabul", "onayla")) {
            m.actions.acceptInvite(p);
            return;
        }
        if (eq(a, "reddet", "red")) {
            m.actions.declineInvite(p);
            return;
        }
        if (eq(a, "sohbet", "chat")) {
            m.chat.toggle(p, "");
            return;
        }
        if (eq(a, "al", "satınal", "satinal")) {
            m.actions.chunkBuyMenu(p);
            return;
        }
        if (eq(a, "sil")) {
            m.actions.chunkDeleteCmd(p);
            return;
        }
        m.menus.myClaims(p);
    }

    // ------------------------------------------------------------------ //

    String tpToken(String mc) {
        Claim c = m.store.get(mc);
        String n = c == null || c.claimName == null ? "Claim" : c.claimName;
        String o = c == null || c.ownerName == null ? "Bilinmiyor" : c.ownerName;
        return (Gui.plain(n) + "@" + o).replace(" ", "_");
    }

    String tpResolve(String arg) {
        Claim c = m.store.get(arg);
        if (c != null && c.owner != null) {
            return c.mainCid == null ? arg : c.mainCid;
        }
        for (String cid : m.store.all) {
            Claim mc = m.store.get(cid);
            if (mc != null && mc.main() && tpToken(cid).equalsIgnoreCase(arg)) {
                return cid;
            }
        }
        return null;
    }

    private void claimTp(Player p, String a) {
        if (!p.hasPermission("claim.kurucu")) {
            Text.send(p, "&cBu komutu kullanma yetkin yok.");
            return;
        }
        if (a == null) {
            int cnt = 0;
            for (String cid : m.store.all) {
                Claim c = m.store.get(cid);
                if (c != null && c.main()) {
                    cnt++;
                }
            }
            m.pre(p, "&eKullanım: &f/claimtp <claim> &7(TAB ile seç) &8· &7Toplam &e" + cnt + " &7claim");
            return;
        }
        String mc = tpResolve(a);
        if (mc == null) {
            m.pre(p, "<#ff4b1f>Claim bulunamadı: &f" + a);
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        Integer z = ClaimModule.cidZ(mc);
        Integer x = ClaimModule.cidX(mc);
        World w = Bukkit.getWorld(ClaimModule.cidWorld(mc));
        if (w == null || x == null || z == null) {
            m.pre(p, "<#ff4b1f>Claim'in dünyası bulunamadı!");
            return;
        }
        Claim c = m.store.get(mc);
        int startY = c == null ? 200 : c.centerY;
        if (startY < 1) {
            startY = 200;
        }
        Location tp = new Location(w, x * 16 + 8.5, startY, z * 16 + 8.5);
        m.session(p).tpLand = true;
        p.teleportAsync(tp);
        String cName = c == null || c.claimName == null ? "<none>" : c.claimName;
        String oName = c == null || c.ownerName == null ? "<none>" : c.ownerName;
        ClaimModule.sound(p, "entity.enderman.teleport", 1, 1);
        m.pre(p, "<#a8ff78>&e" + cName + " &7(" + oName + ") <#a8ff78>claim'ine ışınlandın.");
        m.alog("KURUCU", p.getName() + "(" + p.getUniqueId() + ") /claimtp claim=" + mc + " isim=" + cName);
    }

    // ------------------------------------------------------------------ //

    private static String nz(Object o) {
        if (o == null) {
            return "<none>";
        }
        if (o instanceof Number n) {
            return Text.num(n);
        }
        return o.toString();
    }

    private void claimAdmin(Player p, String[] args) {
        String a1 = args.length > 0 ? args[0] : null;
        String a2 = args.length > 1 ? args[1] : null;
        String a3 = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;
        boolean ok = p.isOp() || p.hasPermission("claim.admin") || ClaimModule.isKurucu(p);
        if (!ok) {
            m.pre(p, "&cBu komutu kullanma yetkin yok.");
            return;
        }
        if (eq(a1, "bilgi", "info")) {
            info(p, args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null);
            return;
        }
        if (!p.isOp() && !ClaimModule.isKurucu(p)) {
            m.pre(p, "&cBu alt komut için yetkin yok. &7(Kullanabileceğin: &e/claimadmin bilgi&7)");
            return;
        }
        if (eq(a1, "tas", "taş")) {
            String here = ClaimModule.chunkId(p.getLocation());
            Claim hc = m.store.get(here);
            String mc = hc == null ? null : hc.mainCid;
            if (mc == null) {
                m.pre(p, "&cBurada bir claim yok.");
                return;
            }
            if (m.towerNearLoc(p.getLocation())) {
                m.pre(p, "&cClaim taşı şok kulesinin dibine konulamaz. &7Arada en az 1 blok boşluk bırak.");
                return;
            }
            m.placeStone(mc, p.getLocation());
            m.alog("ADMIN", p.getName() + "(" + p.getUniqueId() + ") /claimadmin tas claim=" + mc + " loc=" + m.altxt(p.getLocation()));
            m.pre(p, "&aClaim taşı buraya kuruldu.");
        } else if (eq(a1, "afk")) {
            afk(p, a2, a3);
        } else if (eq(a1, "log")) {
            m.flushAdminLog();
            m.pre(p, "&aAdmin logu dosyaya yazıldı: &fplugins/Skript/logs/");
        } else if (eq(a1, "fiyat", "fiyatlar")) {
            int okN = m.refreshFarmerPrices(true);
            m.pre(p, "&aÇiftçi fiyatları EconomyShopGUI'den yenilendi. &7(&e" + okN + "&7/&e" + Farmer.KEYS.size() + "&7)");
            for (String k : Farmer.KEYS) {
                String src = m.fPriceSrc.get(k);
                m.send(p, "&f" + Farmer.name(k) + "&7: " + ClaimModule.farmerPriceText(m.price(k)) + " &8(" + nz(src) + ")");
            }
        } else {
            m.pre(p, "&7Kullanım: &e/claimadmin bilgi [claim] &7| &e/claimadmin tas &7| &e/claimadmin fiyat &7| &e/claimadmin log &7| &e/claimadmin afk");
        }
    }

    private void info(Player p, String a2) {
        String mc;
        String here;
        if (a2 != null) {
            mc = tpResolve(a2);
            here = mc;
        } else {
            here = ClaimModule.chunkId(p.getLocation());
            Claim hc = m.store.get(here);
            mc = hc == null ? null : hc.mainCid;
            if (hc != null && hc.owner != null && mc == null) {
                mc = here;
            }
        }
        if (mc == null) {
            m.pre(p, "&7Burada claim yok. &8(Belirli bir claim için: /claimadmin bilgi <claim> — TAB ile seç)");
            return;
        }
        Claim c = m.store.get(mc);
        if (c == null || c.owner == null) {
            m.pre(p, "&cClaim bulunamadı.");
            return;
        }
        double e = m.energyNow(mc);
        String ou = c.owner;
        String oOn = ClaimModule.onlineByUuid(ou) != null ? "&a(çevrimiçi)" : "&8(çevrimdışı)";
        StringBuilder mNames = new StringBuilder();
        int mCnt = 0;
        for (String mu : c.members) {
            mCnt++;
            if (mNames.length() > 0) {
                mNames.append(", ");
            }
            mNames.append(ClaimActions.offName(mu));
        }
        String mn = mNames.length() == 0 ? "&8yok" : mNames.toString();
        int asCnt = 0;
        for (String k : Farmer.KEYS) {
            if (c.asOn.containsKey(k)) {
                asCnt++;
            }
        }
        String fTxt = c.farmerOn() ? "&aAktif &7(Seviye " + c.farmerLevel + ", oto satış: " + asCnt + " ürün)" : "&cKapalı";
        double pl = m.claimProtectLeft(mc);
        String pTxt = pl > 0 ? "&a" + m.fmtDur(pl) + " kaldı" : "&8yok";
        String sTxt = c.stone != null ? "&f" + m.ltxt(c.stone) : "&8yok";
        Deque<String> lg = m.store.clog.get(mc);
        int lCnt = lg == null ? 0 : lg.size();
        String chTxt = mc.equals(here) ? "Ana claim" : "Genişletme bölgesi";
        double exp = c.expiry == null ? 0 : c.expiry;
        m.send(p, "&6CLAIM BİLGİSİ &8› &e" + nz(c.claimName) + " &8(" + mc + ")");
        m.send(p, "&7Sahip: &f" + nz(c.ownerName) + " " + oOn + " &8(" + ou + ")");
        m.send(p, "&7Bu chunk: &f" + here + " &8(" + chTxt + ") &8· &7Toplam chunk: &f" + c.chunkCount);
        m.send(p, "&7Enerji: &b" + (long) Math.floor(e) + "&7/&b" + (long) Math.floor(m.energyMax(mc)) + " &8· &7Kalan: &e" + m.fmtDur(exp - ClaimModule.now()));
        m.send(p, "&7Yeni claim koruması: " + pTxt);
        m.send(p, "&7Yükseltmeler: &fGece " + m.upgrades.stateTxt(mc, "night") + " &8· &f2x Farm " + m.upgrades.stateTxt(mc, "farm2x") + " &8· &7Tüketim &cx" + Text.num(m.upgMult(mc)));
        m.send(p, "&7Üyeler (&f" + mCnt + "&7): &f" + mn);
        m.send(p, "&7Çiftçi: " + fTxt);
        m.send(p, "&7Claim taşı: " + sTxt + " &8· &7Kuruluş: &f" + nz(c.createdText) + " &8· &7Kayıt: &f" + lCnt);
    }

    private void afk(Player p, String a2, String a3) {
        String tu;
        String tn;
        if (a3 != null) {
            OfflinePlayer op = Bukkit.getPlayerExact(a3);
            if (op == null) {
                op = Bukkit.getOfflinePlayerIfCached(a3);
            }
            if (op == null) {
                m.pre(p, "&cOyuncu bulunamadı: &f" + a3);
                return;
            }
            tu = op.getUniqueId().toString();
            tn = op.getName() == null ? a3 : op.getName();
        } else {
            tu = ClaimModule.uuid(p);
            tn = p.getName();
        }
        ClaimActions.Sac sc = m.actions.sac.computeIfAbsent(tu, k -> new ClaimActions.Sac());
        if (eq(a2, "test")) {
            sc.need = true;
            m.alog("ADMIN", p.getName() + "(" + p.getUniqueId() + ") /claimadmin afk test hedef=" + tn + "(" + tu + ")");
            if (tu.equals(ClaimModule.uuid(p))) {
                m.pre(p, "&aAFK kontrolü açıldı. &7Ayrıca çiftçide &fSat &7tuşuna basınca da açılır (doğrulayana kadar).");
                m.actions.sellAfkMenu(p, "", "");
            } else {
                m.pre(p, "&e" + tn + " &aiçin AFK kontrolü istendi. &7Bir sonraki satışında menü açılacak.");
            }
            return;
        }
        if (eq(a2, "sifirla", "sıfırla")) {
            sc.last = null;
            sc.sess = null;
            sc.cnt = 0;
            synchronized (sc.iv) {
                sc.iv.clear();
            }
            sc.need = false;
            sc.fail = 0;
            sc.lock = null;
            m.alog("ADMIN", p.getName() + "(" + p.getUniqueId() + ") /claimadmin afk sifirla hedef=" + tn + "(" + tu + ")");
            m.pre(p, "&e" + tn + " &aiçin AFK sayaçları, bekleyen kontrol ve kilit sıfırlandı.");
            return;
        }
        if (eq(a2, "durum")) {
            long now = System.currentTimeMillis();
            String sTxt = sc.sess != null ? "&f" + (long) Math.floor((now - sc.sess) / 60000.0) + " dk &7/ " + Text.num(m.cfg.sellCheckMinutes) + " dk" : "&8yok";
            String lTxt = sc.last != null ? "&f" + (long) Math.floor((now - sc.last) / 1000.0) + " sn önce" : "&8yok";
            String nTxt = sc.need ? "&cEvet" : "&aHayır";
            String kTxt = sc.lock != null && now < sc.lock ? "&c" + m.fmtDur((sc.lock - now) / 1000.0) + " kaldı" : "&8yok";
            List<Long> iv;
            synchronized (sc.iv) {
                iv = new ArrayList<>(sc.iv);
            }
            StringBuilder ivTxt = new StringBuilder();
            double sum = 0;
            for (long v : iv) {
                sum += v;
                if (ivTxt.length() > 0) {
                    ivTxt.append(", ");
                }
                ivTxt.append(v);
            }
            String ivs = ivTxt.length() == 0 ? "&8yok" : ivTxt.toString();
            String rTxt = "&8(6 aralık gerekli, şu an " + iv.size() + ")";
            if (iv.size() >= 6) {
                double mean = sum / 6;
                double var = 0;
                for (long v : iv) {
                    double d = v - mean;
                    var += d * d;
                }
                double sd = Math.sqrt(var / 6);
                double cv = Math.round((sd / mean) * 1000) / 10.0;
                rTxt = "&fort " + (long) Math.floor(mean) + " ms, sapma " + (long) Math.floor(sd) + " ms &8(yüzde " + Text.num(cv) + ", sınır " + Text.num(m.cfg.sellRhythmCv) + ")";
                if (mean < m.cfg.sellRhythmMinMs) {
                    rTxt = rTxt + " &8- ortalama " + m.cfg.sellRhythmMinMs + " ms altı, sayılmaz";
                }
            }
            m.send(p, "&6AFK DURUMU &8› &e" + tn);
            m.send(p, "&7Satış sayısı: &f" + sc.cnt + "&7/" + m.cfg.sellCheckCount + " &8· &7Son satış: " + lTxt);
            m.send(p, "&7Aralıksız satış: " + sTxt);
            m.send(p, "&7Son aralıklar (ms): &f" + ivs);
            m.send(p, "&7Ritim: " + rTxt);
            m.send(p, "&7Kontrol bekliyor: " + nTxt + " &8· &7Yanlış: &f" + sc.fail + "&7/" + m.cfg.sellAfkFails + " &8· &7Kilit: " + kTxt);
            return;
        }
        m.pre(p, "&7Kullanım: &e/claimadmin afk test [oyuncu] &7| &e/claimadmin afk durum [oyuncu] &7| &e/claimadmin afk sifirla [oyuncu]");
    }

    // ------------------------------------------------------------------ //
    //  TAB (asenkron): sadece bellekteki veriyi OKUR
    // ------------------------------------------------------------------ //

    @EventHandler(priority = EventPriority.NORMAL)
    public void tab(AsyncTabCompleteEvent e) {
        String buf = e.getBuffer();
        String low = buf.toLowerCase(Locale.ROOT);
        if (low.startsWith("/claim ")) {
            String carg = low.substring(7);
            if (carg.contains(" ")) {
                return;
            }
            List<String> res = new ArrayList<>();
            for (String opt : new String[]{"al", "sil", "sohbet", "onay", "reddet"}) {
                if (opt.startsWith(carg)) {
                    res.add(opt);
                }
            }
            e.setCompletions(res);
            e.setHandled(true);
            return;
        }
        String pre = "";
        CommandSender sd = e.getSender();
        if (low.startsWith("/claimtp ")) {
            if (!sd.hasPermission("claim.kurucu")) {
                return;
            }
            pre = "/claimtp ";
        } else if (low.startsWith("/claimadmin bilgi ")) {
            if (sd.hasPermission("claim.admin") || sd.hasPermission("claim.kurucu")) {
                pre = "/claimadmin bilgi ";
            }
        }
        if (pre.isEmpty()) {
            return;
        }
        String arg = buf.substring(pre.length()).toLowerCase(Locale.ROOT);
        List<String> res = new ArrayList<>();
        for (String cid : m.store.all) {
            Claim c = m.store.get(cid);
            if (c != null && c.main()) {
                String t = tpToken(cid);
                if (t.toLowerCase(Locale.ROOT).startsWith(arg)) {
                    res.add(t);
                }
            }
        }
        e.setCompletions(res);
        e.setHandled(true);
    }
}
