package com.zFlqw.chatcolors;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Messages {

    private final JavaPlugin plugin;
    private FileConfiguration messagesConfig;
    private String lang;

    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    public Messages(JavaPlugin plugin, String lang) {
        this.plugin = plugin;
        this.lang = normalizeLanguage(lang);
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages_" + lang + ".yml");
        if (!file.exists()) {
            plugin.saveResource("messages_" + lang + ".yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void reload(String lang) {
        String normalizedLang = normalizeLanguage(lang);
        if (normalizedLang.equals(this.lang)) {
            return;
        }
        this.lang = normalizedLang;
        load();
    }

    private String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) {
            return "zh";
        }
        return lang.toLowerCase(Locale.ROOT);
    }

    private String format(String raw) {
        if (raw == null) {
            return null;
        }

        String msg = raw;
        String prefix = messagesConfig.getString("prefix", "");
        if (prefix != null && !prefix.isBlank()) {
            msg = msg.replace("%prefix%", prefix);
        }

        return applyColors(msg);
    }

    /**
     * Supports both '&' legacy codes and "#RRGGBB" hex colors.
     */
    private String applyColors(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = "#" + matcher.group(1);
            try {
                String replacement = ChatColor.of(hex).toString();
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            } catch (IllegalArgumentException e) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public String getMessage(String key) {
        String raw = messagesConfig.getString(key);
        if (raw == null) {
            return key;
        }
        return format(raw);
    }

    public List<String> getMessageList(String key) {
        if (!messagesConfig.contains(key)) {
            return Collections.emptyList();
        }

        List<String> list = messagesConfig.getStringList(key);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> formatted = new ArrayList<>(list.size());
        for (String line : list) {
            String out = format(line);
            formatted.add(out == null ? "" : out);
        }
        return formatted;
    }
}
