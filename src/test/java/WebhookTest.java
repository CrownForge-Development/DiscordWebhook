import dev.crownforge.Model.RequestType;
import dev.crownforge.api.WebhookAPI;
import dev.crownforge.Model.WebHookResponse;
import dev.crownforge.manager.WebhookQueue;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

public class WebhookTest {

    public static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1376567162579189873/aDelXlVVf-mJmhOv1j8LNOxfhlGgL1yUHAGcmncG_LenhASgqQCTWU9PrScsVJEo3oeu";
    public static final String WEBHOOK_URL_2 = "https://discord.com/api/webhooks/1376568096646496429/Zz4GiYUs3IWwXA84yivLHp5HJ4zA4-7Ly6frnJDn28H3cM3FFQrHus7tqTVSqq0Ic1PC";

    @Test
    public void TestSendAndDeleteMessage() {
        String MESSAGE_CONTENT = "This is a quick unit test message!";

        WebHookResponse response = WebhookAPI.sendMessage(WEBHOOK_URL, MESSAGE_CONTENT);
        assert response != null;
        assert response.isSuccess();
        String sentMessageId = response.getMessageId();

        boolean deleteResult = WebhookAPI.deleteMessage(WEBHOOK_URL, sentMessageId);
        assert deleteResult;
    }


    @Test
    public void TestSendChangeAndDeleteMessage() {

        String MESSAGE_CONTENT = "This is a quick unit test message!";

        WebHookResponse response = WebhookAPI.sendMessage(WEBHOOK_URL, MESSAGE_CONTENT);
        assert response != null;
        assert response.isSuccess();
        String sentMessageId = response.getMessageId();

        String newContent = "This is a quick unit test message that has been changed!";
        boolean result = WebhookAPI.editMessage(WEBHOOK_URL, sentMessageId, newContent);
        assert result;

        boolean deleteResult = WebhookAPI.deleteMessage(WEBHOOK_URL, sentMessageId);
        assert deleteResult;
    }

    @Test
    public void TestLargeMessage() {
        StringBuilder largeMessage = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            largeMessage.append("A");
        }

        WebHookResponse response = WebhookAPI.sendMessage(WEBHOOK_URL, largeMessage.toString());
        assert response != null;
        assert response.isSuccess();
        String sentMessageId = response.getMessageId();

        boolean deleteResult = WebhookAPI.deleteMessage(WEBHOOK_URL, sentMessageId);
        assert deleteResult;
    }

    @Test
    public void TestMultipleMessages() {
        int messageCount = 20;
        String[] messageIds = new String[messageCount];

        for (int i = 0; i < messageCount; i++) {
            String content = "Test message " + (i + 1);
            WebHookResponse response = WebhookAPI.sendMessage(WEBHOOK_URL, content);
            assert response != null;
            assert response.isSuccess();
            messageIds[i] = response.getMessageId();
        }

        for (String messageId : messageIds) {
            boolean deleteResult = WebhookAPI.deleteMessage(WEBHOOK_URL, messageId);
            assert deleteResult;
        }
    }

    @Test
    public void TestMultipleMessagesMultipleWebhooks() {
        int messageCount = 10;
        String[] messageIdsWebhook1 = new String[messageCount];
        String[] messageIdsWebhook2 = new String[messageCount];

        for (int i = 0; i < messageCount; i++) {
            String content1 = "Webhook 1 - Test message " + (i + 1);
            WebHookResponse response1 = WebhookAPI.sendMessage(WEBHOOK_URL, content1);
            assert response1 != null;
            assert response1.isSuccess();
            messageIdsWebhook1[i] = response1.getMessageId();

            String content2 = "Webhook 2 - Test message " + (i + 1);
            WebHookResponse response2 = WebhookAPI.sendMessage(WEBHOOK_URL_2, content2);
            assert response2 != null;
            assert response2.isSuccess();
            messageIdsWebhook2[i] = response2.getMessageId();
        }

        for (String messageId : messageIdsWebhook1) {
            boolean deleteResult = WebhookAPI.deleteMessage(WEBHOOK_URL, messageId);
            assert deleteResult;
        }

        for (String messageId : messageIdsWebhook2) {
            boolean deleteResult = WebhookAPI.deleteMessage(WEBHOOK_URL_2, messageId);
            assert deleteResult;
        }
    }
}
