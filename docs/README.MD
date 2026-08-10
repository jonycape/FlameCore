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
- **Промокоды** `/название` (команды из конфига + фейерверк, лимит на игрока в H2).
- **Кастомизация админов** (свечение GLOW, попугай на плече, нимб частиц) — `flamecore.customize`.
- **Анонс стримов** `/flamecore stream <url>` — `flamecore.stream`, кулдаун.
- **getDouble** добавлен в `ConfigManager` (для радиусов нимба).

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
│   ├── ConfigKeys.java               — константы ключей config.yml (в т.ч. TG_*, PROMO, CUSTOMIZE_*, STREAM_*)
│   └── ConfigManager.java            — загрузка/кэш; getStringList/getMultiLine/getStringMap/getInt/getDouble/getSection
├── database/
│   ├── DatabaseManager.java          — H2 (локальный файл-база)
│   ├── GiveawayDAO.java
│   ├── ParticipantDAO.java
│   └── PromoCodeDAO.java             — таблица promo_uses(code,player,uses): лимит использования промокода НА ИГРОКА
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
├── promocode/
│   ├── PromoCode.java                — данные кода (@Getter: code/uses/commands/colors/fade)
│   ├── PromoCodeService.java         — загрузка из config, consume() + фейерверк, Result{OK,NOT_FOUND,ALREADY_USED}
│   └── PromoCodeListener.java        — PlayerCommandPreprocessEvent (MONITOR): catch `/код` если есть в конфиге
├── customize/
│   ├── Customization.java            — состояние на игрока (glowColor/rainbow/parrot/nimb)
│   └── CustomizeService.java         — glow-команды скорборда, попугай на плечо, нимб частиц; единый тик
├── stream/
│   └── StreamService.java            — /stream <url>: валидация, кулдаун по UUID, broadcast
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

- `/flamecore customize` → меню; `color <цвет>` / `parrot` / `nimb` (тоглы).
- Права: `flamecore.customize` (`PERM_CUSTOMIZE`). Sub-команды пропускают проверку владельца/панели
  в `FlameCoreCommand` (проверяются до `isOwner`).
- **Свечение (GLOW):** цвет рисуется **командой скорборда** (main scoreboard). Имена команд
  короткие `flamecore_<color.getChar()>` (напр. `flamecore_c`) — ДО 16 символов (иначе `registerNewTeam`
  падает и цвет остаётся белым!). `getTeam` всегда зовёт `setColor`; игрок убирается со старых команд
  (`removeFromColorTeams` → имя начинается с `flamecore_`) перед добавлением в новую — поэтому
  переключение цветов работает. Радужный (`rainbow`) = циклическая смена по `RAINBOW[]`.
- **Попугай:** один `Parrot` на игрока (мапа `parrots: UUID→Parrot`), **фиксированный** вариант
  `PARROT_VARIANT` (RED — один цвет). `invulnerable`/`silent`/`AI(fal=off)`/`gravity(0)`; каждый тик
  проверяется валидность и если не на плече — `setShoulderEntityLeft(parrot)` возвращает его (не слетает).
  При респавне старый **удаляется** (`parrot.remove()`) → нет «миллиона попугаев».
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

---

## 4. Что доделать ЗАВТРА (TODO)

- [ ] **Проверить в бою** 3 кнопки владельца и сжатие клавиатуры после «Впустить».
- [ ] Проверить в бою защиту опасных команд: авто-выполнение по таймауту и бан по «Отклонить».
- [ ] Проверить в бою TTL входа (`protection.login-timeout`) — авто-разморозка по таймауту.
- [ ] Проверить в бою промокоды (фейерверк, лимит на игрока), косметику (цвета GLOW/попугай/нимб)
      и анонсы стримов (кулдаун).
- [ ] Вставить реальный токен бота (`bot.token`) — иначе `GetUpdates 404`.

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
`com.github.pengrad:java-telegram-bot-api 6.9.0` (OkHttp 4.10.0, okio 3.2.0, kotlin-stdlib 1.8.10).
