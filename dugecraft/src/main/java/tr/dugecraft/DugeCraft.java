package tr.dugecraft;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import tr.dugecraft.blok.BlokCommands;
import tr.dugecraft.blok.BlokConfig;
import tr.dugecraft.blok.BlokModule;
import tr.dugecraft.farm.FarmClaims;
import tr.dugecraft.farm.SkriptFarmClaims;
import tr.dugecraft.util.DynCommands;
import tr.dugecraft.util.SkriptBridge;
import tr.dugecraft.util.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class DugeCraft extends JavaPlugin {

    private BlokModule blok;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        SkriptBridge.init(getLogger());

        // Gecis donemi: claim.sk hala Skript'te. Claim modulu tasininca degisecek.
        FarmClaims claims = new SkriptFarmClaims();

        ConfigurationSection bc = getConfig().getConfigurationSection("blok-limit");
        BlokConfig cfg = new BlokConfig(bc != null ? bc : new MemoryConfiguration());

        List<Path> clash = findScripts("chunklimits_v3");
        if (!clash.isEmpty()) {
            // Ayni veri dosyasina iki sistem yazarsa veri bozulur: modul hic baslamaz.
            Text.console(cfg.prefix + " &cBLOK LIMIT MODULU BASLATILMADI! &fbloksinir.sk hala yuklu: &e" + clash
                    + " &7Script'i silin ya da adinin basina '-' koyun ve sunucuyu yeniden baslatin.");
        } else {
            blok = new BlokModule(this, cfg, claims);
            blok.enable();
        }

        if (blok != null) {
            BlokCommands bcmd = new BlokCommands(blok);
            DynCommands.register("bloklag", "bloksinir skriptinin son 1 dakikalık yükünü ve sunucu durumunu gösterir", bcmd);
            DynCommands.register("chunkhesapla", "Bulunduğun chunk'ı baştan sona yeniden sayar", bcmd);
            DynCommands.register("chunklimit", "Kayıtlı sayaçları gösterir (tarama yapmaz)", bcmd);
            DynCommands.register("chunklimittemizle", "Tarama önbelleğini sıfırlar (WorldEdit vb. sonrası)", bcmd);
            DynCommands.register("chunklimitkaydet", "Limit verilerini diske hemen yazar", bcmd);
        }
    }

    @Override
    public void onDisable() {
        DynCommands.unregisterAll();
        if (blok != null) {
            blok.disable();
            blok = null;
        }
    }

    /**
     * plugins/Skript/scripts altinda (kapali olmayan, '-' ile baslamayan)
     * icinde verilen metin gecen .sk dosyalari. Ayni veriyi isleyen eski
     * script'in yanlislikla acik kalmasini yakalamak icin.
     */
    private List<Path> findScripts(String needle) {
        List<Path> out = new ArrayList<>();
        Path dir = getServer().getPluginsFolder().toPath().resolve("Skript").resolve("scripts");
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sk"))
                    .filter(p -> {
                        for (Path part : dir.relativize(p)) {
                            if (part.toString().startsWith("-")) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .forEach(p -> {
                        try {
                            String txt = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                            if (txt.contains(needle)) {
                                out.add(dir.relativize(p));
                            }
                        } catch (IOException ignored) {
                            // okunamayan dosya atlanir
                        }
                    });
        } catch (IOException ignored) {
            // klasor gezilemezse catisma yok sayilmaz: bos liste
        }
        return out;
    }
}
