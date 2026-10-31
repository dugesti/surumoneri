package tr.dugecraft.blok;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tr.dugecraft.util.Text;

import java.util.List;

/** bloksinir.sk komutlari: /bloklag /chunkhesapla /chunklimit /chunklimittemizle /chunklimitkaydet */
public final class BlokCommands implements CommandExecutor {

    /** Skript'in varsayilan yetki / oyuncu mesajlari. */
    static final String NO_PERM = "&cYou don't have the required permission to use this command";
    static final String PLAYERS_ONLY = "&cThis command can only be used by players";
    private static final String LINE = "&8&m                                        ";

    private final BlokModule m;

    public BlokCommands(BlokModule m) {
        this.m = m;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "bloklag" -> bloklag(s);
            case "chunkhesapla" -> chunkhesapla(s);
            case "chunklimit" -> chunklimit(s);
            case "chunklimittemizle" -> temizle(s);
            case "chunklimitkaydet" -> kaydet(s);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void bloklag(CommandSender s) {
        if (!s.hasPermission(m.cfg.warnPerm)) {
            Text.send(s, NO_PERM);
            return;
        }
        var st = m.lag.stats;
        Object scanms = st.get("scanms");
        if (scanms == null) {
            Text.send(s, m.cfg.prefix + " &7Henüz ölçüm yok, ilk özet 1 dakika içinde hazır olur.");
            return;
        }
        Object scans = st.get("scans");
        Object drops = st.get("drops");
        Object direct = st.get("direct");
        Object spikes = st.get("spikes");
        double cpu = ((Number) st.get("cpu")).doubleValue();
        double tps = ((Number) st.get("tps")).doubleValue();
        Object worst = st.get("worst");
        Object save = m.lag.info.get("save");
        Object who = m.lag.info.get("tpswho");

        Text.send(s, LINE);
        Text.send(s, "  " + m.cfg.prefix + " &fbloksinir Yük Durumu &8(son 1 dk)");
        Text.send(s, "");
        Text.send(s, "  &7Tarama süresi &8· &f" + scanms + " ms &7/ " + m.cfg.warnScriptMs + " &8(" + scans + " tarama)");
        Text.send(s, "  &7Yavaş kontrol &8· &f" + spikes + " &8(> " + m.cfg.warnSpikeMs + " ms)");
        if (worst != null) {
            Text.send(s, "  &7En kötüsü &8· &f" + worst);
        }
        Text.send(s, "  &7Yere düşen ürün &8· &f" + drops + " &7/ " + m.cfg.warnDrops);
        if (direct != null && ((Number) direct).longValue() > 0) {
            Text.send(s, "  &7Çiftçiye giden ürün &8· &f" + direct + " &8(yere düşmeden)");
        }
        if (save != null) {
            Text.send(s, "  &7Son kayıt &8· &f" + save + " ms");
        }
        Text.send(s, "");
        if (tps >= 0) {
            Text.send(s, "  &7En düşük TPS &8· &f" + Text.num(tps) + " &8(" + who + ")");
        } else {
            Text.send(s, "  &7En düşük TPS &8· &7oyuncu yok");
        }
        if (cpu >= 0) {
            Text.send(s, "  &7Java CPU &8· &f%" + Text.num(cpu));
        }
        Text.send(s, LINE);
    }

    private void chunkhesapla(CommandSender s) {
        if (!s.hasPermission("op")) {
            Text.send(s, NO_PERM);
            return;
        }
        if (!(s instanceof Player p)) {
            Text.send(s, PLAYERS_ONLY);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = m.fullScanCool.get(p.getUniqueId());
        if (last != null && now - last < 10_000) {
            Text.send(p, m.cfg.prefix + " &cTam tarama ağırdır, biraz bekle.");
            return;
        }
        m.fullScanCool.put(p.getUniqueId(), now);

        // Folia: oyuncu komutu oyuncunun kendi bolge thread'inde calisir;
        // taranan chunk oyuncunun durdugu chunk.
        Block b = p.getLocation().getBlock();
        World w = p.getWorld();
        int cx = Math.floorDiv(b.getX(), 16);
        int cz = Math.floorDiv(b.getZ(), 16);
        String cid = BlokModule.cidOf(b);
        int min = Math.floorDiv(w.getMinHeight(), 16);
        int max = Math.floorDiv(w.getMaxHeight() - 1, 16);

        Text.send(p, m.cfg.prefix + " &eChunk tamamen taranıyor &7(kısa bir takılma olabilir)...");
        long t0 = System.nanoTime();
        for (int sy = min; sy <= max; sy++) {
            m.scanSection(w, cx, cz, sy, "");
        }
        long ms = Math.round((System.nanoTime() - t0) / 1_000_000.0);
        Text.send(p, m.cfg.prefix + " &7Tarama &f" + ms + " ms &7sürdü.");
        for (String t : LimitStore.TYPES) {
            int lv = m.store.blGet(cid + ";L;" + t);
            m.store.addCount(cid, "L", t, -lv);
        }

        Text.send(p, LINE);
        Text.send(p, "  " + m.cfg.prefix + " &fChunk Sayımı &8(&7tam tarama&8)");
        Text.send(p, "");
        Text.send(p, "  &7Piston &8· &f" + m.store.totGet(cid + ";piston") + "&7/&f" + m.cfg.limitPiston);
        Text.send(p, "  &7Kaktüs &8· &f" + m.store.totGet(cid + ";cactus") + "&7/&f" + m.cfg.limitCactus);
        Text.send(p, "  &7Yineleyici &8· &f" + m.store.totGet(cid + ";repeater") + "&7/&f" + m.cfg.limitRepeater);
        Text.send(p, "  &7Karşılaştırıcı &8· &f" + m.store.totGet(cid + ";comparator") + "&7/&f" + m.cfg.limitComparator);
        Text.send(p, "  &7Bambu &8· &f" + m.store.totGet(cid + ";bamboo") + "&7/&f" + m.cfg.limitBamboo);
        Text.send(p, "  &7Su yosunu &8· &f" + m.store.totGet(cid + ";kelp") + "&7/&f" + m.cfg.limitKelp);
        Text.send(p, LINE);
    }

    private void chunklimit(CommandSender s) {
        if (!s.hasPermission("op")) {
            Text.send(s, NO_PERM);
            return;
        }
        if (!(s instanceof Player p)) {
            Text.send(s, PLAYERS_ONLY);
            return;
        }
        String cid = BlokModule.cidOf(p.getLocation().getBlock());
        Text.send(p, LINE);
        Text.send(p, "  " + m.cfg.prefix + " &fKayıtlı Sayaçlar");
        Text.send(p, "  &8" + cid);
        Text.send(p, "");
        for (String t : List.of(LimitStore.TYPES)) {
            int v = m.store.totGet(cid + ";" + t);
            int l = m.store.blGet(cid + ";L;" + t);
            if (l > 0) {
                Text.send(p, "  &7" + BlokConfig.name(t) + " &8· &f" + v + " &8(eski veri: " + l + ")");
            } else {
                Text.send(p, "  &7" + BlokConfig.name(t) + " &8· &f" + v);
            }
        }
        Text.send(p, LINE);
    }

    private void temizle(CommandSender s) {
        if (!s.hasPermission("op")) {
            Text.send(s, NO_PERM);
            return;
        }
        m.store.seen.clear();
        m.store.scan.clear();
        Text.send(s, m.cfg.prefix + " &aTarama önbelleği temizlendi. &7Dilimler ilk yerleştirmede yeniden sayılacak.");
    }

    private void kaydet(CommandSender s) {
        if (!s.hasPermission("op")) {
            Text.send(s, NO_PERM);
            return;
        }
        m.store.markDirty();
        if (m.store.save()) {
            Text.send(s, m.cfg.prefix + " &aChunk limit verileri diske kaydedildi.");
        } else {
            Text.send(s, m.cfg.prefix + " &cKayıt şu an yapılamadı (başka kayıt sürüyor veya disk hatası). &7Konsolu kontrol et, otomatik tekrar denenecek.");
        }
    }
}
