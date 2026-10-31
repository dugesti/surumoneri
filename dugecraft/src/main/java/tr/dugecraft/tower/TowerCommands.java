package tr.dugecraft.tower;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tr.dugecraft.util.Text;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/** /sokkulesi, /sokdebug, /sokmagaza */
final class TowerCommands implements CommandExecutor {

    static final String NO_PERM = "&cYou don't have the required permission to use this command";

    private final TowerModule m;

    TowerCommands(TowerModule m) {
        this.m = m;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String a = args.length == 0 ? null : String.join(" ", args);
        switch (cmd.getName().toLowerCase()) {
            case "sokkulesi" -> sokkulesi(s, a);
            case "sokdebug" -> {
                if (s instanceof Player p) {
                    toggleDebug(p);
                }
            }
            case "sokmagaza" -> sokmagaza(s, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void toggleDebug(Player p) {
        PState st = m.ps(p);
        if (st.debug) {
            st.debug = false;
            Text.send(p, m.cfg.prefix + " <#9ca3af>Debug modu <#f87171>kapatildi<#9ca3af>.");
        } else {
            st.debug = true;
            Text.send(p, m.cfg.prefix + " <#9ca3af>Debug modu <#4ade80>acildi<#9ca3af>. Yuru ve action bar'i izle.");
        }
    }

    private void sokmagaza(CommandSender s, String[] args) {
        if (s instanceof Player p) {
            Text.send(p, "<#f87171>Bu komut sadece mağaza NPC'si tarafından kullanılabilir.");
            return;
        }
        if (args.length == 0) {
            Text.send(s, "Kullanım: sokmagaza <oyuncu>");
            return;
        }
        Player t = Bukkit.getPlayer(args[0]);
        if (t == null) {
            return;
        }
        // Konsol komutu global thread'de; menu oyuncunun kendi thread'inde acilir
        t.getScheduler().run(m.plugin, task -> {
            m.ps(t).shopPending = PState.NONE;
            if (m.ps(t).shop == null) {
                m.ui.openShop(t);
            }
        }, null);
    }

    private void sokkulesi(CommandSender s, String a) {
        if (!s.hasPermission("admin.sokkulesi")) {
            Text.send(s, NO_PERM);
            return;
        }
        Player player = s instanceof Player p ? p : null;
        String pf = m.cfg.prefix;
        if ("liste".equals(a)) {
            Text.send(s, pf + " <#9ca3af>Kayıtlı kule: <#fbbf24>" + m.store.size());
            int shown = 0;
            for (Tower t : m.store.towers.values()) {
                shown++;
                if (shown > 50) {
                    Text.send(s, "<#4b5563>› <#9ca3af>... ve diğerleri (ilk 50 gösterildi)");
                    return;
                }
                Text.send(s, "<#4b5563>› <#9ca3af>" + t.key + " <#4b5563>· <#9ca3af>claim: <#e5e7eb>" + t.cid + " <#4b5563>· <#9ca3af>lvl: <#e5e7eb>" + t.level);
            }
            return;
        }
        if ("dinleyiciler".equals(a)) {
            TowerListener.listListeners(s, BlockBreakEvent.getHandlerList(), "BlockBreakEvent", pf);
            return;
        }
        if ("hasardinleyici".equals(a)) {
            TowerListener.listListeners(s, EntityDamageByEntityEvent.getHandlerList(), "EntityDamageByEntityEvent", pf);
            return;
        }
        if ("hasartest".equals(a)) {
            if (m.dmgTest) {
                m.dmgTest = false;
                m.dmgTestAt = PState.NONE;
                Text.send(s, pf + " <#9ca3af>Hasar testi <#f87171>kapatıldı<#9ca3af>.");
            } else {
                m.dmgTest = true;
                m.dmgTestAt = TowerModule.now();
                Text.send(s, pf + " <#9ca3af>Hasar testi <#4ade80>açıldı <#4b5563>(5 dk) <#9ca3af>· kayıtlar konsolda <#e5e7eb>[HASAR-TEST]");
            }
            return;
        }
        if ("esya".equals(a)) {
            if (player == null) {
                return;
            }
            ItemStack hi = player.getInventory().getItemInMainHand();
            Text.send(player, pf + " <#9ca3af>Eldeki eşya teşhisi");
            Text.send(player, "<#9ca3af>Tür: <#e5e7eb>" + hi.getType().name().toLowerCase().replace('_', ' '));
            int cmd = TowerItems.cmd(hi);
            Text.send(player, "<#9ca3af>Model verisi: <#e5e7eb>" + (cmd < 0 ? "<none>" : cmd) + " <#4b5563>(250-255 = Sv1-6)");
            Text.send(player, "<#9ca3af>Kalıcı etiket: <#e5e7eb>" + TowerItems.tag(hi) + " <#4b5563>(0 = yok)");
            Text.send(player, "<#9ca3af>Algılanan seviye: <#e5e7eb>" + m.items.level(hi) + " <#4b5563>(0 = kule değil)");
            m.ui.repairInv(player);
            Text.send(player, "<#4ade80>Envanterindeki kuleler kontrol edildi/onarıldı.");
            return;
        }
        if ("bak".equals(a)) {
            if (player != null) {
                bak(player);
            }
            return;
        }
        if ("debug".equals(a)) {
            if (player != null) {
                toggleDebug(player);
            }
            return;
        }
        if ("test".equals(a)) {
            if (player != null) {
                test(player);
            }
            return;
        }
        if ("model".equals(a)) {
            if (player != null) {
                model(player);
            }
            return;
        }
        if ("reload".equals(a)) {
            if (player != null) {
                Text.send(player, pf + " <#f87171>Bu işlemi konsoldan yap: <#e5e7eb>sokkulesi reload");
                return;
            }
            m.store.loadAll();
            Text.console(pf + " <#4ade80>Kuleler yeniden yüklendi. <#9ca3af>(" + m.store.size() + ")");
            return;
        }
        if ("temizle".equals(a)) {
            if (player == null) {
                return;
            }
            int removed = 0;
            for (String k : List.copyOf(m.store.nearby(player.getLocation()))) {
                Tower t = m.store.get(k);
                if (t == null) {
                    continue;
                }
                Location tLoc = t.loc();
                if (Bukkit.isOwnedByCurrentRegion(tLoc) && !m.towerBlockOk(tLoc)) {
                    m.unregister(k);
                    m.clearTop(tLoc);
                    removed++;
                }
            }
            Text.send(player, pf + " <#4ade80>" + removed + " hayalet kule temizlendi.");
            return;
        }

        // Baskasina verme: /sokkulesi ver <oyuncu> [seviye] [adet]  |  /sokkulesi <oyuncu> [seviye] [adet]
        if (a != null) {
            String[] kp = a.split(" ");
            String k1 = kp[0];
            Integer k1n = parseInt(k1);
            String hedef = "";
            String sv = "1";
            String adet = "1";
            if (k1.equalsIgnoreCase("ver") || k1.equalsIgnoreCase("give")) {
                if (kp.length < 2) {
                    Text.send(s, pf + " <#9ca3af>Kullanım: <#e5e7eb>/sokkulesi ver (oyuncu) [seviye 1-6] [adet 1-64]");
                    return;
                }
                hedef = kp[1];
                if (kp.length >= 3) {
                    sv = kp[2];
                }
                if (kp.length >= 4) {
                    adet = kp[3];
                }
            } else if (k1n == null) {
                hedef = k1;
                if (kp.length >= 2) {
                    sv = kp[1];
                }
                if (kp.length >= 3) {
                    adet = kp[2];
                }
            }
            if (!hedef.isEmpty()) {
                give(s, hedef, sv, adet);
                return;
            }
        }
        if (player == null) {
            Text.console(pf + " Kullanım: sokkulesi ver (oyuncu) [seviye 1-6] [adet 1-64]");
            return;
        }
        int lvl = 1;
        if (a != null) {
            Integer parsed = parseInt(a);
            if (parsed != null) {
                lvl = parsed;
            }
        }
        HashMap<Integer, ItemStack> left = player.getInventory().addItem(m.items.item(lvl));
        for (ItemStack it : left.values()) {
            player.getWorld().dropItem(player.getLocation(), it);
        }
        Text.send(player, pf + " <#4ade80>Envanterine 1 adet Şok Kulesi eklendi.");
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Baskasina kule verme: is hedefin kendi thread'inde TEK KEZ calisir. */
    private void give(CommandSender g, String ad, String svMetin, String adetMetin) {
        String pf = m.cfg.prefix;
        Integer lv = parseInt(svMetin);
        if (lv == null) {
            Text.send(g, pf + " <#f87171>Geçersiz seviye: <#e5e7eb>" + svMetin + " <#4b5563>(1-6)");
            return;
        }
        if (lv < 1 || lv > 6) {
            Text.send(g, pf + " <#f87171>Seviye 1 ile 6 arasında olmalı.");
            return;
        }
        Integer adet = parseInt(adetMetin);
        if (adet == null) {
            Text.send(g, pf + " <#f87171>Geçersiz adet: <#e5e7eb>" + adetMetin + " <#4b5563>(1-64)");
            return;
        }
        if (adet < 1 || adet > 64) {
            Text.send(g, pf + " <#f87171>Adet 1 ile 64 arasında olmalı.");
            return;
        }
        Player o = Bukkit.getPlayerExact(ad);
        if (o == null) {
            Text.send(g, pf + " <#f87171>Oyuncu çevrimiçi değil: <#e5e7eb>" + ad);
            return;
        }
        String isim = o.getName();
        String gAd = g.getName();
        int fLv = lv;
        int fAdet = adet;
        var task = o.getScheduler().run(m.plugin, t -> {
            if (!o.isOnline()) {
                Text.send(g, pf + " <#f87171>" + isim + " çıktı, kule verilmedi.");
                return;
            }
            m.ui.repairInv(o);
            ItemStack it = m.items.item(fLv);
            it.setAmount(fAdet);
            HashMap<Integer, ItemStack> kalan = o.getInventory().addItem(it);
            int yere = 0;
            for (ItemStack kv : kalan.values()) {
                yere += kv.getAmount();
                o.getWorld().dropItem(o.getLocation(), kv);
            }
            Text.send(o, pf + " <#4ade80>" + fAdet + " adet Seviye " + fLv + " Şok Kulesi aldın!");
            if (yere > 0) {
                Text.send(o, pf + " <#fbbf24>Envanterin dolu olduğu için " + yere + " adet ayağına düştü.");
            }
            o.playSound(o.getLocation(), "entity.experience_orb.pickup", 1f, 1.2f);
            Text.send(g, pf + " <#4ade80>" + isim + " oyuncusuna " + fAdet + " adet Seviye " + fLv + " Şok Kulesi verildi.");
            Text.console(pf + " [VERME] " + gAd + " -> " + isim + " · Sv" + fLv + " x" + fAdet + " (yere düşen: " + yere + ")");
        }, () -> Text.send(g, pf + " <#f87171>" + isim + " oyundan çıktığı için kule verilmedi."));
        if (task == null) {
            Text.send(g, pf + " <#f87171>" + isim + " şu an ulaşılamıyor (çıkıyor olabilir), kule verilmedi.");
        }
    }

    private void bak(Player p) {
        String pf = m.cfg.prefix;
        Block tb = p.getTargetBlockExact(5);
        if (tb == null) {
            Text.send(p, pf + " <#f87171>5 blok içinde bir kuleye bakarak yaz.");
            return;
        }
        Location tl = tb.getLocation();
        Text.send(p, "<#4b5563>────────────────────");
        Text.send(p, "<#9ca3af>Blok: <#e5e7eb>" + tb.getBlockData().getAsString());
        Text.send(p, "<#9ca3af>Kule durumu: <#e5e7eb>" + m.levelOfState(tb) + " <#4b5563>(-1 tanınmadı · 0 üst blok · 1-6 seviye)");
        Location base = m.baseOf(tl);
        if (base == null) {
            Text.send(p, "<#f87171>Kayıtlı kule DEĞİL ve geri kaydedilemedi (durum tanınmadı ya da burada claim yok).");
            Text.send(p, "<#4b5563>────────────────────");
            return;
        }
        String tk = Tower.keyOf(base);
        Tower t = m.store.get(tk);
        Text.send(p, "<#9ca3af>Anahtar: <#e5e7eb>" + tk + " <#4b5563>· <#9ca3af>Sv <#e5e7eb>" + t.level + " <#4b5563>· <#9ca3af>can <#e5e7eb>" + t.hp);
        Text.send(p, "<#9ca3af>Claim (cid): <#e5e7eb>" + t.cid + " <#4b5563>· <#9ca3af>sahibi: <#e5e7eb>" + m.claims.ownerName(t.cid));
        Text.send(p, "<#9ca3af>Dosya chunk'ı (fcid): <#e5e7eb>" + t.fcid + " <#4b5563>· <#9ca3af>sahipsiz: <#e5e7eb>"
                + (t.orphan ? "true" : "<none>") + " <#4b5563>· <#9ca3af>claim koptu: <#e5e7eb>" + m.claimGone(t));
        boolean inIdx = TowerStore.idx(m.store.byChunk, Tower.chunkId(base)).contains(tk);
        Text.send(p, "<#9ca3af>Ana listede: <#e5e7eb>true <#4b5563>· <#9ca3af>konum indeksinde: <#e5e7eb>" + inIdx);
        File df = new File(m.cfg.dataDir, t.fcid + ".yml");
        boolean inFile = false;
        if (df.exists()) {
            YamlConfiguration dy = YamlConfiguration.loadConfiguration(df);
            inFile = dy.contains("shock_towers." + tk.replace(".", "~"));
        }
        Text.send(p, "<#9ca3af>Diskte kayıtlı: <#e5e7eb>" + inFile + " <#4b5563>(" + m.cfg.dataDir + "/" + t.fcid + ".yml)");
        Text.send(p, "<#9ca3af>Sana düşman: <#e5e7eb>" + m.isHostile(p, tk) + " <#4b5563>· <#9ca3af>bypass izni: <#e5e7eb>"
                + m.bypass(p) + " <#4b5563>· <#9ca3af>OP: <#e5e7eb>" + p.isOp() + " <#4b5563>· <#9ca3af>mod: <#e5e7eb>"
                + p.getGameMode().name().toLowerCase());
        Text.send(p, "<#4b5563>(Sahibi/üyesi olduğun, OP ya da " + m.cfg.bypassPermission + " iznin olan kule sana vurmaz. LuckPerms'te * yetkisi bypass iznini de verir.)");
        Text.send(p, "<#4b5563>────────────────────");
    }

    private void test(Player p) {
        Location pLoc = p.getLocation();
        String cid = Tower.chunkId(pLoc);
        Text.send(p, "<#4b5563>────────────────────");
        Text.send(p, "<#9ca3af>Chunk ID: <#e5e7eb>" + cid);
        String owner = m.claims.owner(cid);
        if (owner != null) {
            Text.send(p, "<#9ca3af>Claim sahibi: <#e5e7eb>" + owner);
        } else {
            Text.send(p, "<#f87171>Burada claim YOK -> kule hiç çalışmaz.");
        }
        String mainCid = m.claims.mainCidRaw(cid);
        if (mainCid == null) {
            mainCid = cid;
        }
        Text.send(p, "<#9ca3af>Main CID: <#e5e7eb>" + mainCid);
        Text.send(p, "<#9ca3af>Bu claimdeki kule: <#e5e7eb>" + m.store.byCid(mainCid).size());
        Text.send(p, "<#9ca3af>Toplam kayıtlı kule: <#e5e7eb>" + m.store.size());
        Text.send(p, "<#9ca3af>Senin UUID: <#e5e7eb>" + p.getUniqueId());
        Text.send(p, "<#9ca3af>Bypass yetkin (" + m.cfg.bypassPermission + "): <#e5e7eb>" + m.bypass(p));
        if (m.claims.isRaidActive()) {
            Text.send(p, "<#9ca3af>Baskın durumu: <#4ade80>AKTİF <#4b5563>(kule kırılabilir)");
        } else {
            Text.send(p, "<#9ca3af>Baskın durumu: <#f87171>KAPALI <#4b5563>(kule korumalı)");
        }
        String fr = m.claims.forceRaid();
        if (fr != null) {
            Text.send(p, "<#9ca3af>Zorla ayar: <#fbbf24>" + fr + " <#4b5563>(/claimadmin baskın oto ile otomatiğe al)");
        }
        Text.send(p, "<#9ca3af>Sunucu saati: <#e5e7eb>" + new SimpleDateFormat("HH:mm").format(new Date()));
        for (String k : new ArrayList<>(m.store.byCid(mainCid))) {
            Tower t = m.store.get(k);
            if (t == null) {
                continue;
            }
            Text.send(p, "<#4b5563>› <#9ca3af>" + k + " <#4b5563>· <#9ca3af>lvl <#e5e7eb>" + t.level + " <#4b5563>· <#9ca3af>mesafe <#e5e7eb>"
                    + (t.world == pLoc.getWorld() ? Text.num(t.loc().distance(pLoc)) : "<none>")
                    + " <#4b5563>· <#9ca3af>sana düşman? <#e5e7eb>" + m.isHostile(p, k) + " <#4b5563>· <#9ca3af>sahipsiz? <#e5e7eb>" + t.orphan);
        }
        Text.send(p, "<#4b5563>────────────────────");
    }

    private void model(Player p) {
        String cid = Tower.chunkId(p.getLocation());
        String mainCid = m.claims.mainCidRaw(cid);
        if (mainCid == null) {
            mainCid = cid;
        }
        int fixed = 0;
        int skipped = 0;
        for (String k : new ArrayList<>(m.store.byCid(mainCid))) {
            Tower t = m.store.get(k);
            if (t == null) {
                continue;
            }
            Location tLoc = t.loc();
            if (Bukkit.isOwnedByCurrentRegion(tLoc)) {
                if (m.towerBlockOk(tLoc)) {
                    m.applyModel(tLoc, t.level);
                    fixed++;
                }
            } else {
                skipped++;
            }
        }
        Text.send(p, m.cfg.prefix + " <#4ade80>" + fixed + " kulenin görünümü yenilendi.");
        if (skipped > 0) {
            Text.send(p, m.cfg.prefix + " <#9ca3af>" + skipped + " kule uzakta, yanına gidip tekrar dene.");
        }
    }
}
