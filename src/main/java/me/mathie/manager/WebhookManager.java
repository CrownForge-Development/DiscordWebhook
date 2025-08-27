package me.mathie.manager;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import me.mathie.config.WebhookConfig;
import me.mathie.classes.WebHookResponse;
import org.bukkit.Bukkit;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles sending and deleting messages via Discord webhooks.
 * Accepts either a direct webhook URL or a config key (for use with config.yml).
 * If sending fails, will attempt to fall back to a default webhook if configured.
 */
public class WebhookManager {

    /**
     * Tries to send a message to the given webhook (URL or config key).
     * If this fails, sends to the default webhook if one is set.
     * Returns a WebHookResponse with status, code, and (if present) messageId.
     *
     * @param webhookOrKey Discord webhook URL or key from config.
     * @param content The message content.
     * @return WebHookResponse object with status and optional messageId.
     */
    public static WebHookResponse sendMessage(String webhookOrKey, String content) {
        String webhookUrl = null;

        if (webhookOrKey != null && webhookOrKey.startsWith("https://discord.com/api/webhooks/")) {
            webhookUrl = webhookOrKey;
            Bukkit.getLogger().info("[DiscordWebhook] sendMessage called with direct URL.");
        } else {
            webhookUrl = WebhookConfig.getWebhookUrl(webhookOrKey);
            Bukkit.getLogger().info("[DiscordWebhook] sendMessage called with key: " + webhookOrKey);
        }

        WebHookResponse response = null;
        if (webhookUrl != null) {
            Bukkit.getLogger().info("[DiscordWebhook] Attempting to send to webhook: " + webhookUrl);
            response = sendToWebhook(webhookUrl, content);
        } else {
            Bukkit.getLogger().warning("[DiscordWebhook] No valid webhook found for: " + webhookOrKey);
            return new WebHookResponse(false, 0, null);
        }

        if (!response.isSuccess()) {
            String defaultWebhook = WebhookConfig.getDefaultWebhookUrl();
            if (defaultWebhook != null && !defaultWebhook.equals(webhookUrl)) {
                Bukkit.getLogger().warning("[DiscordWebhook] Primary webhook failed, attempting default: " + defaultWebhook);
                response = sendToWebhook(defaultWebhook, content);
            } else {
                Bukkit.getLogger().severe("[DiscordWebhook] No default webhook configured or already tried.");
            }
        }

        return response;
    }

    /**
     * Sends the message to a Discord webhook URL and parses the response.
     * @param webhookUrl Full Discord webhook URL.
     * @param content The message content.
     * @return WebHookResponse object.
     */
    private static WebHookResponse sendToWebhook(String webhookUrl, String content) {
        if (webhookUrl == null) return new WebHookResponse(false, 0, null);

        webhookUrl += webhookUrl.contains("?") ? "&wait=true" : "?wait=true";

        int responseCode = 0;
        String messageId = null;

        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.addRequestProperty("Content-Type", "application/json");

            JsonObject json = new JsonObject();
            json.addProperty("content", content);

            String payload = new Gson().toJson(json);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes());
            }

            responseCode = conn.getResponseCode();
            Bukkit.getLogger().info("[DiscordWebhook] Code: " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream is = conn.getInputStream();
                     java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A")) {
                    String body = s.hasNext() ? s.next() : "";
                    Bukkit.getLogger().info("[DiscordWebhook] Response body: " + body);

                    JsonObject resp = new Gson().fromJson(body, JsonObject.class);
                    if (resp != null && resp.has("id")) {
                        messageId = resp.get("id").getAsString();
                    }
                }
            }

            return new WebHookResponse(responseCode >= 200 && responseCode < 300, responseCode, messageId);

        } catch (Exception e) {
            Bukkit.getLogger().severe("[DiscordWebhook] Error: " + e.getMessage());
            e.printStackTrace();
            return new WebHookResponse(false, responseCode, null);
        }
    }

    /**
     * Deletes a Discord webhook message.
     * Supports both direct webhook URLs and config keys.
     * If the first attempt fails, will fall back to the default webhook if one is set.
     * @param webhookOrKey Discord webhook URL or key from config.
     * @param messageId Discord message ID to delete.
     * @return true if deletion succeeded, false otherwise.
     */
    public static boolean deleteMessage(String webhookOrKey, String messageId) {
        String webhookUrl;
        if (webhookOrKey != null && webhookOrKey.startsWith("https://discord.com/api/webhooks/")) {
            webhookUrl = webhookOrKey;
            Bukkit.getLogger().info("[DiscordWebhook] deleteMessage called with direct URL.");
        } else {
            webhookUrl = WebhookConfig.getWebhookUrl(webhookOrKey);
            Bukkit.getLogger().info("[DiscordWebhook] deleteMessage called with key: " + webhookOrKey);
        }

        if (webhookUrl == null) {
            webhookUrl = WebhookConfig.getDefaultWebhookUrl();
            if (webhookUrl == null) {
                Bukkit.getLogger().severe("[DiscordWebhook] No webhook found for key: " + webhookOrKey);
                return false;
            }
        }

        String deleteUrl = webhookUrl + "/messages/" + messageId;
        try {
            URL url = new URL(deleteUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");

            int responseCode = connection.getResponseCode();
            Bukkit.getLogger().info("[DiscordWebhook] Webhook delete response code: " + responseCode);
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[DiscordWebhook] Error deleting webhook message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Edits a previously sent Discord webhook message.
     * @param webhookOrKey Discord webhook URL or config key
     * @param messageId ID of the message to edit
     * @param newContent The new message content
     * @return true if edit succeeded, false otherwise
     */
    public static boolean editMessage(String webhookOrKey, String messageId, String newContent) {
        String webhookUrl;
        if (webhookOrKey != null && webhookOrKey.startsWith("https://discord.com/api/webhooks/")) {
            webhookUrl = webhookOrKey;
        } else {
            webhookUrl = WebhookConfig.getWebhookUrl(webhookOrKey);
        }

        if (webhookUrl == null) {
            webhookUrl = WebhookConfig.getDefaultWebhookUrl();
            if (webhookUrl == null) {
                Bukkit.getLogger().severe("[DiscordWebhook] No webhook found for key: " + webhookOrKey);
                return false;
            }
        }

        String editUrl = webhookUrl + "/messages/" + messageId;
        try {
            URL url = new URL(editUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("content", newContent);

            String payload = new com.google.gson.Gson().toJson(json);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes());
            }

            int responseCode = connection.getResponseCode();
            Bukkit.getLogger().info("[DiscordWebhook] Webhook edit response code: " + responseCode);
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[DiscordWebhook] Error editing webhook message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}
