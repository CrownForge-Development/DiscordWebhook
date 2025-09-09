package dev.crownforge.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.crownforge.classes.WebHookResponse;
import dev.crownforge.config.WebhookConfig;
import org.bukkit.Bukkit;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles sending, deleting, and editing messages via Discord webhooks.
 * Adds per-URL queuing and respectful rate-limit retries (429) with Retry-After.
 */
public class WebhookManager {

    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;
    private static final int MAX_RETRIES = 3;

    // ---------------------------
    // Public API
    // ---------------------------

    public static WebHookResponse sendMessage(String webhookOrKey, String content) {
        String webhookUrl = resolveWebhookUrl(webhookOrKey);
        if (webhookUrl == null) {
            Bukkit.getLogger().severe("[DiscordWebhook] No webhook URL available (missing key and no default).");
            return new WebHookResponse(false, 400, null);
        }

        final String url = webhookUrl;
        try {
            // Serialize per-URL and auto-handle 429s
            java.util.concurrent.Future<WebHookResponse> fut =
                    WebhookQueues.submit(url, () -> sendWithRetries(url, content));
            WebHookResponse resp = fut.get();

            // Fallback to default if primary failed and default differs
            if (!resp.isSuccess()) {
                String def = WebhookConfig.getDefaultWebhookUrl();
                if (def != null && !def.equals(url)) {
                    WebHookResponse fallback =
                            WebhookQueues.submit(def, () -> sendWithRetries(def, content)).get();
                    return fallback;
                }
            }
            return resp;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[DiscordWebhook] Error sending webhook message: " + e.getMessage());
            e.printStackTrace();
            return new WebHookResponse(false, 500, null);
        }
    }

    public static boolean deleteMessage(String webhookOrKey, String messageId) {
        String webhookUrl = resolveWebhookUrl(webhookOrKey);
        if (webhookUrl == null) {
            Bukkit.getLogger().severe("[DiscordWebhook] No webhook URL available for delete.");
            return false;
        }

        final String target = webhookUrl + "/messages/" + messageId;
        try {
            WebHookResponse resp = WebhookQueues
                    .submit(webhookUrl, () -> deleteWithRetries(target))
                    .get();
            return resp.isSuccess();
        } catch (Exception e) {
            Bukkit.getLogger().severe("[DiscordWebhook] Error deleting webhook message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean editMessage(String webhookOrKey, String messageId, String newContent) {
        String webhookUrl = resolveWebhookUrl(webhookOrKey);
        if (webhookUrl == null) {
            Bukkit.getLogger().severe("[DiscordWebhook] No webhook URL available for edit.");
            return false;
        }

        final String target = webhookUrl + "/messages/" + messageId;
        try {
            WebHookResponse resp = WebhookQueues
                    .submit(webhookUrl, () -> editWithRetries(target, newContent))
                    .get();
            return resp.isSuccess();
        } catch (Exception e) {
            Bukkit.getLogger().severe("[DiscordWebhook] Error editing webhook message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // --------------------------------
    // Internal: resolve and fallbacks
    // --------------------------------

    private static String resolveWebhookUrl(String webhookOrKey) {
        String webhookUrl = null;
        if (webhookOrKey != null && webhookOrKey.startsWith("http")) {
            webhookUrl = webhookOrKey;
            Bukkit.getLogger().info("[DiscordWebhook] Using direct webhook URL.");
        } else if (webhookOrKey != null) {
            webhookUrl = WebhookConfig.getWebhookUrl(webhookOrKey);
            Bukkit.getLogger().info("[DiscordWebhook] Using webhook key: " + webhookOrKey);
        }

        if (webhookUrl == null) {
            webhookUrl = WebhookConfig.getDefaultWebhookUrl();
            if (webhookUrl != null) {
                Bukkit.getLogger().warning("[DiscordWebhook] Falling back to default webhook.");
            }
        }
        return webhookUrl;
    }

    // --------------------------------
    // Internal: network + retries
    // --------------------------------

    private static WebHookResponse sendWithRetries(String webhookUrl, String content) {
        int attempt = 0;
        while (true) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                // wait=true makes Discord return the created message JSON (incl. "id")
                conn = open(webhookUrl + "?wait=true", "POST");
                conn.setDoOutput(true);

                JsonObject json = new JsonObject();
                json.addProperty("content", content == null ? "" : content);
                byte[] payload = GSON.toJson(json).getBytes(java.nio.charset.StandardCharsets.UTF_8);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int code = conn.getResponseCode();
                boolean ok = code >= 200 && code < 300;
                String body = readBody(conn, ok);

                if (code == 429) {
                    int waitMs = getRetryAfterMillis(conn, body);
                    Bukkit.getLogger().warning("[DiscordWebhook] 429 on send. Retry in " + waitMs + "ms (attempt "
                            + attempt + "/" + (MAX_RETRIES + 1) + ")");
                    Thread.sleep(waitMs);
                    if (attempt <= MAX_RETRIES) continue;
                }

                if (ok) {
                    String messageId = null;
                    try {
                        JsonObject obj = GSON.fromJson(body, JsonObject.class);
                        if (obj != null && obj.has("id")) messageId = obj.get("id").getAsString();
                    } catch (Exception ignored) {}
                    Bukkit.getLogger().info("[DiscordWebhook] Send OK " + code);
                    return new WebHookResponse(true, code, messageId);
                } else {
                    Bukkit.getLogger().warning("[DiscordWebhook] Send failed " + code + " body=" + body);
                    return new WebHookResponse(false, code, null);
                }
            } catch (Exception ex) {
                Bukkit.getLogger().severe("[DiscordWebhook] Error sending webhook message: " + ex.getMessage());
                if (attempt <= MAX_RETRIES) {
                    try { Thread.sleep(500L * attempt); } catch (InterruptedException ignored) {}
                    continue;
                }
                return new WebHookResponse(false, 500, null);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    private static WebHookResponse deleteWithRetries(String targetUrl) {
        int attempt = 0;
        while (true) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                conn = open(targetUrl, "DELETE");
                int code = conn.getResponseCode();
                boolean ok = code >= 200 && code < 300;
                String body = readBody(conn, ok);

                if (code == 429) {
                    int waitMs = getRetryAfterMillis(conn, body);
                    Bukkit.getLogger().warning("[DiscordWebhook] 429 on delete. Retry in " + waitMs + "ms (attempt "
                            + attempt + "/" + (MAX_RETRIES + 1) + ")");
                    try { Thread.sleep(waitMs); } catch (InterruptedException ignored) {}
                    if (attempt <= MAX_RETRIES) continue;
                }

                Bukkit.getLogger().info("[DiscordWebhook] Delete " + (ok ? "OK " : "fail ") + code);
                return new WebHookResponse(ok, code, null);
            } catch (Exception ex) {
                Bukkit.getLogger().severe("[DiscordWebhook] Error deleting webhook message: " + ex.getMessage());
                if (attempt <= MAX_RETRIES) {
                    try { Thread.sleep(500L * attempt); } catch (InterruptedException ignored) {}
                    continue;
                }
                return new WebHookResponse(false, 500, null);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    private static WebHookResponse editWithRetries(String targetUrl, String newContent) {
        int attempt = 0;
        while (true) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                conn = open(targetUrl, "PATCH");
                conn.setDoOutput(true);

                JsonObject json = new JsonObject();
                json.addProperty("content", newContent == null ? "" : newContent);
                byte[] payload = GSON.toJson(json).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int code = conn.getResponseCode();
                boolean ok = code >= 200 && code < 300;
                String body = readBody(conn, ok);

                if (code == 429) {
                    int waitMs = getRetryAfterMillis(conn, body);
                    Bukkit.getLogger().warning("[DiscordWebhook] 429 on edit. Retry in " + waitMs + "ms (attempt "
                            + attempt + "/" + (MAX_RETRIES + 1) + ")");
                    try { Thread.sleep(waitMs); } catch (InterruptedException ignored) {}
                    if (attempt <= MAX_RETRIES) continue;
                }

                Bukkit.getLogger().info("[DiscordWebhook] Edit " + (ok ? "OK " : "fail ") + code);
                return new WebHookResponse(ok, code, null);
            } catch (Exception ex) {
                Bukkit.getLogger().severe("[DiscordWebhook] Error editing webhook message: " + ex.getMessage());
                if (attempt <= MAX_RETRIES) {
                    try { Thread.sleep(500L * attempt); } catch (InterruptedException ignored) {}
                    continue;
                }
                return new WebHookResponse(false, 500, null);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    // --------------------------------
    // Low-level helpers
    // --------------------------------

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setDoInput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Connection", "keep-alive");
        return c;
    }

    private static String readBody(HttpURLConnection conn, boolean success) throws Exception {
        InputStream is = success ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (is; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static int getRetryAfterMillis(HttpURLConnection conn, String body) {
        String h = conn.getHeaderField("Retry-After");
        if (h != null) {
            try { return (int) Math.ceil(Double.parseDouble(h) * 1000.0); } catch (Exception ignored) {}
            try { return Integer.parseInt(h) * 1000; } catch (Exception ignored) {}
        }
        try {
            if (body != null && !body.isEmpty()) {
                JsonObject obj = GSON.fromJson(body, JsonObject.class);
                if (obj != null && obj.has("retry_after")) {
                    return (int) Math.ceil(obj.get("retry_after").getAsDouble() * 1000.0);
                }
            }
        } catch (Exception ignored) {}
        return 1000;
    }
}
