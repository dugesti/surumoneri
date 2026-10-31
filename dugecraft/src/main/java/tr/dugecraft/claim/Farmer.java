package tr.dugecraft.claim;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ciftci urun tablolari ({-f_keys}, {-f_prices_def}, {-f_names}, {-f_mat}). */
public final class Farmer {

    /** Sira onemli: menuler ve kayit bu sirayla gezer (yeni urunler SONA eklendi). */
    public static final List<String> KEYS = List.of("cactus", "pumpkin", "rotten_flesh", "iron_ingot", "bone", "arrow",
            "spider_eye", "string", "gunpowder", "diamond", "prismarine_shard", "prismarine_crystals", "cooked_cod",
            "cooked_beef", "leather", "cooked_mutton", "white_wool", "cooked_chicken", "feather", "melon_slice", "kelp", "bamboo");

    public static final Map<String, Double> DEF_PRICE = new HashMap<>();
    public static final Map<String, String> NAMES = new LinkedHashMap<>();
    /** Material adi (BUYUK) -> urun anahtari */
    public static final Map<String, String> MAT = new HashMap<>();

    static {
        Object[][] t = {
                {"cactus", 0.04, "Kaktüs"}, {"pumpkin", 0.07, "Balkabağı"}, {"rotten_flesh", 0.1, "Çürük Et"},
                {"iron_ingot", 0.20, "Demir Külçesi"}, {"bone", 0.06, "Kemik"}, {"arrow", 0.06, "Ok"},
                {"spider_eye", 0.06, "Örümcek Gözü"}, {"string", 0.08, "İp"}, {"gunpowder", 0.16, "Barut"},
                {"diamond", 4.0, "Elmas"}, {"prismarine_shard", 0.26, "Prizmarin Parçası"},
                {"prismarine_crystals", 0.36, "Prizmarin Kristalleri"}, {"cooked_cod", 0.40, "Pişmiş Morina"},
                {"cooked_beef", 0.20, "Pişmiş İnek Eti"}, {"leather", 0.04, "Deri"}, {"cooked_mutton", 0.12, "Pişmiş Koyun Eti"},
                {"white_wool", 0.06, "Beyaz Yün"}, {"cooked_chicken", 0.08, "Pişmiş Tavuk"}, {"feather", 0.02, "Tüy"},
                {"melon_slice", 0.02, "Karpuz Dilimi"}, {"kelp", 0.02, "Su Yosunu"}, {"bamboo", 0.01, "Bambu"}};
        for (Object[] r : t) {
            DEF_PRICE.put((String) r[0], (Double) r[1]);
            NAMES.put((String) r[0], (String) r[2]);
        }
        for (String k : KEYS) {
            MAT.put(k.toUpperCase(), k);
        }
    }

    private Farmer() {
    }

    public static Material material(String k) {
        return Material.matchMaterial(k);
    }

    public static String name(String k) {
        return NAMES.getOrDefault(k, k);
    }
}
