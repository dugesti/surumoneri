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
        eco = null;
        try {
            Class<?> c = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(c);
            if (rsp == null) {
                log.warning("Vault ekonomi saglayicisi bulunamadi.");
                return false;
            }
            eco = rsp.getProvider();
            getBalance = c.getMethod("getBalance", OfflinePlayer.class);
            withdraw = c.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = c.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            log.warning("Vault bulunamadi: " + e);
            eco = null;
            return false;
        }
    }

    public static boolean ready() {
        return eco != null;
    }

    public static double balance(OfflinePlayer p) {
        if (eco == null) {
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
        if (eco == null) {
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
