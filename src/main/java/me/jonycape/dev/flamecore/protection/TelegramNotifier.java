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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Отправка и приём сообщений Telegram через библиотеку com.pengrad:java-telegram-bot-api
 * (лёгкий транспорт на OkHttp — без тяжёлых зависимостей в отличие от org.telegram).
 */
public final class TelegramNotifier {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private final Main plugin;
    private final TelegramBot bot;

    @Getter
    private volatile boolean available;

    private volatile BiConsumer<String, String> callbackHandler;

    public TelegramNotifier(Main plugin) {
        this.plugin = plugin;
        String token = Main.getCfg().getString(ConfigKeys.BOT_TOKEN, "");
        this.available = !token.isEmpty();
        this.bot = new TelegramBot(token);
    }

    public void startPolling(BiConsumer<String, String> handler) {
        if (!available) {
            return;
        }
        this.callbackHandler = handler;
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
                if (update.callbackQuery() != null) {
                    handleCallback(update.callbackQuery());
                }
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void handleCallback(CallbackQuery query) {
        String data = query.data() == null ? "" : query.data();
        try {
            answerCallback(query.id());
            afterCallbackKeyboard(query, data);
            if (callbackHandler != null) {
                String from = query.from() != null && query.from().username() != null
                        ? query.from().username() : "";
                callbackHandler.accept(data, from);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка обработки callback Telegram: " + data, e);
        }
    }

    /** Поведение клавиатуры после нажатия кнопки (логика владельца). */
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
            // Владелец нажал «Впустить»: убираем «Впустить» и «Кикнуть», но оставляем «Выдать панель».
            editKeyboard(query, new InlineKeyboardMarkup(panelButton(playerName)));
        } else {
            removeKeyboard(query);
        }
    }

    /** Сообщение для самого администратора (отправляется в личку админа). */
    public void sendAdminLogin(String telegramId, String playerName, String ip, long timestamp) {
        if (telegramId == null || telegramId.isEmpty()) {
            return;
        }
        sendMessageTo(telegramId,
                tgText(ConfigKeys.TG_ADMIN_LOGIN, "player", esc(playerName), "ip", esc(ip),
                        "time", TIME_FORMAT.format(new Date(timestamp))),
                new InlineKeyboardMarkup(allowButton(playerName), kickButton(playerName)));
    }

    /** Сообщение владельцу: три кнопки (Впустить / Кикнуть / Выдать панель). */
    public void sendOwnerLogin(String playerName, String ip, long timestamp, boolean panelAsked) {
        sendMessage(tgText(ConfigKeys.TG_OWNER_LOGIN, "player", esc(playerName), "ip", esc(ip),
                        "time", TIME_FORMAT.format(new Date(timestamp))),
                new InlineKeyboardMarkup(allowButton(playerName), kickButton(playerName), panelButton(playerName)));
    }

    /** Сообщение админу: опасная команда требует подтверждения (его собственные кнопки). */
    public void sendDangerAskSender(String telegramId, String playerName, String command,
                                    String actionId, int timeoutSeconds) {
        if (telegramId == null || telegramId.isEmpty()) {
            return;
        }
        sendMessageTo(telegramId,
                tgText(ConfigKeys.TG_DANGER_SENDER, "command", esc(command), "timeout", String.valueOf(timeoutSeconds)),
                new InlineKeyboardMarkup(
                        new InlineKeyboardButton("❌ Отменить").callbackData("dc:cancel " + actionId)));
    }

    /** Сообщение владельцу: чужой админ выполнил опасную команду, кнопка «Отклонить» → бан. */
    public void sendDangerAskOwner(String playerName, String command, String actionId) {
        sendMessage(tgText(ConfigKeys.TG_DANGER_OWNER, "player", esc(playerName), "command", esc(command)),
                new InlineKeyboardMarkup(
                        new InlineKeyboardButton("⛔ Отклонить и забанить").callbackData("dc:reject " + actionId)));
    }

    /** Уведомление владельцу о том, что опасная команда выполнилась автоматически (таймаут). */
    public void sendDangerExecuted(String playerName, String command) {
        sendMessage(tgText(ConfigKeys.TG_DANGER_EXECUTED, "player", esc(playerName), "command", esc(command)), null);
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

    /** Текст сообщения из конфига с подстановкой плейсхолдеров. */
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
        try {
            SendMessage request = new SendMessage(chatId, text);
            request.parseMode(ParseMode.HTML);
            if (markup != null) {
                request.replyMarkup(markup);
            }
            bot.execute(request);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось отправить сообщение в Telegram", e);
        }
    }

    /** Экранирует HTML-спецсимволы (для подстановки в теги <b>/<code>). */
    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void answerCallback(String callbackId) {
        try {
            bot.execute(new AnswerCallbackQuery(callbackId));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось ответить на callback", e);
        }
    }

    private void removeKeyboard(CallbackQuery query) {
        try {
            if (query.message() == null || query.message().chat() == null) {
                return;
            }
            bot.execute(new EditMessageReplyMarkup(query.message().chat().id(),
                    query.message().messageId()).replyMarkup(new InlineKeyboardMarkup()));
        } catch (Exception ignored) {
        }
    }

    private void editKeyboard(CallbackQuery query, InlineKeyboardMarkup markup) {
        try {
            if (query.message() == null || query.message().chat() == null) {
                return;
            }
            bot.execute(new EditMessageReplyMarkup(query.message().chat().id(),
                    query.message().messageId()).replyMarkup(markup));
        } catch (Exception ignored) {
        }
    }
}