package dev.crownforge.manager;

import dev.crownforge.Model.RequestType;
import dev.crownforge.Model.WebHookResponse;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Handles sending and deleting messages via Discord webhooks.
 * Accepts either a direct webhook URL or a config key (for use with config.yml).
 * If sending fails, will attempt to fall back to a default webhook if configured.
 */
public class WebhookManager {

    private static final Logger logger = Logger.getLogger(WebhookManager.class.getName());

    private static final Map<String, WebhookQueue> webhookQueues = new ConcurrentHashMap<>();

    private static WebhookQueue getQueue(String webhookUrl) {
        return webhookQueues.computeIfAbsent(webhookUrl, url -> new WebhookQueue(url, logger));
    }

    /**
     * Tries to send a message to the given webhook (URL or config key).
     * If this fails, sends to the default webhook if one is set.
     * Returns a WebHookResponse with status, code, and (if present) messageId.
     *
     * @param webhookUrl Discord webhook URL or key from config.
     * @param content The message content.
     * @param callback Callback to handle the response.
     * @return WebHookResponse object with status and optional messageId.
     */
    public static void sendMessage(String webhookUrl, String content, Consumer<WebHookResponse> callback) {

        if (webhookUrl == null || content == null || content.isEmpty()) {
            logger.severe("[DiscordWebhook] Invalid webhook URL or message content.");
            if (callback != null) {
                callback.accept(new WebHookResponse(false, 0, null));
            }
            return;
        }

        // Discord has a message limit of 2000 characters
        if (content.length() > 2000) {
            logger.warning("[DiscordWebhook] Message exceeds 2000 character limit. Truncating.");
            content = content.substring(0, 2000);
        }

        WebhookQueue queue = getQueue(webhookUrl);
        queue.addTask(null, content, RequestType.POST, callback);
    }

    /**
     * Deletes a Discord webhook message.
     * Supports both direct webhook URLs and config keys.
     * If the first attempt fails, will fall back to the default webhook if one is set.
     * @param webhookUrl Discord webhook URL.
     * @param messageId Discord message ID to delete.
     * @param callback Callback to handle the response.
     * @return true if deletion succeeded, false otherwise.
     */
    public static void deleteMessage(String webhookUrl, String messageId, Consumer<WebHookResponse> callback) {
        if (webhookUrl == null || messageId == null || messageId.isEmpty()) {
            logger.severe("[DiscordWebhook] Invalid webhook URL or message ID.");
            if (callback != null) {
                callback.accept(new WebHookResponse(false, 0, null));
            }
            return;
        }

        WebhookQueue queue = getQueue(webhookUrl);
        queue.addTask(messageId, null, RequestType.DELETE, callback);
    }


    /**
     * Edits a previously sent Discord webhook message.
     *
     * @param webhookUrl Discord webhook URL or config key
     * @param messageId  ID of the message to edit
     * @param newContent The new message content
     * @return true if edit succeeded, false otherwise
     */
    public static boolean editMessage(String webhookUrl, String messageId, String newContent) throws Exception {

        //URL url = new URL(webhookUrl + "/messages/" + messageId);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPatch patch = new HttpPatch(webhookUrl + "/messages/" + messageId);
            patch.setHeader("Content-Type", "application/json");

            String json = String.format("{\"content\": \"%s\"}", escapeJson(newContent));
            patch.setEntity(new StringEntity(json));

            try (CloseableHttpResponse response = httpClient.execute(patch)) {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    System.out.println("Message edited successfully!");
                } else {
                    throw new RuntimeException("Failed to edit message: " + status);
                }
            }
        }

        return true;
    }

    private static String escapeJson(String text) {
        return text.replace("\"", "\\\"");
    }

}
