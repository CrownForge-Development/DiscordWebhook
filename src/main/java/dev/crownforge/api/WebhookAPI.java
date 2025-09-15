package dev.crownforge.api;

import dev.crownforge.Model.WebHookResponse;
import dev.crownforge.manager.WebhookManager;

/**
 * API for sendin, deleting and editing Discord webhook messages.
 * Use this in your other plugins to send, delete or edit messages.
 */
public class WebhookAPI {

    /**
     * Sends a message to a Discord webhook.
     * If a direct URL is given, tries that first; if it fails, sends to the default webhook (if set).
     * If only a key is given, sends to the URL configured in config.yml; if that fails, falls back to default.
     * @param webhookOrKey Either a Discord webhook URL or a key from config.yml
     * @param message The message to send
     * @return WebHookResponse with status and optional messageId.
     */
    @Deprecated
    public static WebHookResponse sendMessage(String webhookOrKey, String message) {
        final WebHookResponse[] responseHolder = new WebHookResponse[1];
        WebhookManager.sendMessage(webhookOrKey, message, webHookResponse -> {
            responseHolder[0] = webHookResponse;
        });
        return responseHolder[0];
    }

    /**
     * Sends a message to a Discord webhook.
     * Message gets added to a queue and sent but when failed the system will waitt 5 seconds before retrying.
     * @param webhookOrKey Either a Discord webhook URL or a key from config.yml
     * @param message The message to send
     * @param callback Callback to handle the WebHookResponse
     * @return WebHookResponse with status and optional messageId.
     */
    public static void sendMessageAsync(String webhookOrKey, String message, java.util.function.Consumer<WebHookResponse> callback) {
        WebhookManager.sendMessage(webhookOrKey, message, callback);
    }

    /**
     * Deletes a message by ID via webhook.
     * @param webhookOrKey Discord webhook URL or key from config.yml
     * @param messageId ID of the message to delete
     * @return true if deletion succeeded
     */
    @Deprecated
    public static boolean deleteMessage(String webhookOrKey, String messageId) {
        final Boolean[] responseHolder = new Boolean[1];
        WebhookManager.deleteMessage(webhookOrKey, messageId , webHookResponse -> {
            responseHolder[0] = webHookResponse.isSuccess();
        });
        return responseHolder[0] != null && responseHolder[0];
    }

    /**
     * Deletes a message by ID via webhook.
     * delete action gets added to a queue and sent but when failed the system will waitt 5 seconds before retrying.
     * @param webhookOrKey Discord webhook URL or key from config.yml
     * @param messageId ID of the message to delete
     * @param callback Callback to handle the WebHookResponse
     * @return true if deletion succeeded
     */
    public static void deleteMessageAsync(String webhookOrKey, String messageId, java.util.function.Consumer<WebHookResponse> callback) {
        WebhookManager.deleteMessage(webhookOrKey, messageId , callback);
    }

    /**
     * Edits a message sent by a webhook.
     * @param webhookOrKey Discord webhook URL or config key
     * @param messageId Message ID to edit
     * @param newContent New message content
     * @return true if edit succeeded
     * @Warning This method could fail there is no async version of this method.
     */
    public static boolean editMessage(String webhookOrKey, String messageId, String newContent) {
        try {
            return WebhookManager.editMessage(webhookOrKey, messageId, newContent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
