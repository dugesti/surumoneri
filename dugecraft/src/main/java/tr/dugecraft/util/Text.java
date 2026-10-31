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
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private Text() {
    }

    /** "&6DugeCraft &8» &a..." gibi & kodlu metni bilesene cevirir. */
    public static Component color(String legacy) {
        return AMP.deserialize(legacy);
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
