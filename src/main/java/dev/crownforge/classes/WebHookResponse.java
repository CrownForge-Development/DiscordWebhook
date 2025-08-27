package dev.crownforge.classes;

/**
 * Represents the result of sending or deleting a Discord webhook message.
 */
public class WebHookResponse {
    private final boolean success;
    private final int statusCode;
    private final String messageId;

    public WebHookResponse(boolean success, int statusCode, String messageId) {
        this.success = success;
        this.statusCode = statusCode;
        this.messageId = messageId;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessageId() {
        return messageId;
    }
}
