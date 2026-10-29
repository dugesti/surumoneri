package tr.surumoneri;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SurumOneri extends JavaPlugin implements Listener, TabExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_gray>[<gradient:#FF6A00:#FFB347>SürümÖneri</gradient>]</dark_gray> ";

    private enum Mode { HEPSI, ESKI, YENI }

    private record Settings(String recommended, Mode mode, long delayTicks, boolean uuidCheck,
                            String sound, float volume, float pitch, String message) {
    }

    // PlugMan ile ViaVersion/Floodgate/Geyser sonradan yuklenip kaldirilabilir; baglantilar yenilenir.
    private static final Set<String> HOOKED_PLUGINS = Set.of("ViaVersion", "floodgate", "Geyser-Spigot");

    private volatile ViaHook via;
    private volatile BedrockHook bedrock;
    private String serverVersion;
    private volatile int serverProtocol;
    // Bekleyen uyari gorevleri; cikista ve eklenti kapanirken (plugman unload/reload) iptal edilir.
    private final Map<UUID, ScheduledTask> pending = new ConcurrentHashMap<>();
    // Folia'da birden fazla bolge thread'i okur; yenilemede tek seferde degistirilir.
    private volatile Settings settings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverVersion = detectServerVersion();
        refreshHooks();
        loadSettings();

        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("surumoneri");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        getLogger().info("Sunucu surumu: " + serverVersion + " (protokol " + serverProtocol + ")");
    }

    @Override
    public void onDisable() {
        pending.values().forEach(ScheduledTask::cancel);
        pending.clear();
        // Kaldirilan eklentilerin class loader'larina referans birakma.
        via = null;
        bedrock = null;
    }

    private void refreshHooks() {
        ViaHook newVia = ViaHook.find(getLogger());
        BedrockHook newBedrock = new BedrockHook();
        via = newVia;
        bedrock = newBedrock;
        serverProtocol = detectServerProtocol(newVia);
        if (newVia == null) {
            getLogger().warning("ViaVersion bulunamadi: farkli surumle giris yapilamayacagi icin uyari gonderilmeyecek.");
        }
        getLogger().info("Baglantilar: ViaVersion: " + (newVia != null) + " | Floodgate: " + newBedrock.hasFloodgate()
                + " | Geyser: " + newBedrock.hasGeyser());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        onHookedPluginChange(event.getPlugin());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        onHookedPluginChange(event.getPlugin());
    }

    private void onHookedPluginChange(Plugin plugin) {
        if (plugin != this && HOOKED_PLUGINS.contains(plugin.getName())) {
            refreshHooks();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        BedrockHook bedrock = this.bedrock;
        if (via == null || bedrock == null || serverProtocol <= 0) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Settings s = settings;
        if (player.hasPermission("surumoneri.bypass") || bedrock.isBedrock(uuid, s.uuidCheck())) {
            return;
        }
        // Oyuncunun kendi scheduler'i: Folia'da dogru bolge thread'inde calisir,
        // oyuncu cikarsa gorev kendiliginden iptal olur (sizinti/tekrar yok).
        ScheduledTask task = player.getScheduler().runDelayed(this, t -> {
            pending.remove(uuid, t);
            warnIfNeeded(player);
        }, null, Math.max(1L, s.delayTicks()));
        if (task != null) {
            ScheduledTask old = pending.put(uuid, task);
            if (old != null) {
                old.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ScheduledTask task = pending.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void warnIfNeeded(Player player) {
        ViaHook via = this.via;
        if (!isEnabled() || via == null || !player.isOnline()) {
            return;
        }
        Settings s = settings;
        int clientProtocol = via.playerProtocol(player.getUniqueId());
        if (!shouldWarn(s, clientProtocol)) {
            return;
        }
        sendWarning(player, s, clientProtocol);
    }

    private boolean shouldWarn(Settings s, int clientProtocol) {
        if (clientProtocol <= 0 || clientProtocol == serverProtocol) {
            return false;
        }
        return switch (s.mode()) {
            case HEPSI -> true;
            case ESKI -> clientProtocol < serverProtocol;
            case YENI -> clientProtocol > serverProtocol;
        };
    }

    private void sendWarning(Player player, Settings s, int clientProtocol) {
        player.sendMessage(render(s, player.getName(), clientVersionName(clientProtocol)));
        if (!s.sound().isEmpty()) {
            player.playSound(player.getLocation(), s.sound(), s.volume(), s.pitch());
        }
    }

    private Component render(Settings s, String playerName, String clientVersion) {
        return MM.deserialize(s.message(), TagResolver.resolver(
                Placeholder.unparsed("sunucu_surumu", s.recommended()),
                Placeholder.unparsed("istemci_surumu", clientVersion),
                Placeholder.unparsed("oyuncu", playerName)));
    }

    private String clientVersionName(int protocol) {
        ViaHook via = this.via;
        String name = via == null ? null : via.versionName(protocol);
        return name == null ? "protokol " + protocol : name;
    }

    private void loadSettings() {
        FileConfiguration c = getConfig();

        String recommended = c.getString("onerilen-surum", "auto").trim();
        if (recommended.isEmpty() || recommended.equalsIgnoreCase("auto")) {
            recommended = serverVersion;
        }

        Mode mode;
        try {
            mode = Mode.valueOf(c.getString("uyari-modu", "hepsi").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            getLogger().warning("Gecersiz uyari-modu, 'hepsi' kullaniliyor.");
            mode = Mode.HEPSI;
        }

        String message = String.join("\n", c.getStringList("mesaj"));
        Settings s = new Settings(recommended, mode, c.getLong("gecikme-tick", 60L),
                c.getBoolean("bedrock-uuid-kontrolu", true), c.getString("ses", "").trim(),
                (float) c.getDouble("ses-seviyesi", 0.6), (float) c.getDouble("ses-perdesi", 1.6), message);
        try {
            render(s, "test", "test");
        } catch (Exception e) {
            getLogger().severe("mesaj MiniMessage formatinda hatali: " + e.getMessage());
        }
        settings = s;
    }

    @SuppressWarnings("deprecation")
    private static int detectServerProtocol(ViaHook via) {
        try {
            int protocol = Bukkit.getUnsafe().getProtocolVersion();
            if (protocol > 0) {
                return protocol;
            }
        } catch (Throwable ignored) {
            // Paper disi cekirdek; ViaVersion'a sor.
        }
        return via == null ? -1 : via.serverProtocol();
    }

    private static String detectServerVersion() {
        try {
            return Bukkit.getMinecraftVersion();
        } catch (Throwable ignored) {
            // "1.21.11-R0.1-SNAPSHOT" -> "1.21.11"
            return Bukkit.getBukkitVersion().split("-", 2)[0];
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "yenile", "reload" -> {
                reloadConfig();
                loadSettings();
                reply(sender, "<green>Ayarlar yenilendi.</green>");
            }
            case "test" -> {
                if (!(sender instanceof Player player)) {
                    reply(sender, "<red>Bu komut oyun içinden kullanılır.</red>");
                    return true;
                }
                ViaHook via = this.via;
                int protocol = via == null ? -1 : via.playerProtocol(player.getUniqueId());
                sendWarning(player, settings, protocol > 0 ? protocol : serverProtocol);
            }
            case "kontrol" -> {
                if (args.length < 2) {
                    reply(sender, "<gray>Kullanım: <white>/" + label + " kontrol \\<oyuncu></white></gray>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    reply(sender, "<red>Oyuncu çevrimiçi değil.</red>");
                    return true;
                }
                ViaHook via = this.via;
                BedrockHook bedrock = this.bedrock;
                boolean isBedrock = bedrock != null && bedrock.isBedrock(target.getUniqueId(), settings.uuidCheck());
                int protocol = via == null ? -1 : via.playerProtocol(target.getUniqueId());
                boolean warn = !isBedrock && !target.hasPermission("surumoneri.bypass") && shouldWarn(settings, protocol);
                reply(sender, "<white>" + MM.escapeTags(target.getName()) + "</white> <dark_gray>»</dark_gray> "
                        + "<gray>İstemci:</gray> <white>" + (protocol > 0 ? clientVersionName(protocol) : "bilinmiyor") + "</white>"
                        + " <dark_gray>|</dark_gray> <gray>Bedrock:</gray> " + yesNo(isBedrock)
                        + " <dark_gray>|</dark_gray> <gray>Uyarı:</gray> " + yesNo(warn));
            }
            default -> reply(sender, "<gray>Sunucu:</gray> <white>" + serverVersion + "</white> "
                    + "<dark_gray>(protokol " + serverProtocol + ")</dark_gray> <dark_gray>|</dark_gray> "
                    + "<gray>/" + label + " <white>yenile</white> | <white>test</white> | <white>kontrol \\<oyuncu></white></gray>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("yenile", "test", "kontrol")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("kontrol")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }

    private static String yesNo(boolean value) {
        return value ? "<green>evet</green>" : "<red>hayır</red>";
    }

    private static void reply(CommandSender sender, String miniMessage) {
        sender.sendMessage(MM.deserialize(PREFIX + miniMessage));
    }
}
