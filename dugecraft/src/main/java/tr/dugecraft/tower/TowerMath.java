package tr.dugecraft.tower;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

/**
 * sok.sk ZIRH HESABI (v11.42) ve P5 USTU KORUMA (v11.62). Sadece oyuncunun
 * kendi esyasi / attribute'u okunur -> oyuncunun bolge thread'inde cagrilir.
 */
final class TowerMath {

    private static final Map<String, Integer> ARMOR_PTS = new HashMap<>();
    private static final Map<String, Integer> ARMOR_TGH = new HashMap<>();

    static {
        String[] ap = {"LEATHER_HELMET:1", "LEATHER_CHESTPLATE:3", "LEATHER_LEGGINGS:2", "LEATHER_BOOTS:1",
                "CHAINMAIL_HELMET:2", "CHAINMAIL_CHESTPLATE:5", "CHAINMAIL_LEGGINGS:4", "CHAINMAIL_BOOTS:1",
                "GOLDEN_HELMET:2", "GOLDEN_CHESTPLATE:5", "GOLDEN_LEGGINGS:3", "GOLDEN_BOOTS:1",
                "IRON_HELMET:2", "IRON_CHESTPLATE:6", "IRON_LEGGINGS:5", "IRON_BOOTS:2",
                "DIAMOND_HELMET:3", "DIAMOND_CHESTPLATE:8", "DIAMOND_LEGGINGS:6", "DIAMOND_BOOTS:3",
                "NETHERITE_HELMET:3", "NETHERITE_CHESTPLATE:8", "NETHERITE_LEGGINGS:6", "NETHERITE_BOOTS:3",
                "TURTLE_HELMET:2"};
        for (String s : ap) {
            String[] p = s.split(":");
            ARMOR_PTS.put(p[0], Integer.parseInt(p[1]));
        }
        for (String s : new String[]{"DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS"}) {
            ARMOR_TGH.put(s, 2);
        }
        for (String s : new String[]{"NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS"}) {
            ARMOR_TGH.put(s, 3);
        }
    }

    private TowerMath() {
    }

    private static ItemStack[] armor(Player p) {
        PlayerInventory inv = p.getInventory();
        return new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
    }

    static int protLevel(ItemStack i) {
        if (i == null) {
            return 0;
        }
        return i.getEnchantmentLevel(Enchantment.PROTECTION);
    }

    /** Oyuncunun toplam Koruma seviyesi (4 parca, tavansiz). */
    static int epf(Player p) {
        int s = 0;
        for (ItemStack i : armor(p)) {
            s += protLevel(i);
        }
        return s;
    }

    /** Sunucunun uygulayacagi Koruma carpani (sadece debug/teshis). */
    static double protFactor(Player p) {
        int e = Math.min(20, epf(p));
        return 1 - e * 0.04;
    }

    /** P4 sete gore alinan hasar orani (1 = P4 ile ayni). */
    static double protRel(TowerConfig cfg, double epf, double p5, double step) {
        double e = Math.min(epf, cfg.protOverMax);
        if (e <= 16) {
            return 1;
        }
        double r;
        if (e <= 20) {
            r = 1 - (p5 / 100) * ((e - 16) / 4);
        } else {
            r = 1 - p5 / 100 - (step / 100) * ((e - 20) / 4);
        }
        return Math.max(0, Math.min(1, r));
    }

    /** Sunucunun vanilla Korumasini istenen degere ceviren carpan. */
    static double protFix(TowerConfig cfg, double epf, double p5, double step) {
        if (epf <= 16) {
            return 1;
        }
        double e = Math.min(20, epf);
        double v = 1 - e * 0.04;
        if (v <= 0) {
            return 1;
        }
        return (0.36 * protRel(cfg, epf, p5, step)) / v;
    }

    private static double attr(Player p, Attribute a) {
        if (a == null) {
            return -1;
        }
        AttributeInstance ai = p.getAttribute(a);
        return ai == null ? -1 : ai.getValue();
    }

    /** Zirh PUANINDAN sonra hasarin kacta kaci geciyor (0-1). Koruma burada YOK. */
    static double armorFactor(Player p, double dmg) {
        double armor = attr(p, Attribute.ARMOR);
        double tough = attr(p, Attribute.ARMOR_TOUGHNESS);
        if (armor < 0 || tough < 0) {
            armor = 0;
            tough = 0;
            for (ItemStack i : armor(p)) {
                if (i != null) {
                    armor += ARMOR_PTS.getOrDefault(i.getType().name(), 0);
                    tough += ARMOR_TGH.getOrDefault(i.getType().name(), 0);
                }
            }
        }
        if (armor <= 0) {
            return 1;
        }
        double a = armor - dmg / (2 + tough / 4);
        double b2 = armor / 5;
        if (a < b2) {
            a = b2;
        }
        if (a > 20) {
            a = 20;
        }
        double f = 1 - a / 25;
        return Math.max(0, Math.min(1, f));
    }
}
