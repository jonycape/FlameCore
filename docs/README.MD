# FlameCore — документация разработки

> **Этот файл — память проекта.** Прочитав его завтра, ты сразу вспомнишь, что было сделано,
> как всё устроено и что осталось доделать. Разделы читай по порядку.

---

## 1. Что это за проект

`FlameCore` — плагин для **Paper 1.20.1** (Java 17), главный управляющий модуль сервера FlameDex.

Задачи, над которыми работали сегодня:

- Защита входа админов/владельца через **Telegram-бота** (кнопки «Впустить» / «Кикнуть»).
- **Заморозка** админа на входе, пока доступ не подтверждён в Telegram.
- Красивые многострочные сообщения с поддержкой **HEX-цветов**.
- Команда `/flamecore` с **tab-дополнением** (автодополнение).
- Конкурсы `/giveaway`.

---

## 2. Состав проекта (структура)

```
src/main/java/me/jonycape/dev/flamecore/
├── Main.java                         — точка входа, инициализация
├── commands/
│   ├── BaseCommand.java              — база для команд (sendMessage, prefix)
│   ├── FlameCoreCommand.java         — /flamecore (+ tab completion)
│   ├── GiveawayCommandExecutor.java  — /giveaway create|join
│   ├── GiveawayCommand.java
│   └── GiveawayJoinCommand.java
├── config/
│   ├── ConfigKeys.java               — константы ключей config.yml
│   └── ConfigManager.java            — загрузка/кэш config.yml
├── database/
│   ├── DatabaseManager.java          — H2 (локальный файл-база)
│   ├── GiveawayDAO.java
│   └── ParticipantDAO.java
├── giveaway/
│   ├── Giveaway.java
│   ├── GiveawayManager.java
│   └── GiveawayScheduler.java
├── management/
│   ├── ServerManagementService.java  — tps/mspt/system/online/worlds/check
│   └── SessionManager.java           — доступ к панели (ConcurrentHashMap)
├── protection/
│   ├── AdminProtectionService.java   — логика защиты + заморозка
│   ├── AdminProtectionListener.java  — события join/quit + колбэки Telegram
│   ├── AdminRestrictionListener.java — блокировка действий у админа в ожидании
│   └── TelegramNotifier.java         — Telegram Long Polling, кнопки
└── utils/
    ├── MessageUtils.java             — HEX-цвета + translateAlternateColorCodes
    ├── TimeUtils.java
    └── IdGenerator.java
```

---

## 3. Что СДЕЛАНО (прочти перед рефакторингом)

### 3.1 Заморозка админа до подтверждения входа

**Где:** `AdminProtectionService`, `AdminRestrictionListener`, `Main`.

При входе админа/владельца (`onPlayerJoin`):

1. Имя добавляется в `pendingLogins` (приватный `Set<String>` внутри сервиса).
2. Вызывается `freezePlayer(player)`:
   - накладывается `BLINDNESS` (долго `Integer.MAX_VALUE` тиков, без частиц);
   - `walkSpeed = 0.0F`;
   - `setInvulnerable(true)` (через `notifyWaiting`).
3. В Telegram уходит запрос входа.
4. Игроку показывается `admin-waiting` («Ожидание подтверждения»).

**`AdminRestrictionListener`** (зарегистрирован в `Main`) отменяет всё, пока игрок в `pendingLogins`:

- `PlayerMoveEvent` → телепорт назад (`setTo(from)`) при смене блока;
- `PlayerInteractEvent` (кроме OFF_HAND), `PlayerInteractEntityEvent`;
- `InventoryOpenEvent`, `InventoryClickEvent`, `InventoryDragEvent`;
- `BlockBreakEvent`, `BlockPlaceEvent`, `PlayerItemConsumeEvent`, `PlayerSwapHandItemsEvent`.

**Разморозка:** `approveAdmin` (кнопка «Впустить») снимает слепоту, возвращает
`walkSpeed = 0.2F`, `setInvulnerable(false)` и шлёт сообщение. `kickAdmin` кикает.
`onPlayerQuit` тоже чистит состояние.

> ⚠️ `pendingLogins` хранится в `AdminProtectionService`, а НЕ в `SessionManager`.
> `SessionManager` по-прежнему отвечает только за **доступ к панели** (`PANEL_OWNERS`).

### 3.2 Починка кнопок Telegram

**Где:** `TelegramNotifier`.

Были проблемы: «ивент отклонён» и постоянный спам «ошибка пула» в консоли.

Исправлено:

- Добавлен `answerCallbackQuery` — подтверждает нажатие кнопки, убирает «отклонённые» кнопки.
- После нажатия клавиатура убирается через `editMessageReplyMarkup` с пустым `inline_keyboard`.
- `poll()` стал устойчивым:
  - проверяет поле `ok` в ответе API;
  - при `conflict` (409, второй поллинг) — прерывает цикл;
  - экспоненциальный бэкофф `3000ms * failures` (до 15000ms);
  - работает один поток поллинга (`flamecore-telegram-polling`).

Callback-данные в кнопках: `allow <ник>`, `kick <ник>`, `panel <ник>`
(см. `sendAdminLogin` / `sendOwnerLogin`).

### 3.3 Красивые сообщения + HEX

**Где:** `utils/MessageUtils.java`, `BaseCommand`, `AdminProtectionService`.

- `color()` поддерживает и обычные коды (`&a`, `&l`, …), и **HEX** `&#RRGGBB`
  через `net.md_5.bungee.api.ChatColor.of("#RRGGBB")` + `translateAlternateColorCodes('&')`.
- **Починен баг с префиксом**: в `config.yml` `messages.prefix` — это **список**
  (`- "…"`). Старый код читал его через `getString()`, что возвращало `[&7[&6FlameCore&7] &f]`
  (с квадратными скобками). Теперь префикс берётся через `getMultiLine()` → многострочные
  сообщения с новой строки заработали.

Метод `prefix()` добавлен в `BaseCommand` и продублирован в `AdminProtectionService`.

### 3.4 Tab-дополнение для /flamecore

`FlameCoreCommand` теперь реализует `TabCompleter` и регистрируется в `Main`
(`setTabCompleter`). Подкоманды: `tps mspt system online worlds announce player check reload`.
Для `player <ник>` — подстановка онлайн-игроков.

---

## 4. Что доделать ЗАВТРА (TODO)

- [ ] **Проверить в бою** заморозку на входе (слепоту, блокировку движений/инвентаря) — на сервере.
- [ ] Убедиться, что двойное/быстрое нажатие кнопок Telegram не создаёт повторное окно.
- [ ] Возможно, добавить TTL (время жизни) для `pendingLogins` — сейчас запись висит, пока
      игрок не вышел или не нажали кнопку.
- [ ] `reloadPlugin()` в `Main` пересоздаёт `AdminProtectionService` и `GiveawayManager`,
      но **не восстанавливает** `pendingLogins` для уже онлайн-админов — продумать восстановление.
- [ ] Проверить `GiveawayScheduler` (завершение по таймеру) и `ParticipantDAO`.
- [ ] Докрутить `management-*` сообщения (`tps`, `check` и т.д.) под новый HEX-стиль.

---

## 5. Сборка и проверка

Сборка через Maven (на машине не установлен — проверял прямой javac на classpath):

```
mvn -DskipTests package
```

Быстрая javac-проверка (как делал я):

```
javac -encoding UTF-8 -cp paper-api.jar:gson.jar:adventure/*.jar:lombok.jar \
      -d build_dir  src/main/java/**/*.java
```

POM: `io.papermc.paper-api 1.20.1`, Java 17, gson 2.10.1, lombok 1.18.30.