package me.mathie.config;

import org.bukkit.configuration.file.FileConfiguration;
import me.mathie.DiscordWebhook;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handles loading and accessing webhook URLs from the plugin's config.yml.
 */
public class WebhookConfig {

    private static final Map<String, String> webhooks = new HashMap<>();

    /**
     * Loads webhook URLs from config.yml into memory.
     * Expects config to have a "webhooks" section with keys and URLs.
     * Example:
     *   webhooks:
     *     default: "https://discord.com/api/webhooks/..."
     */
    public static void loadConfig() {
        DiscordWebhook plugin = DiscordWebhook.getInstance();
        plugin.saveDefaultConfig();

        FileConfiguration config = plugin.getConfig();

        webhooks.clear();
        if (config.isConfigurationSection("webhooks")) {
            Set<String> keys = config.getConfigurationSection("webhooks").getKeys(false);
            for (String key : keys) {
                String url = config.getString("webhooks." + key);
                if (url != null && url.startsWith("https://")) {
                    webhooks.put(key, url);
                }
            }
        }
    }

    /**
     * Gets the webhook URL for the given key, or null if not found.
     * @param webhookKey Key from config.yml (e.g., "default", "staff", etc.)
     * @return Discord webhook URL or null.
     */
    public static String getWebhookUrl(String webhookKey) {
        return webhooks.get(webhookKey);
    }

    /**
     * Gets the default webhook URL (for key "default").
     * @return Default webhook URL or null.
     */
    public static String getDefaultWebhookUrl() {
        return webhooks.get("default");
    }
}

