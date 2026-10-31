package tr.dugecraft.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Vault ekonomisi (Skript'in "player's balance" ifadesinin kullandigi yol).
 * Derleme bagimliligi olmasin diye reflection ile.
 */
public final class Econ {

    private static Object eco;
    private static Method getBalance;
    private static Method withdraw;
    private static Method deposit;

    private Econ() {
    }

    public static synchronized boolean init(Logger log) {
        logRef = log;
        lastTry = System.currentTimeMillis();
        if (eco != null) {
            return true;
        }
        try {
            Class<?> c = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(c);
            if (rsp == null) {
                if (!warned) {
                    warned = true;
                    log.warning("Vault ekonomi saglayicisi bulunamadi (Essentials/CMI gibi bir ekonomi eklentisi gerekli). Para islemleri calismaz.");
                }
                return false;
            }
            eco = rsp.getProvider();
            getBalance = c.getMethod("getBalance", OfflinePlayer.class);
            withdraw = c.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = c.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            if (!warned) {
                warned = true;
                log.warning("Vault bulunamadi, para islemleri calismaz: " + e);
            }
            eco = null;
            return false;
        }
    }

    private static volatile long lastTry;
    private static volatile boolean warned;
    private static volatile Logger logRef;

    /**
     * Ekonomi saglayicisi (Essentials, CMI...) DugeCraft'tan SONRA acilabilir
     * veya sonradan kurulabilir: hazir degilse en fazla 5 sn'de bir yeniden aranir.
     */
    private static boolean ensure() {
        if (eco != null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastTry < 5000) {
            return false;
        }
        lastTry = now;
        Logger l = logRef != null ? logRef : Bukkit.getLogger();
        return init(l);
    }

    public static boolean ready() {
        return ensure();
    }

    public static double balance(OfflinePlayer p) {
        if (!ensure()) {
            return 0;
        }
        try {
            return ((Number) getBalance.invoke(eco, p)).doubleValue();
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /** @return true ise islem basarili */
    public static boolean withdraw(OfflinePlayer p, double amount) {
        return call(withdraw, p, amount);
    }

    public static boolean deposit(OfflinePlayer p, double amount) {
        return call(deposit, p, amount);
    }

    private static boolean call(Method m, OfflinePlayer p, double amount) {
        if (!ensure()) {
            return false;
        }
        try {
            Object r = m.invoke(eco, p, amount);
            Method ok = r.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(ok.invoke(r));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
