package tr.dugecraft.claim;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import tr.dugecraft.util.Econ;
import tr.dugecraft.util.Gui;
import tr.dugecraft.util.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** claim.sk 6. CHAT GIRDILERI + v15.12 CLAIM SOHBETI. Sohbet olayi asenkron: dunyaya dokunulmaz. */
final class ClaimChat {

    private final ClaimModule m;

    ClaimChat(ClaimModule m) {
        this.m = m;
    }

    boolean canUse(String u, String mc) {
        if (mc == null) {
            return false;
        }
        Claim c = m.store.get(mc);
        if (c == null || c.owner == null) {
            return false;
        }
        return u.equals(c.owner) || c.members.contains(u);
    }

    String pick(Player p) {
        String u = ClaimModule.uuid(p);
        String hc = ClaimModule.chunkId(p.getLocation());
        Claim h = m.store.get(hc);
        if (h != null && h.owner != null) {
            String hm = h.mainOrSelf();
            if (canUse(u, hm)) {
                return hm;
            }
        }
        for (String cid : m.store.all) {
            Claim c = m.store.get(cid);
            if (c != null && c.main() && u.equals(c.owner)) {
                return cid;
            }
        }
        for (String cid : m.store.all) {
            Claim c = m.store.get(cid);
            if (c != null && c.main() && c.members.contains(u)) {
                return cid;
            }
        }
        return "";
    }

    void toggle(Player p, String mc) {
        String u = ClaimModule.uuid(p);
        Session s = m.session(p);
        String cur = s.cchat;
        if (mc.isEmpty()) {
            if (cur != null) {
                s.cchat = null;
                m.pre(p, "&7Claim sohbeti &ckapatıldı&7. Mesajların artık genel sohbete gidiyor.");
                ClaimModule.sound(p, "block.note_block.bass", 0.6f, 1.2f);
                return;
            }
            mc = pick(p);
            if (mc.isEmpty()) {
                m.pre(p, "&cClaim sohbeti için bir claim'in sahibi ya da üyesi olmalısın.");
                return;
            }
        }
        if (!canUse(u, mc)) {
            m.pre(p, "&cBu claim'in sohbetini kullanamazsın.");
            return;
        }
        if (mc.equals(cur)) {
            s.cchat = null;
            m.pre(p, "&7Claim sohbeti &ckapatıldı&7. Mesajların artık genel sohbete gidiyor.");
            ClaimModule.sound(p, "block.note_block.bass", 0.6f, 1.2f);
            return;
        }
        s.cchat = mc;
        Claim c = m.store.get(mc);
        m.pre(p, "<#ff9a3c>Claim sohbeti açıldı &8· &f" + (c == null ? "<none>" : c.claimName));
        m.send(p, "&8› &7Yazdıkların sadece bu claim'in üyelerine gider. Kapatmak için &e/claim sohbet");
        ClaimModule.sound(p, "block.note_block.chime", 0.7f, 1.4f);
    }

    void send(Player p, String mc, String msg) {
        MiniMessage mm = MiniMessage.miniMessage();
        Claim c = m.store.get(mc);
        String cn = c == null || c.claimName == null ? "Claim" : c.claimName;
        cn = mm.escapeTags(ChatColor.stripColor(Gui.legacy(cn)));
        String pn = mm.escapeTags(p.getName());
        String tx = mm.escapeTags(ChatColor.stripColor(Gui.legacy(msg)));
        String rl = c != null && ClaimModule.uuid(p).equals(c.owner) ? "<#f6d365>" : "<#ff9a3c>";
        Component comp = mm.deserialize("<#ff9a3c>[Claim] <#ffd8a8>" + cn + " <#6b7280>› " + rl + pn + "<#6b7280>: <#ffffff>" + tx);
        List<String> to = new ArrayList<>();
        if (c != null) {
            if (c.owner != null) {
                to.add(c.owner);
            }
            to.addAll(c.members);
        }
        int n = 0;
        for (String u : to) {
            Player tp = ClaimModule.onlineByUuid(u);
            if (tp != null) {
                tp.sendMessage(comp);
                n++;
            }
        }
        if (n == 0) {
            p.sendMessage(comp);
        }
        if (n == 1) {
            m.send(p, "&8› &7Şu an çevrimiçi başka üye yok.");
        }
    }

    /** "on chat" — sohbet girdileri ve claim sohbeti. true donerse olay iptal edildi. */
    void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Session s = m.session(p);
        String message = PlainTextComponentSerializer.plainText().serialize(e.message());
        if ("naming".equals(s.chatMode) && s.chatCid != null) {
            double nt = Double.isNaN(s.chatAt) ? 0 : s.chatAt;
            if (ClaimModule.now() - nt > 60) {
                s.chatMode = null;
                s.chatCid = null;
                m.pre(p, "&7Claim ismi girme süresi dolduğu için işlem iptal edildi.");
            }
        }
        if ("naming".equals(s.chatMode) && s.chatCid != null) {
            e.setCancelled(true);
            String cid = s.chatCid;
            double price = s.chatPrice;
            s.chatMode = null;
            s.chatCid = null;
            naming(p, cid, price, message);
            return;
        }
        if ("member".equals(s.chatMode) && s.chatCid != null) {
            e.setCancelled(true);
            String cid = s.chatCid;
            s.chatMode = null;
            s.chatCid = null;
            s.chatAt = Double.NaN;
            memberInput(p, cid, message);
            return;
        }
        String ccm = s.cchat;
        if (ccm != null) {
            e.setCancelled(true);
            if (!canUse(ClaimModule.uuid(p), ccm)) {
                s.cchat = null;
                m.pre(p, "&cClaim sohbetin kapandı (claim silinmiş veya artık üye değilsin). &7Mesajın gönderilmedi, tekrar yaz.");
                return;
            }
            send(p, ccm, message);
        }
    }

    private void naming(Player p, String cid, double price, String message) {
        if (message.equalsIgnoreCase("iptal")) {
            m.pre(p, "&cClaim alma işlemi iptal edildi.");
            return;
        }
        if (!m.inClaimWorld(p)) {
            m.pre(p, "&cClaim alma işlemi iptal edildi. &7Ana Dünya'ya dönüp tekrar deneyin.");
            return;
        }
        String cName = ChatColor.stripColor(Gui.legacy(message)).replace("&", "").replace("§", "");
        if (cName.length() > 16) {
            m.pre(p, "<#ff4b1f>Claim ismi maksimum 16 karakter olabilir!");
            return;
        }
        if (cName.length() < 2) {
            m.pre(p, "<#ff4b1f>Claim ismi en az 2 karakter olmalıdır!");
            return;
        }
        if (Econ.balance(p) < price) {
            m.pre(p, "<#ff4b1f>Yetersiz bakiye! Gerekli: <#f6d365>" + Text.num(price) + "$");
            return;
        }
        if (!ClaimModule.chunkId(p.getLocation()).equals(cid)) {
            m.pre(p, "<#ff4b1f>Claim kurmak için o bölgenin içinde durmalısın. &7İşlem iptal edildi.");
            return;
        }
        if (m.store.owner(cid) != null) {
            m.pre(p, "<#ff4b1f>Bu bölge siz isim yazarken başkası tarafından claimlendi!");
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        int mx = m.maxMainClaims(p);
        if (m.playerMainClaimCount(p) >= mx) {
            m.pre(p, "<#ff4b1f>Maksimum " + mx + " ayrı claim sınırına ulaştınız!");
            return;
        }
        if (m.tooCloseToOtherClaims(cid, "")) {
            m.pre(p, "<#ff4b1f>Bu alan başka bir claime çok yakın! En az 3 chunk boşluk olmalı.");
            return;
        }
        if (!m.chunkInBorder(cid)) {
            m.pre(p, "<#ff4b1f>Bu bölge dünya sınırının dışında! &7Claim sadece &e" + m.cfg.borderMin + " &7ile &e" + m.cfg.borderMax + " &7koordinatları arasında alınabilir.");
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        String u = ClaimModule.uuid(p);
        if (!m.reserve(cid, u)) {
            m.pre(p, "<#ff4b1f>Bu bölge şu an başka biri tarafından alınıyor.");
            return;
        }
        if (m.store.owner(cid) != null) {
            m.release(cid);
            m.pre(p, "<#ff4b1f>Bu bölge az önce başkası tarafından alındı!");
            return;
        }
        if (!Econ.withdraw(p, price)) {
            m.release(cid);
            m.pre(p, "<#ff4b1f>Yetersiz bakiye! Gerekli: <#f6d365>" + Text.num(price) + "$");
            return;
        }
        double now = ClaimModule.now();
        Claim c = new Claim(cid);
        c.owner = u;
        c.ownerName = p.getName();
        c.claimName = cName;
        c.energy = m.cfg.energyStart;
        c.energyTime = now;
        c.expiry = now + 30 * 86400;
        c.isMain = true;
        c.mainCid = cid;
        c.chunkCount = 1;
        c.centerY = (int) Math.floor(p.getLocation().getY() + 1);
        c.createdText = new SimpleDateFormat("dd.MM.yyyy").format(new Date());
        boolean given = false;
        String reason = "cooldown";
        Double protLastO = m.persist.num("claimprot_used", u);
        double protLast = protLastO == null ? 0 : protLastO;
        if (m.persist.get("protchunk", cid) != null) {
            reason = "chunk";
        } else if (now - protLast >= m.cfg.newClaimProtectCooldownDays * 86400) {
            c.protectUntil = Math.floor(now + m.cfg.newClaimProtectHours * 3600);
            m.persist.set("claimprot_used", u, now);
            given = true;
        }
        m.store.claims.put(cid, c);
        m.markProtChunk(cid, cid);
        for (String k : Farmer.KEYS) {
            m.store.fSet(cid, k, 0);
        }
        m.save(cid);
        m.release(cid);
        ClaimModule.sound(p, "ui.toast.challenge_complete", 1, 1);
        ClaimModule.sound(p, "entity.player.levelup", 1, 1.2f);
        m.clog(cid, "&e" + p.getName() + " &aclaim'i kurdu &8(" + cName + ")");
        m.alog("CLAIM", "KURULDU " + p.getName() + "(" + u + ") claim=" + cid + " isim=" + cName + " ucret=" + Text.num(price) + " loc=" + m.altxt(p.getLocation()));
        ClaimActions.title(p, "<#a8ff78>Claim oluşturuldu", "&7İsim: &e" + cName, 3);
        m.pre(p, "<#a8ff78>Tebrikler! &e" + cName + " <#a8ff78>isimli claiminiz kuruldu. <#f6d365>(Ödenen: " + Text.num(price) + "$)");
        if (given) {
            m.pre(p, "<#a8ff78>Yeni claim koruması aktif: &e" + Text.num(m.cfg.newClaimProtectHours) + " saat &7boyunca claim'in raidlenemez. Bu sürede üsünü kur!");
            m.alog("KORUMA", p.getName() + "(" + u + ") claim=" + cid + " " + Text.num(m.cfg.newClaimProtectHours) + " saat koruma aldi");
        } else if (reason.equals("chunk")) {
            m.pre(p, "&7Yeni claim koruması verilmedi: bu bölgede &edaha önce claim kurulmuş&7. Koruma her bölge için sadece bir kez verilir.");
        } else {
            double wait = (protLast + m.cfg.newClaimProtectCooldownDays * 86400) - now;
            m.pre(p, "&7Yeni claim koruması verilmedi: son &e" + Text.num(m.cfg.newClaimProtectCooldownDays) + " gün &7içinde zaten kullandın. &8(Tekrar: " + m.fmtDur(wait) + ")");
        }
        Session s = m.session(p);
        s.stonePendingCid = cid;
        s.stonePendingLoc = p.getLocation();
        m.pre(p, "&eClaim taşın bulunduğun noktaya kuruldu. &7Menüden taşıyabilirsin.");
    }

    private void memberInput(Player p, String cid, String message) {
        if (message.equalsIgnoreCase("iptal")) {
            m.pre(p, "&cİşlem iptal edildi.");
            return;
        }
        String targetStr = ChatColor.stripColor(Gui.legacy(message)).replace(" ", "");
        Player target = Bukkit.getPlayer(targetStr);
        if (target == null) {
            if (targetStr.isEmpty()) {
                m.pre(p, "<#ff4b1f>Geçerli bir oyuncu ismi girmelisiniz!");
                return;
            }
            m.pre(p, "<#ff4b1f>Davet gönderilemedi: oyuncu şu an çevrimiçi değil!");
            return;
        }
        String tu = ClaimModule.uuid(target);
        Claim c = m.store.get(cid);
        if (c != null && c.members.contains(tu)) {
            m.pre(p, "<#ff4b1f>Bu oyuncu zaten ekli!");
            return;
        }
        String u = ClaimModule.uuid(p);
        if ((c == null || !u.equals(c.owner)) && !ClaimModule.isKurucu(p)) {
            m.pre(p, "<#ff4b1f>Artık bu claim'in sahibi değilsiniz, davet gönderilemedi.");
            return;
        }
        if (tu.equals(u)) {
            m.pre(p, "<#ff4b1f>Kendinizi üye olarak ekleyemezsiniz!");
            return;
        }
        int max = m.memberLimit(p);
        int cur = c == null ? 0 : c.members.size();
        if (cur >= max) {
            m.pre(p, "<#ff4b1f>Maksimum üye ekleme sınırına (&e" + max + "<#ff4b1f>) ulaştınız!");
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        double nowT = ClaimModule.now();
        ClaimActions.Invite old = m.actions.invites.get(tu);
        if (old != null && nowT - old.time <= 60 && !old.cid.equals(cid)) {
            m.pre(p, "<#ff4b1f>Bu oyuncunun bekleyen başka bir claim daveti var. Biraz sonra tekrar deneyin.");
            return;
        }
        ClaimActions.Invite inv = new ClaimActions.Invite();
        inv.cid = cid;
        inv.by = u;
        inv.byName = p.getName();
        inv.tName = target.getName();
        inv.time = nowT;
        inv.limit = max;
        inv.own = c == null ? null : c.owner;
        m.actions.invites.put(tu, inv);
        m.alog("UYE", "DAVET " + p.getName() + "(" + u + ") -> " + target.getName() + "(" + tu + ") claim=" + cid);
        String cName = c == null ? "<none>" : c.claimName;
        m.pre(p, "<#a8ff78>&e" + target.getName() + " <#a8ff78>adlı oyuncuya üyelik daveti gönderildi. &7(60 saniye içinde onaylamazsa iptal olur)");
        m.send(p, " &7Sadece &fgüvendiğin &7kişileri ekle. Üyeler verdiğin yetkilere göre spawner kırabilir, çiftçi deposunu kullanabilir ve claim'ine ışınlanabilir.");
        m.pre(target, "&e" + p.getName() + " &7seni &6" + cName + " &7claim'ine üye olarak davet ediyor.");
        m.send(target, " &7Sadece &fgüvendiğin &7kişilerin davetini kabul et. Emin değilsen reddet.");
        target.sendMessage(MiniMessage.miniMessage().deserialize("<click:run_command:'/claim onay'><hover:show_text:'<#a8ff78>Daveti kabul et'><#a8ff78>[ Onayla ]</hover></click> <click:run_command:'/claim reddet'><hover:show_text:'<#f87171>Daveti reddet'><#f87171>[ Reddet ]</hover></click>"));
        m.send(target, "&7Ya da yaz: &a/claim onay &7veya &c/claim reddet &8(60 saniye)");
        target.playSound(target.getLocation(), "block.note_block.pling", 1, 1.5f);
    }
}
