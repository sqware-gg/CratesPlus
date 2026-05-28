package dev.cratesplus;

import dev.cratesplus.api.CratesPlusApi;
import dev.cratesplus.command.CratesCommand;
import dev.cratesplus.command.CratesPlusCommand;
import dev.cratesplus.config.ConfigReferenceWriter;
import dev.cratesplus.config.CratesPlusConfig;
import dev.cratesplus.crate.CrateDataStore;
import dev.cratesplus.crate.CrateService;
import dev.cratesplus.economy.EconomyService;
import dev.cratesplus.gui.CratesGui;
import dev.cratesplus.gui.CratesMenuListener;
import dev.cratesplus.hook.PlaceholderApiExpansion;
import dev.cratesplus.listener.CrateListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CratesPlusPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 31619;

    private CratesPlusConfig cratesConfig;
    private CrateDataStore crateDataStore;
    private EconomyService economyService;
    private CrateService crateService;
    private CratesGui cratesGui;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_PLUGIN_ID);
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);

        cratesConfig = new CratesPlusConfig(this);
        crateDataStore = new CrateDataStore(this);
        economyService = new EconomyService(this);
        crateService = new CrateService(this, cratesConfig, crateDataStore, economyService);
        cratesGui = new CratesGui(crateService);
        CratesPlusApi.register(crateService);

        registerCommands();
        getServer().getPluginManager().registerEvents(new CratesMenuListener(crateService, cratesGui), this);
        getServer().getPluginManager().registerEvents(new CrateListener(crateService, cratesGui), this);
        registerHooks();
        crateService.start();

        if (!economyService.available()) {
            getLogger().warning("No Vault economy provider is available. Crates without open costs will still work.");
        } else {
            getLogger().info("Hooked Vault economy provider: " + economyService.providerName());
        }
        getLogger().info("Loaded " + cratesConfig.crates().size() + " crates.");
    }

    private void registerHooks() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderApiExpansion(this, crateService).register();
            getLogger().info("Registered PlaceholderAPI expansion: cratesplus");
        }
    }

    @Override
    public void onDisable() {
        CratesPlusApi.unregister();
        if (crateService != null) {
            crateService.stop();
        }
    }

    private void registerCommands() {
        CratesCommand cratesCommand = new CratesCommand(crateService, cratesGui);
        PluginCommand crates = getCommand("crates");
        if (crates != null) {
            crates.setExecutor(cratesCommand);
            crates.setTabCompleter(cratesCommand);
        }

        CratesPlusCommand adminCommand = new CratesPlusCommand(crateService);
        PluginCommand admin = getCommand("cratesplus");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }
    }
}
