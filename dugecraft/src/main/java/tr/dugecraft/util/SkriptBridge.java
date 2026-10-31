package tr.dugecraft.util;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hala Skript'te kalan scriptler (bilgi.sk, buyulutas.sk, gecis donemindeki
 * claim.sk / sok.sk) ile ortak Skript degiskenlerini okumak / yazmak icin.
 * Skript yuklu degilse hicbir sey yapmaz, hata vermez.
 */
public final class SkriptBridge {

    private static volatile boolean ready;
    private static Method getVar;
    private static Method setVar;

    private SkriptBridge() {
    }

    public static synchronized void init(Logger log) {
        ready = false;
        if (Bukkit.getPluginManager().getPlugin("Skript") == null) {
            return;
        }
        try {
            Class<?> vars = Class.forName("ch.njol.skript.variables.Variables");
            getVar = vars.getMethod("getVariable", String.class, Event.class, boolean.class);
            setVar = vars.getMethod("setVariable", String.class, Object.class, Event.class, boolean.class);
            ready = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            log.log(Level.WARNING, "Skript degisken API'si bulunamadi; Skript koprusu kapali.", e);
        }
    }

    public static boolean available() {
        return ready;
    }

    /** {name} degerini okur (name "{" ve "}" olmadan, ornek "-claims::x::owner"). */
    public static Object get(String name) {
        if (!ready) {
            return null;
        }
        try {
            return getVar.invoke(null, name, null, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** value null ise siler. "liste::*" adiyla tum liste silinir. */
    public static void set(String name, Object value) {
        if (!ready) {
            return;
        }
        try {
            setVar.invoke(null, name, value, null, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Skript kapaniyor olabilir; kopru opsiyonel
        }
    }

    public static boolean isTrue(String name) {
        return Boolean.TRUE.equals(get(name));
    }
}
