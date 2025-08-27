package dev.crownforge;

import dev.crownforge.config.WebhookConfig;
import org.bukkit.plugin.java.JavaPlugin;


public class DiscordWebhook extends JavaPlugin {

    private static DiscordWebhook instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        WebhookConfig.loadConfig();
        getLogger().info("DiscordWebhook enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("DiscordWebhook disabled!");
    }

    public static DiscordWebhook getInstance() {
        return instance;
    }
}
