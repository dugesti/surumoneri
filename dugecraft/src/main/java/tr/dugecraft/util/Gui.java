package tr.dugecraft.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skript menu kaliplari: "chest inventory with N rows named X",
 * "<esya> named X with lore A, B, C". Ad ve aciklama legacy § metin
 * (Skript'in kullandigi yol) olarak yazilir.
 */
@SuppressWarnings("deprecation")
public final class Gui {

    private static final Pattern HEX = Pattern.compile("<#([0-9a-fA-F]{6})>");

    private Gui() {
    }

    /** Skript metni (<#hex>, &kod) -> legacy § metin. */
    public static String legacy(String s) {
        if (s == null) {
            return null;
        }
        Matcher mt = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (mt.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char h : mt.group(1).toLowerCase().toCharArray()) {
                r.append('§').append(h);
            }
            mt.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        mt.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static Inventory chest(int rows, String title) {
        return Bukkit.createInventory(null, rows * 9, legacy(title));
    }

    public static ItemStack item(Material m, String name, String... lore) {
        return item(new ItemStack(m), name, List.of(lore));
    }

    public static ItemStack item(Material m, String name, Collection<String> lore) {
        return item(new ItemStack(m), name, lore);
    }

    /** Var olan esyanin kopyasina ad ve aciklama yazar ("X named Y with lore Z"). */
    public static ItemStack item(ItemStack base, String name, Collection<String> lore) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) {
            return it;
        }
        if (name != null) {
            meta.setDisplayName(legacy(name));
        }
        if (lore != null && !lore.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (String s : lore) {
                l.add(legacy(s));
            }
            meta.setLore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack pane(Material m) {
        return item(m, "&f");
    }

    public static void fill(Inventory inv, Material m, int from, int to) {
        ItemStack p = pane(m);
        for (int i = from; i <= to && i < inv.getSize(); i++) {
            inv.setItem(i, p.clone());
        }
    }

    /** Skript "colored" + tek satir metin (hologram, mesaj) icin. */
    public static String plain(String legacyText) {
        return ChatColor.stripColor(legacy(legacyText));
    }
}
