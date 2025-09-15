package dev.crownforge.Model;

import lombok.Getter;

import java.util.function.Consumer;

public class WebhookTask {

    @Getter
    final String messageId;

    @Getter
    final RequestType requestType;

    @Getter
    final String payload;

    @Getter
    final Consumer<WebHookResponse> responseHandler;

    @Getter
    int attempts = 5;

    @Getter
    long nextAttemptAt = 0; // timestamp in ms for when it can be retried

    public WebhookTask(String messageId, String payload, RequestType requestType,Consumer<WebHookResponse> responseHandler) {
        this.messageId = messageId;
        this.payload = payload;
        this.requestType = requestType;
        this.responseHandler = responseHandler;

        checkPayload();
    }

    public WebhookTask(String messageId, RequestType requestType,Consumer<WebHookResponse> responseHandler) {
        this.messageId = messageId;
        this.payload = null;
        this.requestType = requestType;
        this.responseHandler = responseHandler;

        checkPayload();
    }

    public WebhookTask(RequestType requestType, Consumer<WebHookResponse> responseHandler) {
        this.messageId = null;
        this.payload = null;
        this.requestType = requestType;
        this.responseHandler = responseHandler;

        checkPayload();
    }

    private void checkPayload(){
        if(payload == null){
            return;
        }

        if( payload.length() > 2000){
            throw new IllegalArgumentException("Payload exceeds Discord's 2000 character limit.");
        }
    }
}
