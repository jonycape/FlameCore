package me.jonycape.dev.flamecore.protection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.management.SessionManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public final class TelegramNotifier {

    private static final String API_URL = "https://api.telegram.org/bot";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private final Main plugin;
    private final String token;
    private final String ownerChatId;

    @Getter
    private boolean available;

    public TelegramNotifier(Main plugin) {
        this.plugin = plugin;
        this.token = Main.getCfg().getString(ConfigKeys.BOT_TOKEN, "");
        this.ownerChatId = Main.getCfg().getString(ConfigKeys.OWNER_TELEGRAM_ID, "");
        this.available = !token.isEmpty() && !ownerChatId.isEmpty();
    }

    public void sendAdminLogin(String playerName, String ip, long timestamp) {
        sendMessage("Запрос входа администратора\n\nНик: " + playerName + "\nIP: " + ip
                        + "\nВремя: " + TIME_FORMAT.format(new Date(timestamp)),
                buttons(button("Впустить", "allow " + playerName),
                        button("Кикнуть", "kick " + playerName)));
    }

    public void sendOwnerLogin(String playerName, String ip, long timestamp, boolean panelAsked) {
        JsonArray keyboard = new JsonArray();
        JsonArray row = new JsonArray();
        row.add(button("Впустить", "allow " + playerName));
        row.add(button("Кикнуть", "kick " + playerName));
        keyboard.add(row);
        if (panelAsked) {
            JsonArray panelRow = new JsonArray();
            panelRow.add(button("Выдать доступ к панели", "panel " + playerName));
            keyboard.add(panelRow);
        }
        JsonObject reply = new JsonObject();
        reply.add("inline_keyboard", keyboard);

        sendMessage("Запрос входа ВЛАДЕЛЬЦА\n\nНик: " + playerName + "\nIP: " + ip
                        + "\nВремя: " + TIME_FORMAT.format(new Date(timestamp)), reply);
    }

    public void grantPanel(String playerName) {
        SessionManager.grant(playerName);
    }

    private JsonObject buttons(JsonObject... btns) {
        JsonArray keyboard = new JsonArray();
        JsonArray row = new JsonArray();
        for (JsonObject btn : btns) {
            row.add(btn);
        }
        keyboard.add(row);
        JsonObject reply = new JsonObject();
        reply.add("inline_keyboard", keyboard);
        return reply;
    }

    private JsonObject button(String text, String callbackData) {
        JsonObject btn = new JsonObject();
        btn.addProperty("text", text);
        btn.addProperty("callback_data", callbackData);
        return btn;
    }

    public void sendMessage(String text, JsonObject markup) {
        JsonObject payload = new JsonObject();
        payload.addProperty("chat_id", ownerChatId);
        payload.addProperty("text", text);
        if (markup != null) {
            payload.add("reply_markup", markup);
        }
        post("sendMessage", payload.toString());
    }

    public void startPolling(BiConsumer<String, String> handler) {
        if (token.isEmpty() || ownerChatId.isEmpty()) {
            return;
        }
        new Thread(() -> {
            int offset = 0;
            int failures = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    offset = poll(offset, handler);
                    failures = 0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    failures++;
                    long backoff = Math.min(15000L, 3000L * failures);
                    plugin.getLogger().log(Level.WARNING,
                            "Ошибка опроса Telegram (" + failures + " подряд), пауза " + backoff + " мс", e);
                    sleepQuietly(backoff);
                    continue;
                }
                sleepQuietly(700);
            }
        }, "flamecore-telegram-polling").start();
    }

    private int poll(int offset, BiConsumer<String, String> handler) throws InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("offset", offset);
        payload.addProperty("timeout", 20);
        payload.add("allowed_updates", arrayOf("callback_query"));

        JsonObject response = post("getUpdates", payload.toString());
        if (!response.has("ok") || !response.get("ok").getAsBoolean()) {
            String description = response.has("description")
                    ? response.get("description").getAsString() : "неизвестная ошибка";
            if (description.toLowerCase().contains("conflict")) {
                throw new InterruptedException();
            }
            throw new IllegalStateException(description);
        }
        JsonElement result = response.get("result");
        if (result == null || result.isJsonNull() || !result.isJsonArray()) {
            return offset;
        }
        int newOffset = offset;
        for (JsonElement element : result.getAsJsonArray()) {
            JsonObject update = element.getAsJsonObject();
            newOffset = update.get("update_id").getAsInt() + 1;
            if (update.has("callback_query")) {
                handleCallback(update.getAsJsonObject("callback_query"), handler);
            }
        }
        return newOffset;
    }

    private void handleCallback(JsonObject callback, BiConsumer<String, String> handler) {
        String data = callback.has("data") ? callback.get("data").getAsString() : "";
        String userName = callback.has("from") && callback.getAsJsonObject("from").has("username")
                ? callback.getAsJsonObject("from").get("username").getAsString() : "";
        String callbackId = callback.has("id") ? callback.get("id").getAsString() : "";
        answerCallback(callbackId);
        removeInlineKeyboard(callback);
        handler.accept(data, userName);
    }

    private void answerCallback(String callbackId) {
        if (callbackId == null || callbackId.isEmpty()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("callback_query_id", callbackId);
        post("answerCallbackQuery", payload.toString());
    }

    private void removeInlineKeyboard(JsonObject callback) {
        if (!callback.has("message")) {
            return;
        }
        JsonObject message = callback.getAsJsonObject("message");
        if (!message.has("chat") || !message.has("message_id")) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("chat_id", message.getAsJsonObject("chat").get("id").getAsString());
        payload.addProperty("message_id", message.get("message_id").getAsLong());
        payload.add("reply_markup", new JsonObject());
        post("editMessageReplyMarkup", payload.toString());
    }

    private JsonArray arrayOf(String value) {
        JsonArray arr = new JsonArray();
        arr.add(value);
        return arr;
    }

    private JsonObject post(String method, String jsonBody) {
        if (token.isEmpty()) {
            return new JsonObject();
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_URL + token + "/" + method);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8)) {
                String response = scanner.hasNext() ? scanner.useDelimiter("\\A").next() : "{}";
                return JsonParser.parseString(response).getAsJsonObject();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось отправить запрос в Telegram", e);
            return new JsonObject();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}