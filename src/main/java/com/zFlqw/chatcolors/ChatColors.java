package com.zFlqw.chatcolors;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatColors extends JavaPlugin implements Listener {

    private boolean enableColor;
    private String defaultColor;
    private String language;

    private Messages messages;

    private final Map<UUID, String> playerColors = new ConcurrentHashMap<>();
    private volatile boolean playerDataDirty = false;

    private File playerDataFile;
    private FileConfiguration playerDataConfig;

    private BukkitTask saveTask;

    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");
    private static final Pattern VALID_HEX_PATTERN = Pattern.compile("#[A-Fa-f0-9]{6}");
    private static final String VALID_CODES = "0123456789abcdefklmnor";

    private static final String COMMAND_NAME = "chatcolor";
    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "Chat Colors";
    
    // Auto-save configuration constants
    private static final long SAVE_INTERVAL_TICKS = 20L * 60; // 1 minute in ticks
    private static final long SAVE_INITIAL_DELAY_TICKS = 20L * 60; // 1 minute initial delay
    private static final String PLAYER_COLORS_PATH = "colors";

    private static final List<String> COLOR_CODES = Collections.unmodifiableList(Arrays.asList(
            "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9",
            "&a", "&b", "&c", "&d", "&e", "&f",
            "&k", "&l", "&m", "&n", "&o", "&r",
            "#ff0000", "#00ff00", "#0000ff", "#ffff00", "#00ffff", "#ff00ff"));

    private static final Map<String, Material> GUI_COLORS;

    static {
        Map<String, Material> guiColors = new LinkedHashMap<>();
        guiColors.put("&0", Material.BLACK_WOOL);
        guiColors.put("&1", Material.BLUE_CONCRETE);
        guiColors.put("&2", Material.GREEN_CONCRETE);
        guiColors.put("&3", Material.CYAN_CONCRETE);
        guiColors.put("&4", Material.RED_CONCRETE);
        guiColors.put("&5", Material.PURPLE_CONCRETE);
        guiColors.put("&6", Material.ORANGE_CONCRETE);
        guiColors.put("&7", Material.LIGHT_GRAY_CONCRETE);
        guiColors.put("&8", Material.GRAY_CONCRETE);
        guiColors.put("&9", Material.LIGHT_BLUE_CONCRETE);
        guiColors.put("&a", Material.LIME_CONCRETE);
        guiColors.put("&b", Material.CYAN_GLAZED_TERRACOTTA);
        guiColors.put("&c", Material.PINK_CONCRETE);
        guiColors.put("&d", Material.MAGENTA_CONCRETE);
        guiColors.put("&e", Material.YELLOW_CONCRETE);
        guiColors.put("&f", Material.WHITE_CONCRETE);
        GUI_COLORS = Collections.unmodifiableMap(guiColors);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfigSettings();

        messages = new Messages(this, language);

        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                if (!playerDataFile.getParentFile().exists()) {
                    playerDataFile.getParentFile().mkdirs();
                }
                playerDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create playerdata.yml file: " + e.getMessage());
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        loadPlayerColors();

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(this.getCommand(COMMAND_NAME)).setTabCompleter(this);

        startAutoSaveTask();

        getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "==============================");
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + messages.getMessage("plugin-loaded"));
        getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "==============================");
    }

    @Override
    public void onDisable() {
        if (saveTask != null)
            saveTask.cancel();

        savePlayerColors();

        getServer().getConsoleSender().sendMessage(ChatColor.RED + "==============================");
        getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + messages.getMessage("plugin-disabled"));
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "==============================");
    }

    private void reloadConfigSettings() {
        reloadConfig();
        enableColor = getConfig().getBoolean("enable-color", true);
        defaultColor = getConfig().getString("default-color", "&f");
        language = normalizeLanguage(getConfig().getString("language", "zh"));
    }

    private String normalizeLanguage(String configuredLanguage) {
        if (configuredLanguage == null || configuredLanguage.isBlank()) {
            return "zh";
        }
        return configuredLanguage.toLowerCase(Locale.ROOT);
    }

    private void loadPlayerColors() {
        playerColors.clear();
        org.bukkit.configuration.ConfigurationSection colorsSection = playerDataConfig.getConfigurationSection(PLAYER_COLORS_PATH);
        if (colorsSection != null) {
            for (String uuidStr : colorsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String color = colorsSection.getString(uuidStr);
                    if (color != null) {
                        playerColors.put(uuid, color);
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid UUID in playerdata.yml: " + uuidStr);
                }
            }
        }
        playerDataDirty = false; // Mark as clean after loading
    }

    private synchronized void savePlayerColors() {
        if (!playerDataDirty) {
            return; // No changes to save
        }

        playerDataConfig.set(PLAYER_COLORS_PATH, null);

        for (Map.Entry<UUID, String> entry : playerColors.entrySet()) {
            playerDataConfig.set(PLAYER_COLORS_PATH + "." + entry.getKey(), entry.getValue());
        }
        try {
            playerDataConfig.save(playerDataFile);
            playerDataDirty = false; // Mark as clean after successful save
        } catch (IOException e) {
            getLogger().severe("Could not save playerdata.yml file: " + e.getMessage());
        }
    }

    private void startAutoSaveTask() {
        // Run save on the main thread to avoid concurrent access to shared YamlConfiguration
        // between periodic save and /chatcolor reload.
        saveTask = getServer().getScheduler().runTaskTimer(this,
                this::savePlayerColors, SAVE_INITIAL_DELAY_TICKS, SAVE_INTERVAL_TICKS);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enableColor)
            return;

        Player player = event.getPlayer();
        String colorCode = playerColors.getOrDefault(player.getUniqueId(), defaultColor);

        String coloredMsg = colorize(event.getMessage());
        ChatColor color = translateSingleColor(colorCode);

        event.setMessage((color != null ? color.toString() : "") + coloredMsg);
    }

    private ChatColor translateSingleColor(String code) {
        if (code == null)
            return ChatColor.WHITE;
        if (VALID_HEX_PATTERN.matcher(code).matches()) {
            try {
                return ChatColor.of(code);
            } catch (IllegalArgumentException ignored) {
                // Invalid hex color format
            }
        }
        if (code.startsWith("&") && code.length() == 2) {
            ChatColor c = ChatColor.getByChar(code.charAt(1));
            return c != null ? c : ChatColor.WHITE;
        }
        return ChatColor.WHITE;
    }

    private String colorize(String input) {
        if (input == null)
            return "";
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            try {
                matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
            } catch (IllegalArgumentException e) {
                // Invalid hex color, keep original text
                matcher.appendReplacement(buffer, matcher.group(0));
            }
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase(COMMAND_NAME))
            return false;

        if (args.length == 0) {
            if (!sender.hasPermission("chatcolor.use")) {
                sender.sendMessage(ChatColor.RED + messages.getMessage("no-permission"));
                return true;
            }
            List<String> helpLines = messages.getMessageList("command-help");
            for (String line : helpLines) {
                sender.sendMessage(line);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload":
                if (!sender.hasPermission("chatcolor.admin")) {
                    sender.sendMessage(ChatColor.RED + messages.getMessage("no-permission"));
                    return true;
                }
                reloadConfigSettings();
                messages.reload(language);
                playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
                loadPlayerColors();
                sender.sendMessage(ChatColor.GREEN + messages.getMessage("config-reloaded"));
                return true;

            case "set":
                if (!sender.hasPermission("chatcolor.use")) {
                    sender.sendMessage(ChatColor.RED + messages.getMessage("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + messages.getMessage("only-player"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + messages.getMessage("usage-set"));
                    return true;
                }
                String colorCode = args[1];
                if (!isValidColorCode(colorCode)) {
                    sender.sendMessage(ChatColor.RED + messages.getMessage("invalid-color"));
                    return true;
                }
                Player player = (Player) sender;
                playerColors.put(player.getUniqueId(), colorCode);
                playerDataDirty = true; // Mark data as dirty

                String coloredSample = generateColorSample(colorCode);
                sender.sendMessage(
                        ChatColor.GREEN + messages.getMessage("set-color-success").replace("%color%", coloredSample));
                return true;

            case "gui":
                if (!sender.hasPermission("chatcolor.use")) {
                    sender.sendMessage(ChatColor.RED + messages.getMessage("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + messages.getMessage("only-player"));
                    return true;
                }
                openColorGui((Player) sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + messages.getMessage("unknown-command"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase(COMMAND_NAME)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("chatcolor.use"))
                subCommands.add("set");
            if (sender.hasPermission("chatcolor.use"))
                subCommands.add("gui");
            if (sender.hasPermission("chatcolor.admin"))
                subCommands.add("reload");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            if (sender.hasPermission("chatcolor.use")) {
                StringUtil.copyPartialMatches(args[1], COLOR_CODES, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }

    private boolean isValidColorCode(String code) {
        if (code == null)
            return false;
        if (VALID_HEX_PATTERN.matcher(code).matches())
            return true;
        if (code.length() == 2 && code.charAt(0) == '&') {
            char colorChar = Character.toLowerCase(code.charAt(1));
            return VALID_CODES.indexOf(colorChar) >= 0;
        }
        return false;
    }

    /**
     * Generates a colored sample text for display purposes
     */
    private String generateColorSample(String colorCode) {
        return colorize(colorCode + "Sample");
    }

    private void openColorGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        for (Map.Entry<String, Material> entry : GUI_COLORS.entrySet()) {
            String code = entry.getKey();
            Material material = entry.getValue();

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(colorize(code + "Color " + code));
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to apply"));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
            }
            gui.addItem(item);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player)) {
            return;
        }

        if (!GUI_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        Player player = (Player) clicker;
        String selectedCode = getColorCodeByMaterial(event.getCurrentItem().getType());
        if (selectedCode == null) {
            return;
        }

        playerColors.put(player.getUniqueId(), selectedCode);
        playerDataDirty = true;
        player.closeInventory();

        String coloredSample = generateColorSample(selectedCode);
        player.sendMessage(ChatColor.GREEN + messages.getMessage("set-color-success").replace("%color%", coloredSample));
    }

    private String getColorCodeByMaterial(Material material) {
        for (Map.Entry<String, Material> entry : GUI_COLORS.entrySet()) {
            if (entry.getValue() == material) {
                return entry.getKey();
            }
        }
        return null;
    }
}
