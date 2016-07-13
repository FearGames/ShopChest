package de.epiceric.shopchest;

import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Regex;
import de.epiceric.shopchest.language.LanguageUtils;
import de.epiceric.shopchest.language.LocalizedMessage;
import de.epiceric.shopchest.listeners.*;
import de.epiceric.shopchest.nms.IJsonBuilder;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.Shop.ShopType;
import de.epiceric.shopchest.sql.Database;
import de.epiceric.shopchest.sql.MySQL;
import de.epiceric.shopchest.sql.SQLite;
import de.epiceric.shopchest.utils.Metrics;
import de.epiceric.shopchest.utils.Metrics.Graph;
import de.epiceric.shopchest.utils.Metrics.Plotter;
import de.epiceric.shopchest.utils.ShopUtils;
import de.epiceric.shopchest.utils.UpdateChecker;
import de.epiceric.shopchest.utils.UpdateChecker.UpdateCheckerResult;
import de.epiceric.shopchest.utils.Utils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

public class ShopChest extends JavaPlugin {

    private static ShopChest instance;

    private Config config = null;
    private Economy econ = null;
    private Permission perm = null;
    private boolean lockette = false;
    private boolean lwc = false;
    private Database database;
    private boolean isUpdateNeeded = false;
    private String latestVersion = "";
    private String downloadLink = "";

    /**
     * @return An instance of ShopChest
     */
    public static ShopChest getInstance() {
        return instance;
    }

    /**
     * Sets up the economy of Vault
     * @return Whether an economy plugin has been registered
     */
    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    /**
     * Sets up the permissions of Vault
     * @return Whether a permissions plugin has been registered
     */
    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perm = rsp.getProvider();
        return perm != null;
    }


    @Override
    public void onEnable() {
        instance = this;

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Could not find plugin 'Vault'!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupEconomy()) {
            getLogger().severe("Could not find any Vault economy dependency!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupPermissions()) {
            getLogger().severe("Could not find any Vault permission dependency!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        switch (Utils.getServerVersion()) {
            case "v1_8_R1":
            case "v1_8_R2":
            case "v1_8_R3":
            case "v1_9_R1":
            case "v1_9_R2":
            case "v1_10_R1":
                break;
            default:
                getLogger().severe("Incompatible Server Version: " + Utils.getServerVersion() + "!");
                getServer().getPluginManager().disablePlugin(this);
                return;
        }

        config = new Config(this);
        LanguageUtils.load();
        saveResource("item_names.txt", true);

        try {
            Metrics metrics = new Metrics(this);
            Graph shopType = metrics.createGraph("Shop Type");
            shopType.addPlotter(new Plotter("Normal") {

                @Override
                public int getValue() {
                    int value = 0;

                    for (Shop shop : ShopUtils.getShops()) {
                        if (shop.getShopType() == ShopType.NORMAL) value++;
                    }

                    return value;
                }

            });

            shopType.addPlotter(new Plotter("Admin") {

                @Override
                public int getValue() {
                    int value = 0;

                    for (Shop shop : ShopUtils.getShops()) {
                        if (shop.getShopType() == ShopType.ADMIN) value++;
                    }

                    return value;
                }

            });

            Graph databaseType = metrics.createGraph("Database Type");
            databaseType.addPlotter(new Plotter("SQLite") {

                @Override
                public int getValue() {
                    if (config.database_type == Database.DatabaseType.SQLite)
                        return 1;

                    return 0;
                }

            });

            databaseType.addPlotter(new Plotter("MySQL") {

                @Override
                public int getValue() {
                    if (config.database_type == Database.DatabaseType.MySQL)
                        return 1;

                    return 0;
                }

            });

            metrics.start();
        } catch (IOException e) {
            getLogger().severe("Could not submit stats.");
        }

        if (config.database_type == Database.DatabaseType.SQLite) {
            getLogger().info("Using SQLite");
            database = new SQLite(this);
        } else {
            getLogger().info("Using MySQL");
            database = new MySQL(this);
        }

        lockette = getServer().getPluginManager().getPlugin("Lockette") != null;
        lwc = getServer().getPluginManager().getPlugin("LWC") != null;

        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                UpdateChecker uc = new UpdateChecker(ShopChest.this);
                UpdateCheckerResult result = uc.check();

                Bukkit.getConsoleSender().sendMessage("[ShopChest] " + LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CHECKING));
                if (result == UpdateCheckerResult.TRUE) {
                    latestVersion = uc.getVersion();
                    downloadLink = uc.getLink();
                    isUpdateNeeded = true;
                    Bukkit.getConsoleSender().sendMessage("[ShopChest] " + LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)));

                    for (Player p : getServer().getOnlinePlayers()) {
                        if (p.isOp() || perm.has(p, "shopchest.notification.update")) {
                            IJsonBuilder jb;
                            switch (Utils.getServerVersion()) {
                                case "v1_8_R1":
                                    jb = new de.epiceric.shopchest.nms.v1_8_R1.JsonBuilder(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)), LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CLICK_TO_DOWNLOAD), downloadLink);
                                    break;
                                case "v1_8_R2":
                                    jb = new de.epiceric.shopchest.nms.v1_8_R2.JsonBuilder(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)), LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CLICK_TO_DOWNLOAD), downloadLink);
                                    break;
                                case "v1_8_R3":
                                    jb = new de.epiceric.shopchest.nms.v1_8_R3.JsonBuilder(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)), LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CLICK_TO_DOWNLOAD), downloadLink);
                                    break;
                                case "v1_9_R1":
                                    jb = new de.epiceric.shopchest.nms.v1_9_R1.JsonBuilder(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)), LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CLICK_TO_DOWNLOAD), downloadLink);
                                    break;
                                case "v1_9_R2":
                                    jb = new de.epiceric.shopchest.nms.v1_9_R2.JsonBuilder(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)), LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CLICK_TO_DOWNLOAD), downloadLink);
                                    break;
                                case "v1_10_R1":
                                    jb = new de.epiceric.shopchest.nms.v1_10_R1.JsonBuilder(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, latestVersion)), LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CLICK_TO_DOWNLOAD), downloadLink);
                                    break;
                                default:
                                    return;
                            }
                            jb.sendJson(p);
                        }
                    }

                } else if (result == UpdateCheckerResult.FALSE) {
                    latestVersion = "";
                    downloadLink = "";
                    isUpdateNeeded = false;
                    Bukkit.getConsoleSender().sendMessage("[ShopChest] " + LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_NO_UPDATE));
                } else {
                    latestVersion = "";
                    downloadLink = "";
                    isUpdateNeeded = false;
                    Bukkit.getConsoleSender().sendMessage("[ShopChest] " + LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_ERROR));
                }
            }
        });

        try {
            Commands.registerCommand(new Commands(this, config.main_command_name, "Manage Shops.", "", new ArrayList<String>()), this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        initializeShops();

        getServer().getPluginManager().registerEvents(new HologramUpdateListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemProtectListener(), this);
        getServer().getPluginManager().registerEvents(new ShopInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new NotifyUpdateOnJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ChestProtectListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemCustomNameListener(), this);

        if (getServer().getPluginManager().getPlugin("ClearLag") != null)
            getServer().getPluginManager().registerEvents(new ClearLagListener(), this);

        if (getServer().getPluginManager().getPlugin("LWC") != null)
            new LWCMagnetListener(this).initializeListener();
    }

    @Override
    public void onDisable() {
        for (Shop shop : ShopUtils.getShops()) {
            ShopUtils.removeShop(shop, false);
        }

        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (item.hasMetadata("shopItem")) {
                    item.remove();
                }
            }
        }
    }

    /**
     * Initializes the shops
     */
    private void initializeShops() {
        int count = ShopUtils.reloadShops();
        getLogger().info("Initialized " + String.valueOf(count) + " Shops");
    }

    /**
     * @return Registered Economy of Vault
     */
    public Economy getEconomy() {
        return econ;
    }

    /**
     * @return Registered Permission of Vault
     */
    public Permission getPermission() {
        return perm;
    }

    /**
     * @return ShopChest's shop database
     */
    public Database getShopDatabase() {
        return database;
    }

    /**
     * @return Whether LWC is available
     */
    public boolean hasLWC() {
        return lwc;
    }

    /**
     * @return Whether Lockette is available
     */
    public boolean hasLockette() {
        return lockette;
    }

    /**
     * @return Whether an update is needed (will return false if not checked)
     */
    public boolean isUpdateNeeded() {
        return isUpdateNeeded;
    }

    /**
     * Set whether an update is needed
     * @param isUpdateNeeded Whether an update should be needed
     */
    public void setUpdateNeeded(boolean isUpdateNeeded) {
        this.isUpdateNeeded = isUpdateNeeded;
    }

    /**
     * @return The latest version of ShopChest (will return null if not checked or if no update is available)
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Set the latest version
     * @param latestVersion Version to set as latest version
     */
    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    /**
     * @return The download link of the latest version (will return null if not checked or if no update is available)
     */
    public String getDownloadLink() {
        return downloadLink;
    }

    /**
     * Set the download Link of the latest version (will return null if not checked or if no update is available)
     * @param downloadLink Link to set as Download Link
     */
    public void setDownloadLink(String downloadLink) {
        this.downloadLink = downloadLink;
    }

    /**
     * @return The {@link Config} of ShopChset
     */
    public Config getShopChestConfig() {
        return config;
    }

    /**
     * Provides a reader for a text file located inside the jar.
     * The returned reader will read text with the UTF-8 charset.
     * @param file - the filename of the resource to load
     * @return null if getResource(String) returns null
     * @throws IllegalArgumentException - if file is null
     */
    public Reader getTextResourceP(String file) throws IllegalArgumentException {
       return getTextResource(file);
    }
}