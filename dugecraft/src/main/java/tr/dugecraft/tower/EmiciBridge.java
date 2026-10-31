package tr.dugecraft.tower;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

/**
 * buyulutas.sk (Skript'te kalir) emiciReduction(p) koprusu.
 * Once Skript fonksiyonunun kendisi cagrilir (buyulutas.sk'de yapilan her
 * degisiklik aynen gecerli olsun). Skript fonksiyon API'si bulunamazsa
 * buyulutas.sk v2.12'deki formulun birebir Java karsiligi kullanilir.
 */
final class EmiciBridge {

    private final Logger log;
    private Method getFunction;
    private Method execute;
    private volatile Object function;
    private volatile boolean warned;

    EmiciBridge(Logger log) {
        this.log = log;
        if (Bukkit.getPluginManager().getPlugin("Skript") == null) {
            return;
        }
        try {
            Class<?> fns = Class.forName("ch.njol.skript.lang.function.Functions");
            getFunction = fns.getMethod("getGlobalFunction", String.class);
            Class<?> fn = Class.forName("ch.njol.skript.lang.function.Function");
            execute = fn.getMethod("execute", Object[][].class);
        } catch (ReflectiveOperationException | LinkageError e) {
            getFunction = null;
            log.warning("Skript fonksiyon API'si bulunamadi; emiciReduction icin Java formulu kullanilacak.");
        }
    }

    /** 0.0 - 1.0 */
    double reduction(Player p) {
        if (getFunction != null) {
            try {
                Object f = function;
                if (f == null) {
                    f = getFunction.invoke(null, "emiciReduction");
                    function = f;
                }
                if (f != null) {
                    Object[] r = (Object[]) execute.invoke(f, (Object) new Object[][]{{p}});
                    if (r != null && r.length > 0 && r[0] instanceof Number n) {
                        return n.doubleValue();
                    }
                    return 0;
                }
                if (!warned) {
                    warned = true;
                    log.warning("buyulutas.sk emiciReduction fonksiyonu bulunamadi; Java formulu kullaniliyor.");
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Skript yeniden yukleniyor olabilir: sonraki cagrida tekrar aranir
                function = null;
            }
        }
        return javaReduction(p);
    }

    // ---- buyulutas.sk v2.12 formulu (emici_linear true, per_point 0.75, max 75) ----

    static double javaReduction(Player p) {
        PlayerInventory inv = p.getInventory();
        int sum = emici(inv.getHelmet()) + emici(inv.getChestplate()) + emici(inv.getLeggings()) + emici(inv.getBoots());
        if (sum <= 0) {
            return 0;
        }
        double eff = Math.min(sum * 0.75, 75);
        return Math.max(0, eff) / 100.0;
    }

    /** Lore'da "✦ Emici" ile BASLAYAN satirdaki sayi, en fazla 40. */
    @SuppressWarnings("deprecation")
    static int emici(ItemStack i) {
        if (i == null || !i.hasItemMeta()) {
            return 0;
        }
        ItemMeta m = i.getItemMeta();
        if (!m.hasLore()) {
            return 0;
        }
        List<String> lore = m.getLore();
        for (String l : lore) {
            String line = ChatColor.stripColor(l);
            if (line.startsWith("✦ Emici")) {
                String d = line.replaceAll("[^0-9]", "");
                if (d.isEmpty()) {
                    continue;
                }
                try {
                    return Math.min(40, Integer.parseInt(d));
                } catch (NumberFormatException e) {
                    return 40;
                }
            }
        }
        return 0;
    }
}
