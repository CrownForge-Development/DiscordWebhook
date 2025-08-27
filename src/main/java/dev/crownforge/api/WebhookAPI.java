package dev.crownforge.api;

import dev.crownforge.classes.WebHookResponse;
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
    public static WebHookResponse sendMessage(String webhookOrKey, String message) {
        return WebhookManager.sendMessage(webhookOrKey, message);
    }

    /**
     * Deletes a message by ID via webhook.
     * @param webhookOrKey Discord webhook URL or key from config.yml
     * @param messageId ID of the message to delete
     * @return true if deletion succeeded
     */
    public static boolean deleteMessage(String webhookOrKey, String messageId) {
        return WebhookManager.deleteMessage(webhookOrKey, messageId);
    }

    /**
     * Edits a message sent by a webhook.
     * @param webhookOrKey Discord webhook URL or config key
     * @param messageId Message ID to edit
     * @param newContent New message content
     * @return true if edit succeeded
     */
    public static boolean editMessage(String webhookOrKey, String messageId, String newContent) {
        return WebhookManager.editMessage(webhookOrKey, messageId, newContent);
    }

}
