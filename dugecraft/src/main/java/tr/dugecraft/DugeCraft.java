package tr.dugecraft;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import tr.dugecraft.blok.BlokCommands;
import tr.dugecraft.blok.BlokConfig;
import tr.dugecraft.blok.BlokModule;
import tr.dugecraft.claim.ClaimConfig;
import tr.dugecraft.claim.ClaimModule;
import tr.dugecraft.claim.ModuleFarmClaims;
import tr.dugecraft.farm.FarmClaims;
import tr.dugecraft.farm.SkriptFarmClaims;
import tr.dugecraft.tower.TowerConfig;
import tr.dugecraft.tower.TowerModule;
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
    private ClaimModule claim;
    private TowerModule tower;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        SkriptBridge.init(getLogger());

        // CLAIM + SOK KULESI: birbirine bagli, ikisi BIRLIKTE baslar. Eski
        // script'lerden biri bile acik kalmissa (ayni dosyalara iki sistem
        // yazmasin diye) ikisi de baslamaz, sunucu eskisi gibi Skript'le calisir.
        FarmClaims claims = new SkriptFarmClaims();
        List<Path> claimClash = findScripts("function saveSingleClaim(");
        List<Path> sokClash = findScripts("sok_kuleleri");
        if (!claimClash.isEmpty() || !sokClash.isEmpty()) {
            List<Path> all = new ArrayList<>(claimClash);
            all.addAll(sokClash);
            Text.console("<#f6d365>DugeCraft &8› &cCLAIM ve SOK KULESI MODULLERI BASLATILMADI! &feski script(ler) hala yuklu: &e" + all
                    + " &7claim.sk ve sok.sk'yi silin ya da adlarinin basina '-' koyun, sunucuyu yeniden baslatin.");
        } else {
            try {
                claim = new ClaimModule(this, new ClaimConfig(section("claim")));
                tower = new TowerModule(this, new TowerConfig(section("sok-kulesi")), claim);
                claim.attachTowers(tower);
                claim.enable();
                tower.enable();
                claims = new ModuleFarmClaims(claim);
            } catch (RuntimeException | LinkageError ex) {
                getLogger().severe("Claim / sok kulesi modulu baslatilamadi: " + ex);
                ex.printStackTrace();
                if (tower != null) {
                    tower.disable();
                }
                if (claim != null) {
                    claim.disable();
                }
                tower = null;
                claim = null;
                claims = new SkriptFarmClaims();
            }
        }

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

    private ConfigurationSection section(String name) {
        ConfigurationSection c = getConfig().getConfigurationSection(name);
        return c != null ? c : new MemoryConfiguration();
    }

    @Override
    public void onDisable() {
        // Sira: once kuleler (claim'e soruyor), sonra claim (kayitlar), en son blok
        if (tower != null) {
            tower.disable();
            tower = null;
        }
        if (claim != null) {
            claim.disable();
            claim = null;
        }
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
