package tr.dugecraft.tower;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tr.dugecraft.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Kule esyasi (not blogu). Seviye: custom model data 250-255 + kalici etiket
 * dugecraft:sok_kule = "1".."6". Ad/aciklama Skript'teki gibi legacy (§) metin.
 */
@SuppressWarnings("deprecation")
public final class TowerItems {

    static final NamespacedKey KEY = NamespacedKey.fromString("dugecraft:sok_kule");

    private final TowerConfig cfg;
    private final String[] stdName = new String[7];
    private final List<List<String>> stdLore = new ArrayList<>();
    private final String[] plainName = new String[7];

    TowerItems(TowerConfig cfg) {
        this.cfg = cfg;
        stdLore.add(List.of());
        for (int lv = 1; lv <= 6; lv++) {
            stdName[lv] = legacy("<#5ac8fa>⚡ <#e5e7eb>Şok Kulesi <#4b5563>· <#9ca3af>Seviye " + lv);
            stdLore.add(List.of(
                    legacy("<#4b5563>────────────────────"),
                    legacy("<#9ca3af>Hasar        <#e5e7eb>" + Text.num(cfg.hearts(lv)) + " <#9ca3af>kalp"),
                    legacy("<#9ca3af>Mesafe       <#e5e7eb>" + Text.num(cfg.range(lv)) + " <#9ca3af>blok"),
                    legacy("<#9ca3af>Duvar arkası " + cfg.wallText(lv)),
                    legacy("<#4b5563>────────────────────"),
                    legacy("<#9ca3af>Claim içine yerleştirilir,"),
                    legacy("<#9ca3af>sağ tıkla yükseltilir.")));
            plainName[lv] = ChatColor.stripColor(stdName[lv]);
        }
    }

    /** Skript metni (<#hex>, &kod) -> legacy § metin (esya adi/aciklamasi icin). */
    public static String legacy(String s) {
        java.util.regex.Matcher mt = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (mt.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char h : mt.group(1).toLowerCase().toCharArray()) {
                r.append('§').append(h);
            }
            mt.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(r.toString()));
        }
        mt.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private static final java.util.regex.Pattern HEX = java.util.regex.Pattern.compile("<#([0-9a-fA-F]{6})>");

    static int clamp(int level) {
        return Math.max(1, Math.min(6, level));
    }

    /** sok.sk towerItem(level). */
    public ItemStack item(int level) {
        int lv = clamp(level);
        ItemStack it = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(stdName[lv]);
        m.setLore(new ArrayList<>(stdLore.get(lv)));
        m.setCustomModelData(249 + lv);
        m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, Integer.toString(lv));
        it.setItemMeta(m);
        return it;
    }

    /** Ayni esya, ozel ad/aciklama ile (menu vitrini, magaza secenekleri). */
    public ItemStack item(int level, String name, String... lore) {
        ItemStack it = item(level);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(legacy(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) {
            l.add(legacy(s));
        }
        m.setLore(l);
        it.setItemMeta(m);
        return it;
    }

    static int cmd(ItemStack i) {
        if (i == null || !i.hasItemMeta()) {
            return -1;
        }
        ItemMeta m = i.getItemMeta();
        return m.hasCustomModelData() ? m.getCustomModelData() : -1;
    }

    /** Esyadaki seviye etiketi (yoksa / bozuksa 0). */
    public static int tag(ItemStack i) {
        if (i == null || !i.hasItemMeta()) {
            return 0;
        }
        String v = i.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (v == null) {
            return 0;
        }
        try {
            int n = Integer.parseInt(v.trim());
            return n >= 1 && n <= 6 ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Kule esyasi degilse 0. Once model verisi, sonra etiket / renkli ad + aciklama. */
    public int level(ItemStack i) {
        if (i == null) {
            return 0;
        }
        int c = cmd(i);
        if (c >= 250 && c <= 255) {
            return c - 249;
        }
        return lostLevel(i);
    }

    private int lostLevel(ItemStack i) {
        if (i.getType() != Material.NOTE_BLOCK || !i.hasItemMeta()) {
            return 0;
        }
        int t = tag(i);
        if (t > 0) {
            return t;
        }
        if (!cfg.recoverByName) {
            return 0;
        }
        ItemMeta m = i.getItemMeta();
        String nm = m.hasDisplayName() ? m.getDisplayName() : "<none>";
        String un = ChatColor.stripColor(nm);
        // Renksiz ad = orste yazilmis -> kule DEGIL (sahte kule engeli)
        if (un.equals(nm)) {
            return 0;
        }
        String lr = m.hasLore() ? ChatColor.stripColor(String.join(", ", m.getLore())) : "";
        if (!lr.contains("sağ tıkla yükseltilir") || !lr.contains("Claim içine yerleştirilir")) {
            return 0;
        }
        for (int k = 1; k <= 6; k++) {
            if (un.equals(plainName[k])) {
                return k;
            }
        }
        return 0;
    }

    static boolean cmdOk(ItemStack i, int lv) {
        return cmd(i) == 249 + lv;
    }

    /**
     * Bozuk / etiketsiz / eski aciklamali kule yiginini onarir. Gerekmiyorsa null.
     * ADET HER ZAMAN AYNI KALIR (dupe yok). Seviye esyanin kendisinden gelir.
     */
    public ItemStack repair(ItemStack i) {
        int lv = level(i);
        if (lv <= 0) {
            return null;
        }
        if (cmdOk(i, lv)) {
            if (tag(i) != lv) {
                ItemStack c = i.clone();
                ItemMeta m = c.getItemMeta();
                m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, Integer.toString(lv));
                c.setItemMeta(m);
                return c;
            }
            // Eski hasar/menzil yazan aciklama guncellenir. Sadece standart
            // adli kulede (orste isim verilmisse dokunulmaz).
            ItemMeta m = i.getItemMeta();
            String nm = m.hasDisplayName() ? m.getDisplayName() : "";
            List<String> lore = m.hasLore() ? m.getLore() : List.of();
            boolean stdNameNow = nm.equals(stdName[lv]) || ChatColor.stripColor(nm).equals(plainName[lv]) && !ChatColor.stripColor(nm).equals(nm);
            if (stdNameNow && (!lore.equals(stdLore.get(lv)) || !nm.equals(stdName[lv]))) {
                ItemStack r = item(lv);
                r.setAmount(i.getAmount());
                return r;
            }
            return null;
        }
        ItemStack r = item(lv);
        r.setAmount(i.getAmount());
        return r;
    }

    public List<String> stdLorePlain(int lv) {
        List<String> out = new ArrayList<>();
        for (String s : stdLore.get(clamp(lv))) {
            out.add(ChatColor.stripColor(s));
        }
        return out;
    }

    static List<String> list(String... s) {
        return Arrays.asList(s);
    }
}
