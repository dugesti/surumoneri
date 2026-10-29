package tr.surumoneri;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViaVersion'a reflection ile baglanir; boylece ViaVersion yoksa veya
 * surumu degisirse eklenti derleme/yukleme hatasi vermez.
 */
final class ViaHook {

    private final Logger logger;
    private final Object api;
    private final Method getPlayerVersion;
    private final Method getServerVersion;
    private final Method getProtocol;
    private final Method getName;
    private final AtomicBoolean warned = new AtomicBoolean();

    private ViaHook(Logger logger, Object api, Method getPlayerVersion, Method getServerVersion,
                    Method getProtocol, Method getName) {
        this.logger = logger;
        this.api = api;
        this.getPlayerVersion = getPlayerVersion;
        this.getServerVersion = getServerVersion;
        this.getProtocol = getProtocol;
        this.getName = getName;
    }

    /** ViaVersion yuklu degilse veya API beklenmedikse null doner. */
    static ViaHook find(Logger logger) {
        Plugin via = Bukkit.getPluginManager().getPlugin("ViaVersion");
        if (via == null || !via.isEnabled()) {
            return null;
        }
        try {
            ClassLoader loader = via.getClass().getClassLoader();
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via", true, loader);
            Class<?> apiClass = Class.forName("com.viaversion.viaversion.api.ViaAPI", true, loader);
            Class<?> protocolClass = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion", true, loader);

            Object api = viaClass.getMethod("getAPI").invoke(null);
            Method getPlayerVersion = apiClass.getMethod("getPlayerVersion", UUID.class);
            Method getServerVersion = apiClass.getMethod("getServerVersion");
            Method getProtocol = protocolClass.getMethod("getProtocol", int.class);
            Method getName = protocolClass.getMethod("getName");
            return new ViaHook(logger, api, getPlayerVersion, getServerVersion, getProtocol, getName);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "ViaVersion API'sine baglanilamadi, surum kontrolu kapali.", t);
            return null;
        }
    }

    /** Oyuncunun istemci protokol numarasi; bilinmiyorsa -1. */
    int playerProtocol(UUID uuid) {
        try {
            return (int) getPlayerVersion.invoke(api, uuid);
        } catch (Throwable t) {
            warnOnce(t);
            return -1;
        }
    }

    /** ViaVersion'in gordugu sunucu protokolu; bilinmiyorsa -1. */
    int serverProtocol() {
        try {
            Object server = getServerVersion.invoke(api);
            Class<?> type = getServerVersion.getReturnType();
            try {
                return (int) type.getMethod("lowestSupportedVersion").invoke(server);
            } catch (NoSuchMethodException e) {
                Object version = type.getMethod("lowestSupportedProtocolVersion").invoke(server);
                return (int) version.getClass().getMethod("getVersion").invoke(version);
            }
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Protokol numarasinin okunabilir adi (orn. "1.21.11"); bilinmiyorsa null. */
    String versionName(int protocol) {
        try {
            Object version = getProtocol.invoke(null, protocol);
            return version == null ? null : (String) getName.invoke(version);
        } catch (Throwable t) {
            return null;
        }
    }

    private void warnOnce(Throwable t) {
        if (warned.compareAndSet(false, true)) {
            logger.log(Level.WARNING, "ViaVersion'dan oyuncu surumu okunamadi.", t);
        }
    }
}
