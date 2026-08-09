# FlameCore — документация разработки

> **Этот файл — память проекта.** Прочитав его завтра, ты сразу вспомнишь, что было сделано,
> как всё устроено и что осталось доделать. Разделы читай по порядку.

---

## 1. Что это за проект

`FlameCore` — плагин для **Paper 1.20.1** (Java 17), главный управляющий модуль сервера FlameDex.

Задачи, над которыми работали:

- Защита входа админов/владельца через **Telegram-бота** (Long Polling).
- **Заморозка** админа на входе, пока доступ не подтверждён в Telegram.
- **Защита опасных команд** (двойное подтверждение, бан при отказе владельца).
- Красивые многострочные сообщения с поддержкой **HEX-цветов** и **HTML** в TG.
- Команда `/flamecore` с **tab-дополнением** и панелью владельца.
- Конкурсы `/giveaway`.
- **Все сообщения настраиваемые** — в игре (`messages:`) и в Telegram (`telegram-messages:`).
- **Lombok** для конструкторов/данных.

---

## 2. Состав проекта (структура)

```
src/main/java/me/jonycape/dev/flamecore/
├── Main.java                         — точка входа, роутинг Telegram-callback (`dc:`), регистрация
├── commands/
│   ├── BaseCommand.java              — база для команд (sendMessage без префикса, @RequiredArgsConstructor)
│   ├── FlameCoreCommand.java         — /flamecore (+ tab completion) + панель владельца
│   ├── GiveawayCommandExecutor.java  — /giveaway create|join
│   ├── GiveawayCommand.java
│   └── GiveawayJoinCommand.java
├── config/
│   ├── ConfigKeys.java               — константы ключей config.yml (в т.ч. TG_*)
│   └── ConfigManager.java            — загрузка/кэш; getStringList/getMultiLine/getStringMap/getInt
├── database/
│   ├── DatabaseManager.java          — H2 (локальный файл-база)
│   ├── GiveawayDAO.java
│   └── ParticipantDAO.java
├── giveaway/
│   ├── Giveaway.java                 — @Getter/@Setter/@Builder
│   ├── GiveawayManager.java
│   └── GiveawayScheduler.java
├── management/
│   ├── ServerManagementService.java  — tps/mspt/system/online/worlds/check/player/announce
│   └── SessionManager.java           — доступ к панели (ConcurrentHashMap)
├── protection/
│   ├── AdminProtectionService.java   — логика защиты, pendingLogins (TTL map), карта ники→TG ID, заморозка
│   ├── AdminProtectionListener.java  — события join/quit + колбэки Telegram (allow/kick/panel)
│   ├── AdminRestrictionListener.java — блокировка действий у админа в ожидании
│   ├── DangerousCommandService.java  — перехват опасных команд (pending, cancel/reject, бан)
│   ├── DangerousCommandListener.java — PlayerCommandPreprocessEvent (LOWEST)
│   └── TelegramNotifier.java         — Telegram Long Polling, кнопки, HTML-экранирование
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

1. Имя добавляется в `pendingLogins` — **карта ник(lower)→expiresAt** (хранение с **TTL**).
   Время жизни = `protection.login-timeout` (сек, дефолт 90).
2. `freezePlayer(player)`: `BLINDNESS` (`Integer.MAX_VALUE` тиков, без частиц),
   `walkSpeed = 0.0F`, `setInvulnerable(true)`.
3. В Telegram уходит запрос входа.
4. Игроку показывается title+subtitle (`admin-waiting-title`/`admin-waiting-subtitle`).

**TTL/просрочка:** главный `Main` каждую секунду зовёт `pruneExpired()` — убирает записи,
чей `expiresAt` истёк, и **размораживает** игрока (иначе админ завис бы навсегда при «тихом» отказе).

**`AdminRestrictionListener`** отменяет всё, пока игрок в `pendingLogins`:
`PlayerMoveEvent` (телепорт назад), `PlayerInteractEvent`, `PlayerInteractEntityEvent`,
`InventoryOpen/Click/DragEvent`, `BlockBreak/BlockPlaceEvent`, `PlayerItemConsumeEvent`,
`PlayerSwapHandItemsEvent`.

**Разморозка:** `approveAdmin` («Впустить») снимает слепоту, `walkSpeed = 0.2F`,
`setInvulnerable(false)`. `kickAdmin` кикает. `grantPanelAccess` размораживает и даёт доступ
к панели (НЕ требует pending-статуса — работает и после «Впустить»). `onPlayerQuit` чистит состояние.

> ⚠️ `pendingLogins` хранится в `AdminProtectionService` — теперь это `ConcurrentHashMap` (TTL).
> `SessionManager` отвечает только за **доступ к панели**.

### 3.1a Все слушатели тянут сервисы из Main (fix reload)

`AdminProtectionListener`, `AdminRestrictionListener`, `DangerousCommandListener` больше **не держат
ссылку** на `AdminProtectionService`/`DangerousCommandService` в конструкторе, а берут их через
`Main.getInstance().get…()` на каждый вызов. Это чинит старую проблему: после `/flamecore reload`
создавался новый сервис, а слушатели продолжали работать со старым (заморозка ломалась).

`Main.reloadPlugin()`:
- снимает снапшот `pendingSnapshot()` до пересоздания сервиса;
- создаёт новый `AdminProtectionService`, восстанавливает снапшот через `restorePending(snapshot)`
  (только неистёкшие записи);
- зовёт `dangerousCommandService.reload(новыйСервис)` — обновляет ссылку на админов
  и заново пересобирает паттерны dangerous-команд;
- пересоздаёт `GiveawayManager` (пул-схедyлер берёт его динамически из `Main`).

### 3.2 Кнопки Telegram владельца (3 кнопки)

**Где:** `TelegramNotifier`.

- Владельцу приходит сообщение с **3 кнопками**: «✅ Впустить» (`allow`),
  «⛔ Кикнуть» (`kick`), «🛠 Выдать доступ к панели» (`panel`).
- Админу (в личку) — только «Впустить» / «Кикнуть».
- `afterCallbackKeyboard` (в `handleCallback`):
  - владелец нажал «Впустить» на себя → `editKeyboard` оставляет только кнопку «Выдать панель»
    (Впустить/Кикнуть исчезают);
  - любое другое нажатие или любая `dc:*` → `removeKeyboard` (кнопки убираются).
- После любого нажатия вызывается `answerCallback` (снимает «отклонённые» кнопки).

**ВАЖНО — баг с потоками (главный фикс):** Telegram-callback приходит с отдельного потока
(не главный Bukkit-поток). Любой вызов Bukkit-API из него молча не срабатывает — именно поэтому
**слепота/движение не сбрасывались** и игрок не мог ходить. Всё обращение к Bukkit в колбэках
обёрнуто в `Bukkit.getScheduler().runTask(plugin, () -> …)`:
- `AdminProtectionListener.handleCallback` (allow/kick/panel);
- `DangerousCommandService.handleCallback` (dc:cancel / dc:reject).

### 3.3 Защита опасных команд

**Где:** `DangerousCommandService`, `DangerousCommandListener`.

- `DangerousCommandListener` перехватывает команды на `PlayerCommandPreprocessEvent`
  (priority `LOWEST`), зовёт `service.intercept(sender, message)`.
- `intercept` матчит команду (без ведущего `/`) по маскам-`Pattern` из
  `protection.dangerous-commands` (`CASE_INSENSITIVE`). Если совпало — команда **НЕ выполняется**,
  создаётся `PendingCommand` (`@Value`) с `UUID`-id и таймаутом (`confirm-timeout`, сек).
- Админу (если задан его TG) уходит `sendDangerAskSender` с кнопкой «❌ Отменить»
  (`dc:cancel <id>`), владельцу — `sendDangerAskOwner` с кнопкой «⛔ Отклонить и забанить»
  (`dc:reject <id>`).
- Колбэки `dc:*` маршрутизируются в `Main` в `DangerousCommandService.handleCallback`
  (на главном потоке через `runTask`):
  - `cancel` — команда не выполняется, без бана;
  - `reject` — **вечный бан** (`Bukkit.getBanList(NAME).addBan`), кик с сообщением
    «Защита сработала, за обжалованием в админ-чат.», запись в лог.
- По таймауту `resolveTimeout` (если не отменено/не отклонено) — команда выполняется
  автоматически через `Bukkit.dispatchCommand`.
- **Команды бана НЕ включены** в dangerous-commands — модераторы багят игроков без подтверждения.
- `cancel`/`reject` идемпотентны: `pending.remove(id)` атомарен, повторное нажатие без влияния.
- `reload(AdminProtectionService)` пересобирает паттерны без пересоздания сервиса.

### 3.4 Сообщения настраиваемые + HTML-безопасность

**Где:** `TelegramNotifier`, `config.yml`, `BaseCommand`, `ServerManagementService`.

- Тексты TG берутся из `telegram-messages:` в config через `tgText(key, pairs)` +
  `Main.getCfg().getMultiLine(key)`. Плейсхолдеры: `%player%`, `%ip%`, `%time%`,
  `%command%`, `%timeout%`.
- TG отправляется с `ParseMode.HTML`; **весь** пользовательский контент экранируется
  через `esc()` (`&`→`&amp;`, `<`→`&lt;`, `>`→`&gt;`) — никаких «сырых» тегов из ника/команды.
- Игровые сообщения — из `messages:` списками (единый блочный стиль, `&#RRGGBB` HEX).
  `BaseCommand.sendMessage` и `GiveawayManager.buildMessage` больше **не** дописывают префикс сами.
- `ServerManagementService.raw` читает текст через `getMultiLine` (фикс: раньше `getString`
  возвращал список с квадратными скобками).

### 3.5 Админы — карта «ник → Telegram ID»

`admins:` в config — это **map** `ник → ID` (раньше был список). Читается через
`ConfigManager.getStringMap`, хранится в `AdminProtectionService`; `getTelegramId(name)`
нужен для лички (danger-подтверждение, уведомление о входе).

### 3.6 Lombok

`@RequiredArgsConstructor` — `BaseCommand`, `AdminProtectionListener`, `ServerManagementService`,
`GiveawayScheduler`, DAO, `DatabaseManager`; `@Getter/@Setter/@Builder` — `Giveaway`,
`@Value` — `PendingCommand`. Остальные конструкторы содержат `super()`/логику, их Lombok не сворачивает.

---

## 4. Что доделать ЗАВТРА (TODO)

- [ ] **Проверить в бою** 3 кнопки владельца и сжатие клавиатуры после «Впустить».
- [ ] Проверить в бою защиту опасных команд: авто-выполнение по таймауту и бан по «Отклонить».
- [ ] Проверить в бою TTL входа (`protection.login-timeout`) — авто-разморозка по таймауту.

> Сделано в этот заход: убраны все java-комментарии из классов; TTL pendingLogins +
> авто-прунинг; починка reload (слушатели тянут сервисы из Main, восстановление pending,
> пересборка dangerous-паттернов); идемпотентность кнопок; убран N+1 из loadAll
> (GiveawayDAO больше не тянет участников); дары/схедyлер берут менеджера динамически.
>
> Починены пустые **тайтл/боссбар**: `Component.text(coloredString)` выводил `§` дословно,
> а не цвет. Теперь title/subtitle/кик-причина строятся через
> `LegacyComponentSerializer.legacySection().deserialize(...)`, а на время ожидания вешается
> **BossBar** (жёлтый, с текстом `admin-waiting-title`), убирается при approve/kick/grant/quit/expire
> (`bars` map в `AdminProtectionService`).
>
> **Telegram**: в `telegram-messages` переведено на цитаты `<blockquote>` (команда),
> в опасные сообщения добавлен плейсхолдер `%time%` и время (sendDangerAskSender/Owner).
> `protection.login-timeout` — TTL входа в конфиге.

---

## 5. Сборка и проверка

Сборка через Maven (на машине не установлен — проверяю прямой javac на classpath):

```
mvn -DskipTests package
```

Быстрая javac-проверка (как делал я) — компилируется с EXIT=0:

```
javac -encoding UTF-8 -proc:full -processorpath lombok.jar \
      -cp paper-api.jar:adventure/*.jar:examination/*.jar:bungeecord-chat.jar:gson.jar:h2.jar:\
          java-telegram-bot-api.jar:okhttp.jar:okio.jar:kotlin.jar:lombok.jar \
      -d build_dir  src/main/java/**/*.java
```

POM: `io.papermc.paper-api 1.20.1`, Java 17, gson 2.10.1, lombok 1.18.30,
`com.github.pengrad:java-telegram-bot-api 6.9.0` (OkHttp 4.10.0, okio 3.2.0, kotlin-stdlib 1.8.10).
