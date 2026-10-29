package tr.surumoneri;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bedrock (Geyser/Floodgate) oyuncularini isim on ekine bakmadan tespit eder:
 * once Floodgate ve Geyser API'leri, sonra Floodgate UUID formati.
 */
final class BedrockHook {

    private final Method floodgateInstance;
    private final Method floodgateCheck;
    private final Method geyserInstance;
    private final Method geyserCheck;

    BedrockHook() {
        Method[] floodgate = lookup("floodgate", "org.geysermc.floodgate.api.FloodgateApi",
                "getInstance", "isFloodgatePlayer");
        Method[] geyser = lookup("Geyser-Spigot", "org.geysermc.geyser.api.GeyserApi",
                "api", "isBedrockPlayer");
        floodgateInstance = floodgate[0];
        floodgateCheck = floodgate[1];
        geyserInstance = geyser[0];
        geyserCheck = geyser[1];
    }

    boolean hasFloodgate() {
        return floodgateCheck != null;
    }

    boolean hasGeyser() {
        return geyserCheck != null;
    }

    boolean isBedrock(UUID uuid, boolean uuidCheck) {
        if (ask(floodgateInstance, floodgateCheck, uuid) || ask(geyserInstance, geyserCheck, uuid)) {
            return true;
        }
        // Floodgate, linklenmemis Bedrock oyuncularina 00000000-0000-0000-xxxx-xxxxxxxxxxxx
        // biciminde UUID verir. Floodgate yalnizca proxy'de kuruluysa da bu yakalar.
        return uuidCheck && uuid.getMostSignificantBits() == 0L;
    }

    private static boolean ask(Method instance, Method check, UUID uuid) {
        if (check == null) {
            return false;
        }
        try {
            Object api = instance.invoke(null);
            return api != null && (boolean) check.invoke(api, uuid);
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method[] lookup(String pluginName, String className, String instanceMethod, String checkMethod) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            return new Method[2];
        }
        try {
            Class<?> type = Class.forName(className, true, plugin.getClass().getClassLoader());
            return new Method[]{type.getMethod(instanceMethod), type.getMethod(checkMethod, UUID.class)};
        } catch (Throwable t) {
            return new Method[2];
        }
    }
}
