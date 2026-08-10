package me.jonycape.dev.flamecore.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigKeys {

    public static final String DATABASE_TYPE = "database.type";
    public static final String DATABASE_FILE = "database.file";

    public static final String BOT_TOKEN = "bot.token";

    public static final String OWNER_NAME = "owner.name";
    public static final String OWNER_TELEGRAM_ID = "owner.telegram_id";

    public static final String ADMINS = "admins";

    public static final String DANGEROUS_COMMANDS = "protection.dangerous-commands";
    public static final String DANGEROUS_CONFIRM_TIMEOUT = "protection.confirm-timeout";
    public static final String LOGIN_TIMEOUT = "protection.login-timeout";

    public static final String TG_ADMIN_LOGIN = "telegram-messages.admin-login";
    public static final String TG_OWNER_LOGIN = "telegram-messages.owner-login";
    public static final String TG_DANGER_SENDER = "telegram-messages.danger-sender";
    public static final String TG_DANGER_OWNER = "telegram-messages.danger-owner";
    public static final String TG_DANGER_EXECUTED = "telegram-messages.danger-executed";

    public static final String MESSAGE_PREFIX = "messages.prefix";
    public static final String MESSAGE_NO_PERMISSION = "messages.no-permission";
    public static final String MESSAGE_UNKNOWN_COMMAND = "messages.unknown-command";
    public static final String MESSAGE_PANEL_LOCKED = "messages.panel-locked";

    public static final String MESSAGE_GIVEAWAY_CREATED = "messages.giveaway-created";
    public static final String MESSAGE_GIVEAWAY_CREATE_USAGE = "messages.giveaway-create-usage";
    public static final String MESSAGE_GIVEAWAY_JOIN_USAGE = "messages.giveaway-join-usage";
    public static final String MESSAGE_GIVEAWAY_INVALID_TIME = "messages.giveaway-invalid-time";
    public static final String MESSAGE_GIVEAWAY_NOT_FOUND = "messages.giveaway-not-found";
    public static final String MESSAGE_GIVEAWAY_ALREADY_JOINED = "messages.giveaway-already-joined";
    public static final String MESSAGE_GIVEAWAY_JOIN_SUCCESS = "messages.giveaway-join-success";
    public static final String MESSAGE_GIVEAWAY_CONSOLE_ONLY = "messages.giveaway-console-only";
    public static final String MESSAGE_GIVEAWAY_NO_PARTICIPANTS = "messages.giveaway-no-participants";
    public static final String MESSAGE_GIVEAWAY_WINNER = "messages.giveaway-winner";

    public static final String MESSAGE_ADMIN_APPROVED = "messages.admin-approved";
    public static final String MESSAGE_ADMIN_DENIED = "messages.admin-denied";
    public static final String MESSAGE_ADMIN_WAITING = "messages.admin-waiting";
    public static final String MESSAGE_ADMIN_WAITING_TITLE = "messages.admin-waiting-title";
    public static final String MESSAGE_ADMIN_WAITING_SUBTITLE = "messages.admin-waiting-subtitle";
    public static final String MESSAGE_OWNER_PANEL_GRANTED = "messages.owner-panel-granted";
    public static final String MESSAGE_OWNER_PANEL_DENIED = "messages.owner-panel-denied";

    public static final String MESSAGE_MANAGEMENT_INFO = "messages.management-info";
    public static final String MESSAGE_MANAGEMENT_USAGE = "messages.management-usage";
    public static final String MESSAGE_MANAGEMENT_TPS = "messages.management-tps";
    public static final String MESSAGE_MANAGEMENT_MSPT = "messages.management-mspt";
    public static final String MESSAGE_MANAGEMENT_SYSTEM = "messages.management-system";
    public static final String MESSAGE_MANAGEMENT_ONLINE = "messages.management-online";
    public static final String MESSAGE_MANAGEMENT_WORLDS = "messages.management-worlds";
    public static final String MESSAGE_MANAGEMENT_PLAYER = "messages.management-player";
    public static final String MESSAGE_MANAGEMENT_PLAYER_NOT_FOUND = "messages.management-player-not-found";
    public static final String MESSAGE_MANAGEMENT_CHECK = "messages.management-check";
    public static final String MESSAGE_MANAGEMENT_RELOAD = "messages.management-reload";

    public static final String PROMO_CODES = "promo-codes";

    public static final String MESSAGE_PROMO_ACTIVATED = "messages.promo-activated";
    public static final String MESSAGE_PROMO_ALREADY_USED = "messages.promo-already-used";
    public static final String MESSAGE_PROMO_NOT_FOUND = "messages.promo-not-found";
    public static final String MESSAGE_PROMO_TITLE = "messages.promo-title";
    public static final String MESSAGE_PROMO_SUBTITLE = "messages.promo-subtitle";

    public static final String CUSTOMIZE_GLOW_INTERVAL = "customize.glow-interval";
    public static final String CUSTOMIZE_NIMB_INTERVAL = "customize.nimb-interval";
    public static final String CUSTOMIZE_NIMB_RADIUS = "customize.nimb-radius";
    public static final String CUSTOMIZE_NIMB_PARTICLES = "customize.nimb-particles";
    public static final String CUSTOMIZE_PARROT_INTERVAL = "customize.parrot-interval";

    public static final String MESSAGE_CUSTOMIZE_MENU = "messages.customize-menu";
    public static final String MESSAGE_CUSTOMIZE_COLOR_SET = "messages.customize-color-set";
    public static final String MESSAGE_CUSTOMIZE_INVALID_COLOR = "messages.customize-invalid-color";
    public static final String MESSAGE_CUSTOMIZE_PARROT_ON = "messages.customize-parrot-on";
    public static final String MESSAGE_CUSTOMIZE_PARROT_OFF = "messages.customize-parrot-off";
    public static final String MESSAGE_CUSTOMIZE_NIMB_ON = "messages.customize-nimb-on";
    public static final String MESSAGE_CUSTOMIZE_NIMB_OFF = "messages.customize-nimb-off";

    public static final String STREAM_COOLDOWN = "stream.cooldown";
    public static final String MESSAGE_STREAM_BROADCAST = "messages.stream-broadcast";
    public static final String MESSAGE_STREAM_USAGE = "messages.stream-usage";
    public static final String MESSAGE_STREAM_COOLDOWN = "messages.stream-cooldown";
    public static final String MESSAGE_STREAM_CONSOLE_ONLY = "messages.stream-console-only";

    public static final String PERM_CUSTOMIZE = "flamecore.customize";
    public static final String PERM_STREAM = "flamecore.stream";
}