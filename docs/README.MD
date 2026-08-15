# FlameCore — документация разработки

> **Этот файл — память проекта.** Прочитав его завтра, ты сразу вспомнишь, что было сделано,
> как всё устроено и что осталось доделать. Разделы читай по порядку.

> ## 🚨 ЗАВТРА САМОЕ ГЛАВНОЕ — ПОЧИНИТЬ `/info <ник>` В БОТЕ
>
> **Симптом (проверено 15.08):** кнопки входа/панели работают (callback-апдейты приходят
> и обрабатываются), но `/info jonycape` НЕ отвечает вообще. Отправка в бот идёт, кнопки живые,
> в конфиге `owner.telegram_id` реальный.
>
> **Диагностика:** в `TelegramNotifier.processUpdates` добавлены `[DEBUG]`-логи. При отправке
> `/info` в консоли НЕ появляется даже `[DEBUG] update: ... msg=true text='/info jonycape'` —
> значит **message-апдейты не доходят** до `processUpdates`/`handleMessage`, хотя callback-апдейты
> доходят. (Прокси Happ HTTP `127.0.0.1:10809` — отправка работает, приём сообщений — нет.)
>
> **Что делать завтра:**
> 1. Сначала проверить, действительно ли `getUpdates` возвращает message-апдейты (посмотреть
>    консольный лог после `/info`; убедиться, что сервер стоит на свежем `target/...shaded.jar`).
> 2. Разобраться, почему message-апдейты не обрабатываются (тип апдейта, `update.message()`/`text()`,
>    фильтр `startsWith("/")`, `messageHandler`). Возможная причина — отличия в доставке
>    `message` vs `callback_query` (например, getUpdates-батч режется прокси, или сообщение приходит
>    с иным типом/полем).
> 3. Починить, проверить на сервере, **убрать все `[DEBUG]`-логи** из `TelegramNotifier` и
>    `PlayerInfoService`.
>
> Подробности кода `/info` — раздел 3.17. Связанные файлы: `TelegramNotifier.java`,
> `PlayerInfoService.java`, `Main.java` (`initProtection`, роутинг `messageHandler`).

---

## 1. Что это за проект

`FlameCore` — плагин для **Paper 1.20.1** (Java 17), главный управляющий модуль сервера FlameDex.

Задачи, над которыми работали:

- Защита входа админов/владельца через **Telegram-бота** (Long Polling).
- **Кик не-админов с запрещёнными пермишенами** (op/фулл-права/groups) — кик на входе + алерт владельцу.
- **Заморозка** админа на входе, пока доступ не подтверждён в Telegram.
- **Защита опасных команд** (двойное подтверждение, бан при отказе владельца).
- Красивые многострочные сообщения с поддержкой **HEX-цветов** и **HTML** в TG.
- Команда `/flamecore` с **tab-дополнением** и панелью владельца.
- Конкурсы `/giveaway`.
- **Все сообщения настраиваемые** — в игре (`messages:`) и в Telegram (`telegram-messages:`).
- **Lombok** для конструкторов/данных.
- **Промокоды** `/название` (команды из конфига + фейерверк, лимит на игрока в H2).
- **Кастомизация админов** (свечение GLOW; попугай на плече и нимб частиц **удалены**) — `flamecore.customize`.
- **Анонс стримов** `/flamecore stream <url>` — `flamecore.stream`, кулдаун.
- **Топ по платежам** `/flamecore paymenttop` + `/flamecore addpayment` — NPC-житель +
  голограмма DecentHolograms с топ-5 платежей (H2), переживает рестарт.
- **getDouble** добавлен в `ConfigManager` (для радиусов нимба).
- **Конфиг разбит на файлы** — `config.yml` (база/бот/владелец), `admin-guard.yml`,
  `messages.yml`, `customize.yml`, `stream.yml`, `promo-codes.yml`, `donatetop.yml`
  (ConfigManager сливает их в один кэш, пути ключей не изменились).
- **Резолвер звуков** в `MessageProcessor` — старые имена звуков (`LEVEL_UP`, `NOTE_PLING`, …)
  маппятся на актуальные для Paper 1.20.1 (`ENTITY_PLAYER_LEVELUP`, `BLOCK_NOTE_BLOCK_PLING`, …).
- **Переключатели модулей** (`modules:` в config.yml) — каждая фича включается/выключается отдельно:
  protection (защита админов/владельца), giveaway, promocode, customize, stream, donatetop.
- **Оптимизация** — look-task NPC топа (каждые 2 тика, телепорт только при смене взгляда),
  кэш конфиг-значений тика кастомизации, thread-safe форматтер времени Telegram,
  immutable SOUND_ALIASES, прунинг кулдаунов стримов.

---

## 2. Состав проекта (структура)

```
src/main/java/me/jonycape/dev/flamecore/
├── Main.java                         — точка входа, роутинг Telegram-callback (`dc:`), регистрация,
│                                       инициализация/stop праздных сервисов, /flamecore reload
├── commands/
│   ├── BaseCommand.java              — база для команд (sendMessage без префикса, @RequiredArgsConstructor)
│   ├── FlameCoreCommand.java         — /flamecore (+ tab completion) + панель владельца;
│   │                                    customize/stream роутятся до проверки владельца (по правам)
│   ├── GiveawayCommandExecutor.java  — /giveaway create|join
│   ├── GiveawayCommand.java
│   └── GiveawayJoinCommand.java
├── config/
│   ├── ConfigKeys.java               — константы ключей конфигов (в т.ч. TG_*, PROMO, CUSTOMIZE_*, STREAM_*)
│   └── ConfigManager.java            — многофайловая загрузка/кэш; getStringList/getMultiLine/getStringMap/getInt/getDouble/getSection/getBoolean
├── database/
│   ├── DatabaseManager.java          — H2 (локальный файл-база)
│   ├── GiveawayDAO.java
│   ├── ParticipantDAO.java
│   ├── PromoCodeDAO.java             — таблица promo_uses(code,player,uses): лимит использования промокода НА ИГРОКА
│   └── DonateTopDAO.java             — таблица donate_top(player,payments): счётчик платежей (+1, топ по убыванию)
├── giveaway/
│   ├── Giveaway.java                 — @Getter/@Setter/@Builder
│   ├── GiveawayManager.java
│   └── GiveawayScheduler.java
├── donatetop/
│   ├── TopEntry.java                 — record (player, payments)
│   └── DonateTopService.java         — NPC-житель + голограмма DecentHolograms, refresh по таймеру, точки в конфиге
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
├── promocode/
│   ├── PromoCode.java                — данные кода (@Getter: code/uses/commands/colors/fade)
│   ├── PromoCodeService.java         — загрузка из config, consume() + фейерверк, Result{OK,NOT_FOUND,ALREADY_USED}
│   └── PromoCodeListener.java        — PlayerCommandPreprocessEvent (MONITOR): catch `/код` если есть в конфиге
├── customize/
│   ├── Customization.java            — состояние на игрока (glowColor/currentColor/rainbow)
│   └── CustomizeService.java         — glow-команды скорборда; единый тик (попугай/нимб удалены)
├── placeholder/
│   └── FlameCoreExpansion.java       — PlaceholderAPI-расширение `flamecore` (%flamecore_glowcolor%)
├── stream/
│   └── StreamService.java            — /stream <url>: валидация, кулдаун по UUID, broadcast
└── utils/
    ├── MessageUtils.java             — HEX-цвета + translateAlternateColorCodes
    ├── MessageProcessor.java         — теги сообщений: [console]/[sound]/[message] + %player% + pairs;
    │                                    резолвер звуков (старые→новые имена для Paper 1.20.1)
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

### 3.4 Сообщения настраиваемые + HTML-безопасность + теги

**Где:** `TelegramNotifier`, `config.yml`, `BaseCommand`, `MessageProcessor`, `ServerManagementService`.

- Тексты TG берутся из `telegram-messages:` в config через `tgText(key, pairs)` +
  `Main.getCfg().getMultiLine(key)`. Плейсхолдеры: `%player%`, `%ip%`, `%time%`,
  `%command%`, `%timeout%`.
- TG отправляется с `ParseMode.HTML`; **весь** пользовательский контент экранируется
  через `esc()` (`&`→`&amp;`, `<`→`&lt;`, `>`→`&gt;`) — никаких «сырых» тегов из ника/команды.
- Игровые сообщения — из `messages:` списками (единый блочный стиль, `&#RRGGBB` HEX).
  `BaseCommand.sendMessage` больше **не** дописывает префикс сами.
- **Теги сообщений (`MessageProcessor`)**: каждая строка сообщения в конфиге может начинаться
  с тега, который меняет действие:
  - `[console] <команда>` — консоль выполняет команду (авто-подстановка `%player%` и пар);
  - `[sound] <ЗВУК> [громкость] [питч]` — играет звук игроку (по имени enum `org.bukkit.Sound`);
  - `[message] <текст>` — пишет текст игроку;
  - без тега — обычная строка-сообщение (обратная совместимость).
  `MessageProcessor.send(target, list, pairs)` — для одного игрока; `broadcast(list, pairs)` —
  для всех онлайн: `[console]` выполняется ОДИН раз, `[sound]`/`[message]` — каждому игроку.
  Подключён во все места отправки игровых сообщений (BaseCommand, CustomizeService, GiveawayManager,
  StreamService, PromoCodeService/Listener, AdminProtectionService, StreamCommandExecutor).
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

### 3.7 Промокоды

**Где:** `promocode/*`, `PromoCodeDAO`, `FlameCoreCommand` (не трогает), `PromoCodeListener`.

- В config раздел `promo-codes:` — **map**: ключ = название кода (напр. `jonycape`),
  значения = `uses` (лимит **на игрока**), `commands` (консоль выполнит, плейсхолдер `%player%`),
  `colors`/`fade` (HEX без `#`, цвета фейерверка).
- Игрок пишет `/jonycape` → `PromoCodeListener` (priority `MONITOR`) берёт из сообщения первый токен,
  если он есть в карте кодов — `event.setCancelled(true)` и `service.consume(player, name)`.
- `PromoCodeService.consume`:
  1. код не найден → `NOT_FOUND` (зуется в листенер → сообщение `promo-not-found`);
  2. `dao.getUses(code, player_lower)` >= `uses` → `ALREADY_USED` (сообщение `promo-already-used`);
  3. иначе `dao.setUses(...)` (upsert через `MERGE INTO ... KEY`), выполняются `commands` (консоль),
     чат-сообщение `promo-activated` + тайтл `promo-title`/`promo-subtitle` (LegacyComponentSerializer)
     + фейерверк `BURST` с `colors`/`fade` вокруг игрока (`spawnFireworks`, `detonate()` сразу).
- **Семантика `uses`:** счётчик ведётся ПО ИГРОКУ в H2 (`promo_uses(code, player) PRIMARY KEY`),
  поэтому `uses: 1` = каждый игрок может активировать один раз (другие — тоже), а не один раз на всех.
- Таблица создаётся в `PromoCodeDAO.init()` (вызывается из конструктора сервиса).

### 3.8 Кастомизация админов (flamecore.customize)

**Где:** `customize/*`, роутинг в `FlameCoreCommand.handleCustomize`, `PlayerQuitEvent` в `Main`.

> **Сейчас осталось ТОЛЬКО свечение.** Попугай (`parrot`) и нимб (`nimb`) **удалены**
> (кнопки/случаи в `FlameCoreCommand`, парт-иклы, поля/методы в `Customization`/`CustomizeService`,
> ключи `CUSTOMIZE_PARROT_*`/`CUSTOMIZE_NIMB_*` в `ConfigKeys`, секции `customize.yml`).
> Записи ниже — историческая память о том, как это было устроено.

- `/flamecore customize` → меню; `color <цвет>`.
- Права: `flamecore.customize` (`PERM_CUSTOMIZE`). Sub-команды пропускают проверку владельца/панели
  в `FlameCoreCommand` (проверяются до `isOwner`).
- **Свечение (GLOW):** цвет рисуется командой скорборда, в которую входит игрок. Имена команд короткие
  `flamecore_<color.getChar()>` (напр. `flamecore_c`) — ДО 16 символов (иначе `registerNewTeam` падает и цвет
  остаётся белым!). `getTeam` всегда зовёт `setColor`; игрок убирается со старых команд
  (`removeFromColorTeams` → имя начинается с `flamecore_`) перед добавлением в новую — переключение цветов
  работает. Радужный (`rainbow`) = циклическая смена по `RAINBOW[]`.
  **Фикс «всегда белый при TAB»:** НЕ подменяем скорборд игрока целиком (TAB ставит свой скорборд и перебивал
  наш `glowBoard`). Вместо этого цветная команда добавляется на **активный скорборд игрока**
  (`activeBoard` = `player.getScoreboard()`), а в тике проверяется `isOnColorTeam` и переприменяется — команда
  видна клиенту на том скорборде, что он реально показывает. При выключении/выходе игрок снимается с команд
  (`removeFromColorTeams`), скорборд не трогаем.
- **Попугай:** **shoulder-сущность** (`player.setShoulderEntityLeft(parrot)`), а НЕ летающая
  мира-сущность. При включении спавнится временный `Parrot` (`PARROT_VARIANT` RED, `invulnerable`/`silent`),
  вешается на левое плечо, а временная сущность сразу удаляется (`parrot.remove()`) — клиент рисует
  **сидящего** попугая из NBT игрока, без летающей/дублирующейся сущности. В тике `parrotTick` только
  проверяется `getShoulderEntityLeft()==null` и при «слетании» (урон и т.п.) происходит повторная посадка.
  Мапа `parrots: UUID→Parrot` удалена (world-сущностей больше не держим). Выключение/выход/stop —
  `clearShoulder(player)` = `setShoulderEntityLeft(null)`.
- **PlaceholderAPI (цвет под TAB):** расширение `FlameCoreExpansion` (идентификатор `flamecore`,
  плейсхолдер `glowcolor` → текущий цвет свечения как legacy-код, `''` если свечения нет).
  Регистрируется в `Main.initPlaceholders()` через `PlaceholderAPI.registerExpansion` (только если PAPI
  установлен; `persist()`=true). Чтобы цвет работал вместе с TAB — в `plugins/TAB/groups.yml` в конце
  `tagprefix` добавить `%flamecore_glowcolor%` (TAB берёт последний цвет префикса как цвет команды).
  Эффективный цвет хранится в `Customization.currentColor` (выставляется в `setColor` и `applyRainbow`),
  читается через `CustomizeService.currentColor(player)`. PAPI добавлен в `pom.xml` (scope `provided`),
  репозиторий `repo.extendedclip.com`. Без TAB цвет работает и без PAPI (команда скорборда на активном скорборде).
- **Нимб:** белые частицы `Particle.END_ROD` кольцом над головой (радиус/кол-во из конфига,
  `nimb-radius`, `nimb-particles`).
- **Оптимизация:** единый `runTaskTimer` (task-interval, дефолт 2 тика); если `active` и `parrots` пусты —
  мгновенный выход. Эффекты гейтятся своими intervals (`glow-interval`, `nimb-interval`).
  `stop()`/`onQuit` чистят: убирают с команд, снимают GLOWING, удаляют попугаев.

### 3.9 Стримеры (flamecore.stream)

**Где:** `stream/StreamService`, `FlameCoreCommand.handleStream`.

- `/flamecore stream <url>` → право `flamecore.stream`; `StreamService.announce`:
  валидация URL (http/https, длина ≤200) → кулдаун **по UUID игрока** (`stream.cooldown` сек,
  мэпка `cooldowns`, кол-во выводится в `stream-cooldown`) → `broadcastMessage(stream-broadcast)`
  c плейсхолдерами `%player%`/`%url%`.
- Разные стримеры не мешают друг другу; `0` = без ограничения.

> ⚠️ **Telegram 404:** если `bot.token` — это заглушка `ВАШ_ТОКЕН_БОТА` (или токен неверный),
> либа шлёт `GetUpdates ... 404 Not Found` (такого бота нет). Ожидаемо, лечится реальным токеном
> от `@BotFather` + `/flamecore reload`.

### 3.10 Кик не-админов с запрещёнными пермишенами

**Где:** `AdminProtectionService.onPlayerJoin`/`hasForbiddenPermission`/`kickNonAdmin`,
`TelegramNotifier.sendKickNonAdmin`, `ConfigKeys.KICK_NON_ADMIN_PERMISSIONS`.

- В `onPlayerJoin`, если игрок **не в `admins`** и **не владелец** — проверяется `hasForbiddenPermission`:
  1. `player.isOp()` → всегда кик (независимо от списка);
  2. далее проход по списку `protection.kick-non-admin-permissions` — если `player.hasPermission(perm)` → кик.
- `kickNonAdmin`: лог в консоль + `sendKickNonAdmin` владельцу (TG-текст `telegram-messages.kick-non-admin`,
  плейсхолдеры `%player%`, `%ip%`, `%time%`) + `player.kick` (текст `messages.kick-not-admin`).
- Зарегистрированы админы и владелец этой проверкой **не задеваются** — для них идёт обычный путь freeze/approve.
- Вызов происходит на входе (HIGHEST), игрок ещё online → `kick()` работает.
- Пермишены в конфиге: `*`, `minecraft/bukkit.command.op`, `luckperms.*`, `group.admin` (пример, поправимо под нужды).

### 3.11 Главный фикс зависания сервера — Telegram АСИНКРОННЫЙ

**Где:** `TelegramNotifier`.

**Баг:** при входе админа/владельца `onPlayerJoin` (main-поток) звал `sendOwnerLogin`
→ `sendMessageTo` → `bot.execute(...)` — **синхронный блокирующий HTTP-запрос**. Если Telegram
недоступен (нет интернета/неверный токен), `Socket.connect` к `api.telegram.org` блокировал
**главный поток сервера** на 10–15+ сек (хартбит: «The server has not responded for N seconds»),
т.е. весь сервер «замерзал» при входе админа.

**Фикс:**
1. ВСЕ отправки в Telegram (`sendMessageTo`, `answerCallback`, `removeKeyboard`, `editKeyboard`)
   обёрнуты в `executeAsync()` = `Bukkit.getScheduler().runTaskAsynchronously(plugin, …)` —
   HTTP больше никогда не выполняется на главном потоке.
2. `available = !token.isEmpty() && !token.contains("ВАШ")` — токен-заглушка
   `ВАШ_ТОКЕН_БОТА` теперь считается НЕдоступным: нет прослушивания `getUpdates` (нет спама 404)
   и нет пустых попыток connect при каждом входе.

> **ПРАВИЛО:** Telegram I/O (и вообще любой сетевой вызов) — только асинхронно/на фоне,
> никогда не в main-потоке (`runTask` для Bukkit-реакций, `runTaskAsynchronously` для HTTP).

### 3.12 Конфиг разбит на файлы (ConfigManager)

**Где:** `ConfigManager`, ресурсы `src/main/resources/*.yml`.

Вместо одного `config.yml` настройки лежат по подсистемам (в `config.yml` — только самое важное):

| Файл | Разделы |
|------|---------|
| `config.yml` | `database`, `bot`, `owner` |
| `admin-guard.yml` | `admins`, `protection`, `telegram-messages` |
| `messages.yml` | `messages` (все игровые сообщения) |
| `customize.yml` | `customize` |
| `stream.yml` | `stream` |
| `promo-codes.yml` | `promo-codes` |
| `donatetop.yml` | `donatetop`, `donatetop-hologram` |

- `ConfigManager.load()` сохраняет каждый ресурс при первом запуске (`saveResource`), грузит все файлы
  и **сливает** их в один `merged` `YamlConfiguration` + общий кэш (`rebuildMerged`).
- **Пути ключей не изменились** — весь код читает через `ConfigKeys` как раньше.
- `FILE_BY_PREFIX` мапит первый сегмент пути → файл: `set(path, value)` пишет в нужный файл,
  `save()` сохраняет все файлы (так `DonateTopService` сохраняет точки спавна в `donatetop.yml`).
- `getConfig()` возвращает merged — `getLocation(...)` работает.

### 3.13 Топ по платежам (flamecore.admin)

**Где:** `donatetop/*`, `DonateTopDAO`, роутинг `addpayment`/`paymenttop` в `FlameCoreCommand`,
`initDonateTop()` в `Main`.

- **Данные (H2):** `donate_top(player VARCHAR PK, payments INT)`. `addPayment(player)` — upsert +1
  (`MERGE INTO ... WHEN MATCHED UPDATE +1 ... NOT MATCHED INSERT 1`); `clear()` — вайп;
  `getTop(limit)` — топ по `payments DESC, player ASC`.
- **NPC:** житель `Villager` на точке `donatetop.npc-location`, `AI=false`, `invulnerable`,
  `silent`, `collidable=false`, `canPickupItems=false`, `persistent`; **без имени**
  (имя над головой убрано). Неубиваем: в `Main` слушатель `EntityDamageEvent` отменяет урон по нему
  (`donateTopService.isNpc(entity)`).
- **Взгляд на игрока:** `startLookTask()` — тик находит ближайшего игрока в радиусе `LOOK_RANGE` (8)
  и поворачивает жителя лицом к нему (`teleport` с направлением).
- **Голограмма (DecentHolograms):** на `donatetop.hologram-location` (независимо от NPC — правка `y`
  поднимает только голограмму). Текст — `donatetop-hologram` список, плейсхолдеры
  `%flamecore_donatetop_1%`…`_5%` (места 1–5, пустые = `N/A`). Создаётся программно
  (`new Hologram(name, loc)`, `setSaveToFile(false)`), ид-строка `flamecore_donatetop`.
- **Обновление:** `refresh()` каждые `donatetop.update-interval` минут; `HologramLine.setText(...)`
  обновляет строки на месте. При remove/stop — `hologram.destroy()` (обязательно, иначе старая
  голограмма остаётся в мире) + `manager.removeHologram(name)`.
- **Персистентность:** `spawn` пишет `npc-location` и `hologram-location` в `donatetop.yml`;
  `init()` при загрузке читает их и восстанавливает NPC + голограмму (переживает рестарт).
- Включён только если установлен DecentHolograms (`plugin.yml: softdepend`), иначе — пропуск.

### 3.14 Резолвер звуков (MessageProcessor)

**Где:** `MessageProcessor.resolveSound`.В Paper 1.20.1 старые короткие имена звуков (`LEVEL_UP`, `NOTE_PLING`, `ORB_PICKUP`,
`PARROT_AMBIENT`, …) **удалены** из enum `org.bukkit.Sound` (остались только `ENTITY_*`/`BLOCK_*`).
`Sound.valueOf("LEVEL_UP")` кидал исключение, и тег `[sound]` молча не играл.

`resolveSound(raw)`:
1. прямое имя (`Sound.valueOf`);
2. алиас из `SOUND_ALIASES` (например `LEVEL_UP`→`ENTITY_PLAYER_LEVELUP`,
   `NOTE_PLING`→`BLOCK_NOTE_BLOCK_PLING`, `PARROT_*`→`ENTITY_PARROT_*`);
3. пробует префиксы `ENTITY_`, `BLOCK_`, `UI_`, `ITEM_`, `AMBIENT_`.

Поэтому в конфиге работают и старые, и новые имена звуков.

### 3.15 Переключатели модулей (config.yml → modules)

**Где:** `Main.isModuleEnabled`, `ConfigManager.getBoolean`, `ConfigKeys.MODULE_*`.

В `config.yml` добавлена секция `modules:` — каждый модуль включается/выключается отдельно
(`true`/`false`):

| Ключ | Что отключает |
|------|---------------|
| `modules.protection` | защиту входа админов/владельца через Telegram + защиту опасных команд |
| `modules.giveaway` | конкурсы `/giveaway` |
| `modules.promocode` | промокоды |
| `modules.customize` | кастомизацию (свечение) |
| `modules.stream` | анонсы стримов |
| `modules.donatetop` | топ по платежам (NPC + голограмма) |
| `modules.playerinfo` | `/info` в Telegram-боте |
| `modules.donate` | донат-алерт (`/flamecore donate`) |

> Сбор статистики игроков (первый/последний вход, время онлайн, история IP) в `PlayerStatsService`
> работает **всегда**, независимо от `modules.playerinfo` — данные пригодятся другим фичам.

- `Main.onEnable()` вызывает `isModuleEnabled(key)` перед каждым `init*` — если модуль выключен,
  сервис **не создаётся** (остаётся `null`), слушатели не регистрируются.
- `Main.reloadPlugin()` тоже учитывает модули: при выключенном `protection` не пересоздаёт
  `AdminProtectionService`/dangerous, при выключенном `giveaway` не пересоздаёт `GiveawayManager`.
- `logDisabledModules()` пишет в консоль список отключённых модулей при старте.
- Команды проверяют сервис на `null` и показывают сообщение `messages.module-disabled`
  (плейсхолдер `%module%`), если модуль выключен:
  `FlameCoreCommand` (customize/stream), `GiveawayCommandExecutor`, `StreamCommandExecutor`.
- `ConfigManager.getBoolean(path, def)` добавлен для чтения булевых флагов.

---

### 3.16 Оповещение о покупке (донат-алерт)

**Где:** `DonateService`, подкоманда `donate` в `FlameCoreCommand`.

Команда **только для консоли/сайта** (т.е. для администраторов, у которых есть доступ к
консоли/сайту; игрок увидит `messages.donate-console-only`):
```
/flamecore donate <ник> <что-купил>
```

- `<что-купил>` объединяется из всех оставшихся аргументов и **колоризится**
  (`&c&lИНФЕРНО` → красный жирный), плейсхолдеры `%player%`/`%item%`.
- Шаблон объявления — `messages.donate-broadcast` (в нём же `[sound]` играет всем).
- Если игрок онлайн — вокруг него спавнятся **3 фейерверка** (burst) + звук на весь сервер.
- Управляется модулем `modules.donate` (если выключен — команда показывает `module-disabled`).

Пример шаблона:
```
НОВАЯ ПОКУПКА
ТОЛЬКО ЧТО ИГРОК jonycape КУПИЛ:
<цвет>ИНФЕРНО + 1000 ОГНЕЙ.
СПАСИБО ЕМУ, ТЕПЕРЬ АДМИН ПОЧИНИТ ХОСТ.
```

---

### 3.17 Информация об игроке в Telegram (/info)

**Где:** `PlayerInfoService`, `PlayerStatsService`, `PlayerStatsDAO`.

Новые таблицы H2: `player_stats` (ник/UUID/первый вход/последний вход/общее время/IP)
и `player_ips` (история IP по входам). `PlayerStatsService` (слушатель входа/выхода)
ведёт статистику и сессии — общее время корректно растёт и за текущий онлайн.

В чат бота — **владельцу и всем админам** из списка `admins` (ник → Telegram ID), которым
послано `/info` в личку бота (ответ уходит именно отправившему):

```
/info <ник>
```

Отправляется меню **«ИНФОРМАЦИЯ ОБ ИГРОКЕ <ник>»** с 3 кнопками:

| Кнопка | Содержимое |
|--------|-----------|
| **Основная информация** | ник, UUID, реальный IP, страна/город по IP (geo), первый вход, последний вход, общее время онлайн, статус online/offline |
| **Игровая информация** | мир, координаты X/Y/Z, режим игры, уровень опыта, здоровье, голод (только если игрок в сети) |
| **Модерация** | активные муты (LiteBans), история всех IP с датой первого входа |

- **Страна/город** — бесплатный `ip-api.com` (без ключа), кэшируется по IP, запрос асинхронный
  (НЕ на главном потоке).
- **Муты** — через API LiteBans **рефлексией** (`litesolutions.litebans.api.Database.isPlayerMuted`)
  — жёсткой зависимости нет, если LiteBans нет — показывается «—». В `plugin.yml` добавлен
  `softdepend: LiteBans`.
- **Клиент/лаунчер** — не выводится (на Paper надёжно не определяется).
- Тексты сообщений — `playerinfo.yml` (`playerinfo.tg-menu/tg-basic/tg-game/tg-mod`, HTML, плейсхолдеры).
- Модуль `modules.playerinfo` (нужен включённый `modules.protection` — `/info` идёт через бота);
  сам `PlayerInfoService` создаётся при включённом `playerinfo` внутри `initProtection()`.

---

## 4. Что доделать ЗАВТРА (TODO)

> ### 🚨 ПЕРВОЕ И ГЛАВНОЕ — `/info <ник>` в боте не отвечает
>
> (Подробно — в блоке в самом верху этого файла.) Кнопки работают, message-апдейты — нет.
> Починить обработку входящих сообщений в боте, затем **убрать `[DEBUG]`-логи** из
> `TelegramNotifier` (`processUpdates`/`handleMessage`) и `PlayerInfoService` (`handleCommand`).

- [ ] Проверить в бою **переключатели модулей** (`config.yml → modules`): выключить каждую фичу отдельно,
      проверить, что команда показывает `module-disabled` и сервис не грузится.

- [ ] Проверить в бою **топ по платежам**: спавн NPC/голограммы, живой житель (взгляд на игрока,
      неубиваемость), обновление топа, переживание рестарта, `paymenttop remove`/`clear`.
- [ ] Проверить в бою 3 кнопки владельца и сжатие клавиатуры после «Впустить».
- [ ] Проверить в бою защиту опасных команд: авто-выполнение по таймауту и бан по «Отклонить».
- [ ] Проверить в бою TTL входа (`protection.login-timeout`) — авто-разморозка по таймауту.
- [ ] Проверить в бою промокоды (фейерверк, лимит на игрока), косметику (цвета GLOW)
      и анонсы стримов (кулдаун).
- [ ] Вставить реальный токен бота (`bot.token`) + реальный `owner.telegram_id` (полный РЕСТАРТ, НЕ `/flamecore reload` — он не пересоздаёт бота). *(Bot I/O уже асинхронный)*
- [ ] Проверить в бою **кик не-админов с запрещённым пермишеном** (в т.ч. op) и алерт владельцу.
- [ ] Проверить в бою **донат-алерт** (`/flamecore donate <ник> <товар>` из консоли): объявление, цветной `%item%`, фейерверки вокруг игрока, звук всем.
- [ ] Проверить в бою **`/info <ник>`** в боте: 3 кнопки, первый/последний вход, общее время (накапливается), история IP, гео по IP, муты через LiteBans.

> Это утро — баги кастомизации и команды (ФИНАЛЬНЫЕ фиксы):
>
> - **Попугай-дюп** (прыжок → старый оставался сзади, новый на плече): раньше рисовал
>   `setShoulderEntityLeft` — он КЛОНИРУЕТ сущность по NBT, а клиент при прыжке сбрасывает плечо.
>   **Окончательный фикс:** плечевые сущности убраны полностью. Попугай — реальная сущность-**пассажир**
>   игрока (`player.addPassenger`): ванильная механика, не отваливается при прыжке/полёте и не создаёт
>   следа дубликатов (раньше был телепорт к плечу каждый тик — тоже сбрасывался при прыжке/полёте).
> - **GLOW всегда белый / радужный не работает**: цвет видят ТОЛЬКО если команда на «активном»
>   скорборде игрока. **Окончательный фикс:** заведён собственный `glowBoard` (`getNewScoreboard()`).
>   `ensureGlow`/`applyRainbow` ставят `player.setScoreboard(glowBoard)` и добавляют в команду именно его —
>   плагины скорбордов/никнеймов не могут перебить → цвет применяется всегда (не белый).
> - **`%color%` показывало «&cКрасный» дословно**: `MessageUtils.color().replace()` колоризил ДО,
>   а вставляемый цвет уже был с `&c`. Фикс: `.replace("%color%", …)` ДО `.color()`. (off/rainbow/fixed.)
> - **Промокоды теперь реальные команды**: `PromoCodeService.load()` регистрирует каждый код в
>   `commandMap` (`PromoCodeCommand` — no-op `execute`), поэтому `/код` не подсвечивается как
>   неизвестный и живёт в Tab; консумит по-прежнему `PromoCodeListener` (MONITOR, cancel). Без двойного срабатывания.
> - **Добавлен `/stream <url>`** — отдельная команда (executor `StreamCommandExecutor`,
>   команда в plugin.yml, право `flamecore.stream`), дублирует `/flamecore stream`.

> Сделано в это утро (ранее): кик не-админов с запрещёнными пермишенами — `AdminProtectionService`
> (op + список `protection.kick-non-admin-permissions`), TG-алерт владельцу и кик-сообщение
> `messages.kick-not-admin`. Промокоды/косметика/стримы уже проверяются на тест-сервере.

> Сделано в этот заход (промокоды, косметика, стримеры):
>
> - **Промокоды**: `promo-codes` в config, лимит использования НА ИГРОКА (H2 `promo_uses`),
>   перехват через `PlayerCommandPreprocessEvent`, консоль-команды с `%player%`, тайтл + фейерверк.
> - **Косметика** (`flamecore.customize`): `/flamecore customize color|parrot|nimb`.
>   Свечение = GLOW-эффект + цвет команды скорборда (исправлен баг белого цвета: имена команд
>   были длиннее 16 символов для dark/light-цветов — теперь `flamecore_<char>`).
>   Попугай = один фикс.вариант (RED), invulnerable, не слетает (ре-посадка каждый тик),
>   старый удаляется при респавне (нет «миллиона попугаев»).
>   Нимб = белые `END_ROD` частицы. Единый оптимизированный тик + чистка при выходе/reload.
> - **Стримеры** (`flamecore.stream`): `/flamecore stream <url>` -> broadcast + кулдаун по игроку.
> - **ConfigManager**: добавлены `getDouble`, `getSection` (для промокодов).
> - **plugin.yml**: права `flamecore.customize`, `flamecore.stream`.
> - README обновлены.

> Ранее (старый заход): убраны все java-комментарии из классов; TTL pendingLogins +
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
`com.github.pengrad:java-telegram-bot-api 6.9.0` (OkHttp 4.10.0, okio 3.2.0, kotlin-stdlib 1.8.10),
`com.github.decentsoftware-eu:decentholograms 2.10.1` (provided; item-nbt-api исключён — не резолвится с codemc).
