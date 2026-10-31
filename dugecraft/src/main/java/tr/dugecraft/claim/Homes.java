package tr.dugecraft.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tr.dugecraft.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * claim.sk 8. SETHOME VE ISINLANMA: plugins/Skript/yaml/homes/<uuid>.yml.
 * Skript degisken adlari buyuk/kucuk harf duyarsiz oldugu icin ev adlari da
 * oyle karsilastirilir (ilk yazilisi korunur).
 */
final class Homes implements CommandExecutor {

    static final String DIR = "plugins/Skript/yaml/homes";

    static final class Home {
        String name;
        String cid;
        Location loc;
        // dunyasi yuklu olmayan ev: ham veri aynen geri yazilir
        String rw;
        double rx;
        double ry;
        double rz;
        double ryaw;
        double rpitch;
    }

    private final ClaimModule m;
    /** uuid -> (kucuk harf ad -> ev), ekleme sirasi korunur */
    final ConcurrentHashMap<String, LinkedHashMap<String, Home>> homes = new ConcurrentHashMap<>();

    Homes(ClaimModule m) {
        this.m = m;
    }

    private static String key(String n) {
        return n.toLowerCase(Locale.ROOT);
    }

    int limit(Player p) {
        if (p.hasPermission("group.dvip+")) {
            return 5;
        }
        if (p.hasPermission("group.dvip")) {
            return 4;
        }
        if (p.hasPermission("group.vip")) {
            return 3;
        }
        return 2;
    }

    synchronized void save(String u) {
        try {
            File file = new File(DIR, u + ".yml");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            y.set("homes", null);
            LinkedHashMap<String, Home> map = homes.get(u);
            if (map != null) {
                for (Home h : map.values()) {
                    String p = "homes." + h.name;
                    y.set(p + ".cid", h.cid);
                    if (h.loc != null) {
                        y.set(p + ".w", h.loc.getWorld().getName());
                        y.set(p + ".x", h.loc.getX());
                        y.set(p + ".y", h.loc.getY());
                        y.set(p + ".z", h.loc.getZ());
                        y.set(p + ".yaw", h.loc.getYaw());
                        y.set(p + ".pitch", h.loc.getPitch());
                    } else if (h.rw != null) {
                        y.set(p + ".w", h.rw);
                        y.set(p + ".x", h.rx);
                        y.set(p + ".y", h.ry);
                        y.set(p + ".z", h.rz);
                        y.set(p + ".yaw", h.ryaw);
                        y.set(p + ".pitch", h.rpitch);
                    }
                }
            }
            File tmp = new File(DIR, u + ".yml." + Thread.currentThread().getId() + ".tmp");
            y.save(tmp);
            if (!ClaimStore.replaceFile(tmp, file)) {
                Text.console(m.cfg.prefix + " &cEv dosyası yazılamadı (kilitli olabilir): &f" + u + ".yml");
            }
        } catch (IOException | RuntimeException e) {
            Text.console(m.cfg.prefix + " &cEv dosyası yazılamadı (kilitli olabilir): &f" + u + ".yml");
        }
    }

    void loadAll() {
        File dir = new File(DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        m.store.recoverTmp(DIR, ".tmp");
        homes.clear();
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String fn = f.getName();
            if (!fn.endsWith(".yml")) {
                continue;
            }
            String u = fn.replace(".yml", "");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection sec = y.getConfigurationSection("homes");
            if (sec == null) {
                continue;
            }
            LinkedHashMap<String, Home> map = new LinkedHashMap<>();
            for (String hName : sec.getKeys(false)) {
                String p = "homes." + hName;
                Home h = new Home();
                h.name = hName;
                h.cid = y.getString(p + ".cid");
                String w = y.getString(p + ".w");
                double x = y.getDouble(p + ".x");
                double yy = y.getDouble(p + ".y");
                double z = y.getDouble(p + ".z");
                double yaw = y.getDouble(p + ".yaw");
                double pitch = y.getDouble(p + ".pitch");
                World world = w == null ? null : Bukkit.getWorld(w);
                if (world != null) {
                    h.loc = new Location(world, x, yy, z, (float) yaw, (float) pitch);
                } else {
                    h.rw = w;
                    h.rx = x;
                    h.ry = yy;
                    h.rz = z;
                    h.ryaw = yaw;
                    h.rpitch = pitch;
                }
                map.putIfAbsent(key(hName), h);
            }
            homes.put(u, map);
        }
    }

    boolean valid(Player p, Home h) {
        if (h == null || h.cid == null) {
            return false;
        }
        Claim c = m.store.get(h.cid);
        if (c == null || c.mainCid == null) {
            return false;
        }
        String u = ClaimModule.uuid(p);
        Claim mc = m.store.get(c.mainCid);
        if (mc == null) {
            return false;
        }
        if (u.equals(mc.owner)) {
            return true;
        }
        return mc.members.contains(u) && mc.perm(u, "sethome");
    }

    /** Gecersiz evleri temizler; temizlendiyse true. */
    private boolean clean(Player p, LinkedHashMap<String, Home> map) {
        boolean cleaned = false;
        for (Map.Entry<String, Home> e : new ArrayList<>(map.entrySet())) {
            if (!valid(p, e.getValue())) {
                map.remove(e.getKey());
                cleaned = true;
            }
        }
        return cleaned;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(s instanceof Player p)) {
            Text.send(s, "&cThis command can only be used by players");
            return true;
        }
        String arg = args.length == 0 ? null : String.join(" ", args);
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> setHome(p, arg);
            case "home" -> home(p, arg);
            case "delhome" -> delHome(p, arg);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void setHome(Player p, String name) {
        if (name == null) {
            m.pre(p, "&7Kullanım: &e/sethome <isim>");
            return;
        }
        if (name.length() > 16) {
            m.pre(p, "&cEv ismi en fazla 16 karakter olabilir!");
            return;
        }
        if (!name.matches("[A-Za-z0-9_-]+")) {
            m.pre(p, "&cEv isminde sadece harf, rakam, _ ve - kullanılabilir.");
            return;
        }
        String u = ClaimModule.uuid(p);
        LinkedHashMap<String, Home> map = homes.computeIfAbsent(u, k -> new LinkedHashMap<>());
        synchronized (map) {
            if (!map.containsKey(key(name))) {
                if (clean(p, map)) {
                    save(u);
                }
                int lim = limit(p);
                if (map.size() >= lim) {
                    m.pre(p, "&cMaksimum ev sınırına ulaştınız! &8(&e" + lim + "&8)");
                    return;
                }
            }
        }
        Location loc = p.getLocation();
        String cid = ClaimModule.chunkId(loc);
        Claim c = m.store.get(cid);
        if (c == null || c.owner == null) {
            m.pre(p, "&cEv belirlemek için bir claim içinde olmalısınız!");
            return;
        }
        String mainCid = c.mainCid;
        Claim mc = mainCid == null ? null : m.store.get(mainCid);
        boolean can = mc != null && (u.equals(mc.owner) || (mc.members.contains(u) && mc.perm(u, "sethome")));
        if (!can) {
            m.pre(p, "&cBu claimde sethome yetkiniz bulunmuyor!");
            return;
        }
        synchronized (map) {
            Home h = map.get(key(name));
            if (h == null) {
                h = new Home();
                h.name = name;
                map.put(key(name), h);
            }
            h.loc = loc;
            h.rw = null;
            h.cid = mainCid;
        }
        save(u);
        ClaimModule.sound(p, "entity.player.levelup", 1, 1);
        m.pre(p, "&aEv başarıyla kaydedildi: &e" + name);
    }

    private void home(Player p, String name) {
        String u = ClaimModule.uuid(p);
        LinkedHashMap<String, Home> map = homes.get(u);
        if (name == null) {
            if (map == null || map.isEmpty()) {
                m.pre(p, "&cHiç kayıtlı eviniz yok.");
                return;
            }
            List<String> list = new ArrayList<>();
            boolean cleaned;
            synchronized (map) {
                cleaned = clean(p, map);
                for (Home h : map.values()) {
                    list.add("&e" + h.name);
                }
            }
            if (cleaned) {
                save(u);
            }
            if (list.isEmpty()) {
                m.pre(p, "&cMevcut evlerinizin bulunduğu claimler silindiği veya kovulduğunuz için evleriniz kaldırıldı!");
            } else {
                m.pre(p, "&7Kayıtlı Evleriniz: " + String.join(", ", list));
            }
            return;
        }
        Home h = map == null ? null : map.get(key(name));
        if (h == null) {
            m.pre(p, "&cBöyle bir eviniz bulunmuyor!");
            return;
        }
        if (!valid(p, h)) {
            synchronized (map) {
                map.remove(key(name));
            }
            save(u);
            m.pre(p, "&cBu evinizin bulunduğu claim silindiği veya yetkiniz alındığı için ev kaldırıldı!");
            ClaimModule.sound(p, "entity.villager.no", 1, 1);
            return;
        }
        Location tp = h.loc;
        if (tp == null) {
            m.pre(p, "&cBu evin bulunduğu dünya şu an yüklü değil.");
            return;
        }
        if (m.inSokCombat(p)) {
            m.pre(p, "&cSavaş modundayken ışınlanamazsın.");
            return;
        }
        p.closeInventory();
        m.pre(p, "<#f6d365>5 saniye içinde evinize ışınlanacaksınız. Lütfen hareket etmeyin!");
        ClaimModule.sound(p, "block.note_block.pling", 1, 1);
        Location start = p.getLocation();
        AtomicInteger step = new AtomicInteger();
        p.getScheduler().runAtFixedRate(m.plugin, task -> {
            int st = step.getAndIncrement();
            if (st % 10 == 0 && st < 50) {
                ClaimActions.title(p, "<#f6d365>Işınlanıyorsun", "&e" + (5 - st / 10) + " saniye kaldı... (Hareket etmeyin)", 1);
            }
            if (st > 0) {
                if (!p.isOnline() || p.isDead() || !p.getWorld().equals(start.getWorld())) {
                    task.cancel();
                    return;
                }
                if (p.getLocation().distance(start) > 0.4) {
                    ClaimActions.title(p, "<#ff6b6b>Işınlanma iptal edildi", "&cHareket ettiğiniz için ışınlanma iptal edildi!", 2);
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
            if (!valid(p, h)) {
                m.pre(p, "&cBu ev artık geçerli değil, ışınlanma iptal edildi.");
                return;
            }
            if (m.inSokCombat(p)) {
                m.pre(p, "&cSavaş modundayken ışınlanamazsın.");
                return;
            }
            m.session(p).tpFx = true;
            p.teleportAsync(tp);
            ClaimModule.sound(p, "entity.enderman.teleport", 1, 1);
            ClaimActions.title(p, "<#a8ff78>Işınlandın", "&7Evinize başarıyla ulaştınız.", 2);
            m.pre(p, "<#a8ff78>Evinize başarıyla ışınlandınız.");
        }, null, 1L, 2L);
    }

    private void delHome(Player p, String name) {
        if (name == null) {
            m.pre(p, "&7Kullanım: &e/delhome <isim>");
            return;
        }
        String u = ClaimModule.uuid(p);
        LinkedHashMap<String, Home> map = homes.get(u);
        boolean removed;
        if (map == null) {
            removed = false;
        } else {
            synchronized (map) {
                removed = map.remove(key(name)) != null;
            }
        }
        if (removed) {
            save(u);
            m.pre(p, "&aEv başarıyla silindi: &e" + name);
        } else {
            m.pre(p, "&cBöyle bir eviniz bulunmuyor!");
        }
    }
}
