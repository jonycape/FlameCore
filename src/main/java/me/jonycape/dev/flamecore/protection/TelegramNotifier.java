package me.jonycape.dev.flamecore.protection;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.Getter;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public final class TelegramNotifier {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Main plugin;
    private final TelegramBot bot;

    @Getter
    private volatile boolean available;

    private volatile BiConsumer<String, String> callbackHandler;
    private volatile BiConsumer<String, String> messageHandler;

    public TelegramNotifier(Main plugin) {
        this.plugin = plugin;
        String token = Main.getCfg().getString(ConfigKeys.BOT_TOKEN, "");
        this.available = !token.isEmpty() && !token.contains("ВАШ");
        this.bot = new TelegramBot.Builder(token).okHttpClient(buildHttpClient()).build();
    }

    private static OkHttpClient buildHttpClient() {
        if (!Main.getCfg().getBoolean(ConfigKeys.BOT_PROXY_ENABLED, false)) {
            return new OkHttpClient();
        }
        String type = Main.getCfg().getString(ConfigKeys.BOT_PROXY_TYPE, "socks").toLowerCase();
        String host = Main.getCfg().getString(ConfigKeys.BOT_PROXY_HOST, "127.0.0.1");
        int port = Main.getCfg().getInt(ConfigKeys.BOT_PROXY_PORT, 1080);
        String user = Main.getCfg().getString(ConfigKeys.BOT_PROXY_USER, "");
        String password = Main.getCfg().getString(ConfigKeys.BOT_PROXY_PASSWORD, "");

        Proxy.Type proxyType = "http".equals(type) ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
        Proxy proxy = new Proxy(proxyType, new InetSocketAddress(host, port));

        OkHttpClient.Builder builder = new OkHttpClient.Builder().proxy(proxy);

        if (!user.isEmpty()) {
            builder.proxyAuthenticator(new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", Credentials.basic(user, password))
                            .build();
                }
            });
        }
        return builder.build();
    }

    public void startPolling(BiConsumer<String, String> callback, BiConsumer<String, String> message) {
        if (!available) {
            return;
        }
        this.callbackHandler = callback;
        this.messageHandler = message;
        bot.setUpdatesListener(this::processUpdates, e ->
                plugin.getLogger().log(Level.WARNING, "Ошибка при приёме обновлений Telegram", e));
    }

    public void stopPolling() {
        try {
            bot.removeGetUpdatesListener();
        } catch (Exception ignored) {
        }
    }

    public int processUpdates(List<Update> updates) {
        if (updates != null) {
            for (Update update : updates) {
                plugin.getLogger().info("[DEBUG] update: cb=" + (update.callbackQuery() != null)
                        + " msg=" + (update.message() != null)
                        + " text=" + (update.message() != null ? "'" + update.message().text() + "'" : "null"));
                if (update.callbackQuery() != null) {
                    handleCallback(update.callbackQuery());
                } else if (update.message() != null && update.message().text() != null) {
                    handleMessage(update.message());
                }
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void handleMessage(com.pengrad.telegrambot.model.Message message) {
        String text = message.text();
        plugin.getLogger().info("[DEBUG] handleMessage enter: text='" + text + "' startsWith/=" + (text != null && text.startsWith("/")) + " handler=" + (messageHandler != null));
        if (text == null || !text.startsWith("/") || messageHandler == null) {
            return;
        }
        Long userId = message.from() != null ? message.from().id() : null;
        messageHandler.accept(text, userId == null ? "" : String.valueOf(userId));
    }

    private void handleCallback(CallbackQuery query) {
        String data = query.data() == null ? "" : query.data();
        try {
            answerCallback(query.id());
            afterCallbackKeyboard(query, data);
            if (callbackHandler != null) {
                String chatId = query.message() != null && query.message().chat() != null
                        ? String.valueOf(query.message().chat().id()) : "";
                callbackHandler.accept(data, chatId);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка обработки callback Telegram: " + data, e);
        }
    }

    private void afterCallbackKeyboard(CallbackQuery query, String data) {
        if (data.startsWith("dc:")) {
            removeKeyboard(query);
            return;
        }
        String[] parts = data.split("\\s+", 2);
        if (parts.length < 2) {
            return;
        }
        String action = parts[0];
        String playerName = parts[1];
        String ownerName = Main.getCfg().getString(ConfigKeys.OWNER_NAME, "");
        boolean ownerPress = !ownerName.isEmpty() && ownerName.equalsIgnoreCase(playerName);
        if ("allow".equals(action) && ownerPress) {
            editKeyboard(query, new InlineKeyboardMarkup(panelButton(playerName)));
        } else {
            removeKeyboard(query);
        }
    }

    public void sendAdminLogin(String telegramId, String playerName, String ip, long timestamp) {
        if (telegramId == null || telegramId.isEmpty()) {
            return;
        }
        sendMessageTo(telegramId,
                tgText(ConfigKeys.TG_ADMIN_LOGIN, "player", esc(playerName), "ip", esc(ip),
                        "time", TIME_FORMAT.format(Instant.ofEpochMilli(timestamp))),
                new InlineKeyboardMarkup(allowButton(playerName), kickButton(playerName)));
    }

    public void sendOwnerLogin(String playerName, String ip, long timestamp, boolean panelAsked) {
        sendMessage(tgText(ConfigKeys.TG_OWNER_LOGIN, "player", esc(playerName), "ip", esc(ip),
                        "time", TIME_FORMAT.format(Instant.ofEpochMilli(timestamp))),
                new InlineKeyboardMarkup(allowButton(playerName), kickButton(playerName), panelButton(playerName)));
    }

    public void sendDangerAskSender(String telegramId, String playerName, String command,
                                    String actionId, int timeoutSeconds, long timestamp) {
        if (telegramId == null || telegramId.isEmpty()) {
            return;
        }
        sendMessageTo(telegramId,
                tgText(ConfigKeys.TG_DANGER_SENDER, "command", esc(command),
                        "timeout", String.valueOf(timeoutSeconds), "time", TIME_FORMAT.format(Instant.ofEpochMilli(timestamp))),
                new InlineKeyboardMarkup(
                        new InlineKeyboardButton("❌ Отменить").callbackData("dc:cancel " + actionId)));
    }

    public void sendDangerAskOwner(String playerName, String command, String actionId, long timestamp) {
        sendMessage(tgText(ConfigKeys.TG_DANGER_OWNER, "player", esc(playerName), "command", esc(command),
                        "time", TIME_FORMAT.format(Instant.ofEpochMilli(timestamp))),
                new InlineKeyboardMarkup(
                        new InlineKeyboardButton("⛔ Отклонить и забанить").callbackData("dc:reject " + actionId)));
    }

    public void sendDangerExecuted(String playerName, String command) {
        sendMessage(tgText(ConfigKeys.TG_DANGER_EXECUTED, "player", esc(playerName), "command", esc(command)), null);
    }

    public void sendKickNonAdmin(String playerName, String ip) {
        if (!available) {
            return;
        }
        sendMessage(tgText(ConfigKeys.TG_KICK_NON_ADMIN, "player", esc(playerName), "ip", esc(ip),
                "time", TIME_FORMAT.format(Instant.now())), null);
    }

    public void sendToOwner(String text, InlineKeyboardMarkup markup) {
        sendMessage(text, markup);
    }

    public void sendToChat(String chatId, String text, InlineKeyboardMarkup markup) {
        sendMessageTo(chatId, text, markup);
    }

    private static InlineKeyboardButton allowButton(String name) {
        return new InlineKeyboardButton("✅ Впустить").callbackData("allow " + name);
    }

    private static InlineKeyboardButton kickButton(String name) {
        return new InlineKeyboardButton("⛔ Кикнуть").callbackData("kick " + name);
    }

    private static InlineKeyboardButton panelButton(String name) {
        return new InlineKeyboardButton("🛠 Выдать доступ к панели").callbackData("panel " + name);
    }

    private static String tgText(String key, String... pairs) {
        String body = Main.getCfg().getMultiLine(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            body = body.replace("%" + pairs[i] + "%", pairs[i + 1]);
        }
        return body;
    }

    private void sendMessage(String text, InlineKeyboardMarkup markup) {
        String chatId = Main.getCfg().getString(ConfigKeys.OWNER_TELEGRAM_ID, "");
        if (chatId.isEmpty()) {
            return;
        }
        sendMessageTo(chatId, text, markup);
    }

    void sendMessageTo(String chatId, String text, InlineKeyboardMarkup markup) {
        if (!available || chatId == null || chatId.isEmpty()) {
            return;
        }
        SendMessage request = new SendMessage(chatId, text);
        request.parseMode(ParseMode.HTML);
        if (markup != null) {
            request.replyMarkup(markup);
        }
        executeAsync(() -> {
            try {
                bot.execute(request);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось отправить сообщение в Telegram", e);
            }
        });
    }

    public static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void answerCallback(String callbackId) {
        executeAsync(() -> {
            try {
                bot.execute(new AnswerCallbackQuery(callbackId));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось ответить на callback", e);
            }
        });
    }

    private void executeAsync(Runnable action) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                action.run();
            } catch (Exception ignored) {
            }
        });
    }

    private void removeKeyboard(CallbackQuery query) {
        if (query.message() == null || query.message().chat() == null) {
            return;
        }
        EditMessageReplyMarkup request = new EditMessageReplyMarkup(query.message().chat().id(),
                query.message().messageId()).replyMarkup(new InlineKeyboardMarkup());
        executeAsync(() -> {
            try {
                bot.execute(request);
            } catch (Exception ignored) {
            }
        });
    }

    private void editKeyboard(CallbackQuery query, InlineKeyboardMarkup markup) {
        if (query.message() == null || query.message().chat() == null) {
            return;
        }
        EditMessageReplyMarkup request = new EditMessageReplyMarkup(query.message().chat().id(),
                query.message().messageId()).replyMarkup(markup);
        executeAsync(() -> {
            try {
                bot.execute(request);
            } catch (Exception ignored) {
            }
        });
    }
}