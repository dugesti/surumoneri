package tr.dugecraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Skript'in "send" ve "%sayi%" davranisini birebir taklit eden yardimcilar.
 * Oyuncular eskisiyle ayni yaziyi gorsun diye renk kodlari (&) ve sayi
 * bicimi Skript'teki gibi uretilir.
 */
public final class Text {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    private static final java.util.regex.Pattern SK_HEX = java.util.regex.Pattern.compile("<#([0-9a-fA-F]{6})>");

    /**
     * Skript metnini bilesene cevirir: "&a" / "§a" kodlari ve Skript'in
     * "<#5ac8fa>" hex renk etiketi (eski script'lerin hepsi bunlari kullanir).
     */
    public static Component color(String legacy) {
        String s = legacy.replace('§', '&');
        if (s.indexOf("<#") >= 0) {
            s = SK_HEX.matcher(s).replaceAll("&#$1");
        }
        return AMP.deserialize(s);
    }

    /** Renk kodlarini atar (Skript "uncolored"). */
    public static String plain(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static void actionBar(org.bukkit.entity.Player p, String legacy) {
        if (p != null) {
            p.sendActionBar(color(legacy));
        }
    }

    public static void send(CommandSender to, String legacy) {
        if (to != null) {
            to.sendMessage(color(legacy));
        }
    }

    public static void console(String legacy) {
        Bukkit.getConsoleSender().sendMessage(color(legacy));
    }

    /**
     * Skript'in sayi yazimi: tam sayilar ondaliksiz ("20"), digerleri en fazla
     * 2 ondalik, sondaki sifirlar atilir ("19.5", "0.33"). Skript ayari
     * "number accuracy: 2" varsayilanidir.
     */
    public static String num(Number n) {
        if (n == null) {
            return "<none>";
        }
        if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte) {
            return Long.toString(n.longValue());
        }
        double d = n.doubleValue();
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "infinity" : "-infinity";
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        String s = new BigDecimal(Double.toString(d)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return s.equals("-0") ? "0" : s;
    }
}
