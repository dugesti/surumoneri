package tr.dugecraft.util;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Komutlar plugin.yml yerine SADECE ilgili modul basladiginda kaydedilir.
 * Boylece eski script hala yukluyken (modul baslamamisken) Skript'in ayni
 * adli komutlari elinden alinmaz.
 */
public final class DynCommands {

    private static final List<Command> REGISTERED = new ArrayList<>();

    private DynCommands() {
    }

    private static final class Cmd extends Command {
        private final CommandExecutor ex;
        private final TabCompleter tab;

        Cmd(String name, String description, CommandExecutor ex, TabCompleter tab) {
            super(name, description, "/" + name, Collections.emptyList());
            this.ex = ex;
            this.tab = tab;
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            return ex.onCommand(sender, this, label, args);
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (tab != null) {
                List<String> r = tab.onTabComplete(sender, this, alias, args);
                if (r != null) {
                    return r;
                }
            }
            return super.tabComplete(sender, alias, args);
        }
    }

    public static void register(String name, String description, CommandExecutor ex) {
        register(name, description, ex, null);
    }

    public static synchronized void register(String name, String description, CommandExecutor ex, TabCompleter tab) {
        CommandMap map = Bukkit.getCommandMap();
        Cmd c = new Cmd(name, description, ex, tab);
        map.register("dugecraft", c);
        REGISTERED.add(c);
    }

    /** Kapanista kayitlari kaldirir (PlugMan ile yeniden yuklemede cakisma olmasin). */
    public static synchronized void unregisterAll() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        for (Command c : REGISTERED) {
            c.unregister(map);
            known.values().removeIf(v -> v == c);
        }
        REGISTERED.clear();
    }
}
