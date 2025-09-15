package dev.crownforge.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.crownforge.Model.RequestType;
import dev.crownforge.Model.WebHookQueueStatus;
import dev.crownforge.Model.WebHookResponse;
import dev.crownforge.Model.WebhookTask;
import lombok.Getter;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpClient;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class WebhookQueue {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final BlockingQueue<WebhookTask> queue = new LinkedBlockingQueue<>();

    private final String webhookUrl;
    private final Logger logger;

    @Getter
    private WebHookQueueStatus status = WebHookQueueStatus.IDLE;

    public WebhookQueue(String webhookUrl, Logger logger) {
        this.webhookUrl = webhookUrl;

        webhookUrl += webhookUrl.contains("?") ? "&wait=true" : "?wait=true";

        this.logger = logger;
    }

    public void addTask(String messageId, String payload, RequestType type, Consumer<WebHookResponse> method) {
        logger.info("Adding task to webhook queue");
        addTask(new WebhookTask(messageId, payload,type, method));
    }

    public void addTask(WebhookTask task) {
        queue.add(task);

        if (status == WebHookQueueStatus.IDLE) {
            processQueue();
        }
    }


    private void processQueue() {

        //if the last task failed it should be waiting for 5 seconds before retrying
        if(status == WebHookQueueStatus.WAITING_RETRY){
            try{
                logger.info("[DiscordWebhook] Waiting 5 seconds before retrying webhook task.");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("[DiscordWebhook]" + e.getMessage() + " while waiting to retry webhook task.");
            }

            status = WebHookQueueStatus.WORKING;
        }

        //get the task from the queue
        WebhookTask task = queue.poll();

        //if the queue is empty set status to idle and return
        if (task == null) {
            logger.info("[DiscordWebhook] Queue is empty, setting status to IDLE.");
            status = WebHookQueueStatus.IDLE;
            return;
        }

        logger.info("[DiscordWebhook] Processing task: " + task.getRequestType() + " for messageId: " + task.getMessageId());

        try{
            int responseCode = 0;
            String messageId = null;

            URL url = new URL(
                    webhookUrl.replace("?wait=true", "") +
                            (task.getMessageId() != null ? "/messages/" + task.getMessageId() : "") +
                            (webhookUrl.contains("?") ? "&wait=true" : "?wait=true")
            );

            logger.info("[DiscordWebhook] " + task.getMessageId() + " sending request to " + url.toString());

            //Create the connection
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod( task.getRequestType().toString() );
            conn.setDoOutput(true);

            //if the task has a payload add it to the request
            if(task.getPayload() != null) {
                conn.addRequestProperty("Content-Type", "application/json");

                JsonObject json = new JsonObject();
                json.addProperty("content", task.getPayload());

                String payload = new Gson().toJson(json);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes());
                }
            }

            //get the response code
            responseCode = conn.getResponseCode();
            logger.info("[DiscordWebhook] Code: " + responseCode);

            //if the response code is 2xx, call the response handler with success
            if(responseCode >= 200 && responseCode < 300){
                if(task.getRequestType() == RequestType.POST) {

                    logger.info("[DiscordWebhook] i got here because i was a post request ");

                    try (var is = conn.getInputStream();
                         var s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A")) {
                        String body = s.hasNext() ? s.next() : "";
                        logger.info("[DiscordWebhook] Response body: " + body);

                        JsonObject resp = new Gson().fromJson(body, JsonObject.class);
                        if (resp != null && resp.has("id")) {
                            messageId = resp.get("id").getAsString();
                        }
                    }
                }

                task.getResponseHandler().accept(new WebHookResponse(true, responseCode, messageId));
                processQueue();
                return;
            }

            //if the response code is 4xx or 5xx, log the error and retry the task later
            if(responseCode >= 500 && responseCode < 600 || responseCode == 429){
                logger.severe("[DiscordWebhook] Received error response code: " + responseCode + " for task type: " + task.getRequestType());

                status = WebHookQueueStatus.WAITING_RETRY;

                queue.add(task);

                processQueue();
                return;
            }

            //if the response code is 4xx, call the response handler with failure
            if(responseCode >= 400 && responseCode < 500){
                logger.severe("[DiscordWebhook] Received client error response code: " + responseCode + " for task type: " + task.getRequestType());

                task.getResponseHandler().accept(new WebHookResponse(false, responseCode, null));
                processQueue();
                return;
            }

            task.getResponseHandler().accept(new WebHookResponse(false, responseCode, messageId));
            Logger.getLogger("[DiscordWebhook] Unknown response code: " + responseCode + " for task type: " + task.getRequestType());

        }catch(Exception e){
            logger.severe("[DiscordWebhook] could not process URL or Connection. Error: " + e.getMessage());

            task.getResponseHandler().accept(new WebHookResponse(false, 0, null));
            processQueue();
            return;
        }
    }



}
