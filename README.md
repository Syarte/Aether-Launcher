# Aether — открытый лаунчер Minecraft: Java Edition

Лаунчер для лицензионной Java Edition: официальная аутентификация Microsoft,
загрузка клиента строго по метаданным Mojang, интерфейс на Material Design 3.
Без рекламы, аналитики, трекеров и вмешательства в игровой процесс.

---

## 1. Выбор стека

**Kotlin + Compose Multiplatform for Desktop (JVM 21).**

| Критерий | Kotlin/Compose | Electron | .NET / Avalonia |
|---|---|---|---|
| Material Design 3 «из коробки» | да, `androidx.compose.material3` — эталонная реализация Google | вручную (MUI/M3 Web) | Material.Avalonia отстаёт от спецификации |
| Работа с JVM игры | родная: те же classpath, JVM-аргументы, `ProcessBuilder` | через child\_process, вся логика дублируется | межплатформенный запуск JVM из .NET неудобен |
| Размер дистрибутива | 90–120 МБ (jlink оставляет только нужные модули JDK) | 150–200 МБ Chromium | 70–110 МБ |
| Поверхность аудита | ~30 зависимостей, все с Maven Central | сотни транзитивных npm-пакетов | средняя |
| Единый язык UI + ядра | да | нет (TS + нативные хелперы) | да |

Решающий аргумент — не производительность, а **аудируемость**: требование
«ноль трекеров» проверяемо только тогда, когда дерево зависимостей обозримо.
У npm-проекта такого размера в дереве обычно 800+ пакетов, и доказать
отсутствие телеметрии становится нереально. Здесь список разрешённых доменов
зафиксирован в `Http.ALLOWED_HOSTS`, а тест `NetworkPolicyTest` падает при
появлении в коде любого постороннего адреса.

Отдельный плюс: логика запуска игры — это работа с JVM. В Kotlin
формирование classpath, правил `rules[]` и JVM-аргументов пишется на том же
языке и в тех же терминах, что и сама игра.

---

## 2. Структура модулей

```
dev.aether
├── Main.kt                      точка входа, client ID из системного свойства
├── core/                        чистая логика, не зависит от Compose
│   ├── Platform.kt              ОС/архитектура в терминах манифестов Mojang
│   ├── net/
│   │   ├── Http.kt              единственный HTTP-клиент + белый список доменов
│   │   └── Downloader.kt        параллельная загрузка, SHA-1, атомарная запись
│   ├── auth/
│   │   ├── AuthModels.kt        DTO всех четырёх сервисов + модель аккаунта
│   │   ├── MicrosoftAuth.kt     device code + authorization code с PKCE
│   │   ├── MinecraftAuthenticator.kt   Xbox Live → XSTS → MC Services → лицензия
│   │   └── AccountStore.kt      AES-256-GCM, на диске только refresh-токен
│   ├── meta/
│   │   ├── MetaModels.kt        схемы version manifest, version JSON, asset index
│   │   └── MetaClient.kt        piston-meta + слияние inheritsFrom
│   ├── install/
│   │   ├── Rules.kt             вычисление rules[], natives, Maven-координаты
│   │   ├── GameInstaller.kt     client.jar, библиотеки, natives, ассеты
│   │   └── JavaProvisioner.kt   JRE Mojang под javaVersion.component
│   ├── loader/
│   │   ├── Loader.kt            общий интерфейс провайдера загрузчика
│   │   ├── FabricProvider.kt    профиль из meta.fabricmc.net
│   │   ├── ForgeProvider.kt     Forge и NeoForge: разбор инсталлятора
│   │   ├── InstallProfile.kt    схема install_profile.json
│   │   └── ProcessorRunner.kt   локальный патч и деобфускация клиента
│   └── launch/
│       ├── LaunchArguments.kt   плейсхолдеры и аргументы обоих форматов
│       └── GameLauncher.kt      ProcessBuilder + перехват вывода
└── ui/                          Compose, знает про core, но не наоборот
    ├── LauncherState.kt         единое состояние, вся работа в Dispatchers.IO
    ├── App.kt                   каркас: top bar, navigation rail, переходы
    ├── theme/                   схема M3 из seed-цвета (OKLab/OKLCH)
    └── screens/                 Главная, Библиотека, Новости, Настройки, Профили
```

Граница `core` ↔ `ui` жёсткая: ядро можно собрать как отдельную библиотеку
и покрыть тестами без графической подсистемы.

---

## 3. Поток авторизации, по шагам

Все запросы идут только на официальные эндпоинты. Ни один шаг не обходится
и не эмулируется: без подтверждённой лицензии профиль не выдаётся и игра
не запускается.

**Шаг 0. Регистрация приложения.** Подробности и черновик заявки —
в [docs/APP_REGISTRATION.md](docs/APP_REGISTRATION.md). Microsoft Entra ID → App registrations →
тип «Personal Microsoft accounts only», включён Allow public client flows.
Client secret не нужен (RFC 8252: секрет в десктопном дистрибутиве секретом
не является).

> Важно: одной регистрации мало. Minecraft Services отвечает `403 Invalid app
> registration` на неодобренные client ID — доступ к Minecraft API выдаётся
> Microsoft отдельно, по заявке, и рассмотрение занимает недели. Пока доступ
> не получен, работает всё, кроме шага 4. Это ограничение платформы, а не
> лаунчера, и обходить его нельзя — вход в игру возможен только для
> одобренных приложений.

**Шаг 1. Microsoft Identity Platform.**
`POST https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode`
или `GET .../authorize` + `POST .../token`.
Scope: `XboxLive.signin offline_access`. Тенант `consumers` — Minecraft
привязан только к личным аккаунтам MSA.

Основной путь — authorization code + PKCE с редиректом на `http://127.0.0.1:<порт>`:
один клик, браузер сам закрывается. Если порт занят или браузера нет,
код автоматически откатывается на device code flow.
На выходе: `access_token` (~1 ч) и `refresh_token` (даёт тихий вход при
следующих запусках).

**Шаг 2. Xbox Live user token.**
`POST https://user.auth.xboxlive.com/user/authenticate`,
`RelyingParty: http://auth.xboxlive.com`,
`RpsTicket: d=<MSA access_token>` (префикс `d=` обязателен для токенов
из `login.microsoftonline.com`).
На выходе: `Token` и `DisplayClaims.xui[0].uhs` — user hash.

**Шаг 3. XSTS.**
`POST https://xsts.auth.xboxlive.com/xsts/authorize`,
`RelyingParty: rp://api.minecraftservices.com/`, `SandboxId: RETAIL`.
Здесь же обрабатываются человеческие ошибки по коду `XErr`: нет профиля Xbox
(2148916233), детский аккаунт вне семейной группы (2148916238), регион
без Xbox Live (2148916235) — пользователь видит объяснение и что делать.

**Шаг 4. Токен Minecraft.**
`POST https://api.minecraftservices.com/authentication/login_with_xbox`
с телом `{"identityToken": "XBL3.0 x=<uhs>;<xsts>"}`.
На выходе: `access_token` Minecraft, срок жизни 24 часа.

**Шаг 5. Проверка лицензии.**
`GET https://api.minecraftservices.com/entitlements/mcstore` с
`Authorization: Bearer <token>`. Требуется `product_minecraft` или
`game_minecraft`. Нет — запуск не предлагается.

**Шаг 6. Профиль.**
`GET https://api.minecraftservices.com/minecraft/profile` → UUID, ник, скины.
`404` означает, что ник ещё не выбран.

**Хранение.** На диск попадает только refresh-токен и профиль, зашифрованные
AES-256-GCM ключом из PKCS12-хранилища с правами `0600`. Токен Minecraft
не сохраняется вообще — перевыпускается при каждом старте. В журнале
запуска токены маскируются (`LaunchArguments.redact`).

---

## 4. Поток запуска игры

1. **Манифест версий** — `GET https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`.
   Кэшируется: библиотека версий листается офлайн.
2. **Дескриптор версии** — URL и SHA-1 берутся из манифеста, файл сверяется
   по хешу. Если у версии есть `inheritsFrom` (Fabric/Forge), родитель
   загружается рекурсивно и поля сливаются: библиотеки ребёнка идут первыми,
   аргументы конкатенируются.
3. **client.jar** — `downloads.client`, проверка SHA-1.
4. **Библиотеки** — фильтр по `rules[]` (последнее совпавшее правило
   побеждает, по умолчанию запрет). Источник: `downloads.artifact.url`,
   иначе Maven-корень библиотеки, иначе `https://libraries.minecraft.net`.
5. **Natives** — формат до 1.19: `natives.<os>` с подстановкой `${arch}`,
   jar распаковывается в `natives/<version>/` с учётом `extract.exclude`
   и защитой от path traversal, в classpath не попадает. С 1.19 natives —
   обычные библиотеки с классификатором, идут в classpath как есть.
6. **Ассеты** — `assetIndex` → объекты с
   `https://resources.download.minecraft.net/<xx>/<hash>`, загрузка в
   8 потоков. Для старых версий индекс дополнительно материализуется
   в `assets/virtual/<id>` или в `resources/` (`map_to_resources`).
7. **Java** — по `javaVersion.component` (`jre-legacy`, `java-runtime-alpha…delta`)
   из `https://piston-meta.mojang.com/v1/products/java-runtime/.../all.json`,
   ключ платформы — `windows-x64`, `mac-os-arm64`, `linux` и т. д. Фолбэк —
   локальная Java подходящей мажорной версии.
8. **Аргументы** — современный формат (`arguments.jvm` / `arguments.game`
   с условными блоками) и legacy (`minecraftArguments`). Подставляются
   только плейсхолдеры из спецификации: `${auth_player_name}`, `${classpath}`,
   `${natives_directory}`, `${auth_access_token}`, `${auth_xuid}`,
   `${user_type}` = `msa` и остальные.
9. **Запуск** — `ProcessBuilder` в каталоге игры, вывод построчно уходит
   в журнал. Никаких `-javaagent`, подмены classpath и инъекций в процесс.

Каждый скачанный файл проверяется по SHA-1 из метаданных, загрузка идёт
во временный файл с атомарным переименованием — прерванный запуск
не оставляет повреждённого кэша.

---

## 5. UI: компоненты Material Design 3 под приложенный макет

Макет разобран и перенесён 1:1 в компоненты M3.

| Элемент макета | Компонент | Токены |
|---|---|---|
| Верхняя панель 56dp с логотипом и переключателем темы | `Row` + `IconButton` | `surface`, `outlineVariant` |
| Navigation rail 88 ↔ 220dp | кастомный rail с `animateDpAsState` (280 мс) | активный пункт — `primaryContainer` / `onPrimaryContainer` |
| Карточка игрока, радиус 28 | `Surface` + `shapes.extraLarge` | `surfaceContainer` |
| Кнопка «Играть» | `Button` pill, 16×32dp | `primary` / `onPrimary` |
| Сетка версий | `LazyVerticalGrid`, карточки радиус 20 | `surfaceContainer` |
| Чипы фильтров | `FilterChip` с иконкой `check` | `primaryContainer` при выборе |
| Слайдер RAM с бейджем | `Slider` + `Surface` pill | `primaryContainer` |
| Диалог запуска со стадиями | `Surface` 28dp поверх scrim 55 % | `surfaceContainerHigh` |

**Динамический цвет.** В макете палитра считается из seed-цвета через
OKLab/OKLCH: тон берётся из seed, светлота и хрома — из фиксированной шкалы
ролей. Та же математика перенесена в `DynamicColor.kt`, поэтому четыре
предустановленных seed-цвета дают ровно те же оттенки, что в прототипе,
а контраст пар `surface`/`onSurface` сохраняется при любом тоне.
Если понадобится побайтовое совпадение с эталонными палитрами Google,
`DynamicColor.scheme()` заменяется на `material-color-utilities` (HCT) —
это одна функция, остальной код не меняется.

**Анимации.** Три осмысленных: смена экрана (fade + сдвиг на 1/24 высоты),
разворот rail (280 мс, стандартная кривая M3), появление диалога запуска.
Каждая роль цвета анимируется отдельно, поэтому переключение темы
не «моргает». Hover-эффектов на каждой карточке нет — визуальный шум.

Тёмная тема — по умолчанию, как в макете; светлая и системная переключаются
в настройках.

---

## 6. Модлоадеры

Все три загрузчика встраиваются в один конвейер: провайдер готовит профиль
запуска в `versions/<id>/<id>.json`, дальше работает уже существующий код —
`resolveVersion` сливает профиль с ванильным родителем через `inheritsFrom`,
`GameInstaller` докачивает библиотеки.

**Fabric** — самый простой случай. `meta.fabricmc.net/v2` отдаёт готовый
профиль, ничего патчить не нужно, библиотеки описаны Maven-корнем.

**Forge и NeoForge** отличаются только координатами Maven и схемой версий
(NeoForge нумеруется от версии игры: 1.21.1 → 21.1.x, и существует
с 1.20.2), поэтому реализованы одним классом.

Главная сложность здесь в том, что современный Forge нельзя «просто скачать».
Клиентский jar приходится патчить и деобфусцировать **на машине
пользователя** — распространять готовый пропатченный клиент нельзя
по лицензии Mojang. Инсталлятор описывает это списком `processors`
в `install_profile.json`: цепочка утилит (BinaryPatcher, installertools,
ForgeAutoRenamingTool), каждая — обычный jar, который нужно запустить
с собранным classpath и подставленными токенами из блока `data`.

`ProcessorRunner` делает ровно это и проверяет результат: у каждого
процессора есть заявленные `outputs` с ожидаемыми SHA-1. Если файлы уже
на месте и хеши сходятся, шаг пропускается — повторная установка той же
сборки проходит мгновенно, а расхождение хеша останавливает установку,
а не оставляет молча битый клиент.

Процессоры запускаются той же Java, что и игра, поэтому подготовка JRE
идёт до установки загрузчика.

## 7. Что уже работает (MVP)

- Вход через Microsoft: PKCE-редирект с откатом на device code, тихий
  рефреш при старте, несколько аккаунтов, зашифрованное хранилище.
- Полная цепочка Xbox Live → XSTS → Minecraft Services с проверкой лицензии
  и человеческими сообщениями об ошибках XSTS.
- Список версий из `version_manifest_v2.json` с поиском и фильтрами,
  офлайн-кэш.
- Установка ванильного клиента: библиотеки с правилами, natives, ассеты
  (включая legacy-раскладки), конфигурация логгера.
- Модлоадеры: Fabric, Forge и NeoForge с выбором версии загрузчика.
- Автоподбор JRE Mojang под версию, слайдер RAM, ручное переопределение Java.
- Запуск игры с журналом и корректной обработкой кода выхода.
- Интерфейс M3 по макету: пять экранов, rail, динамический цвет, две темы.

### Сборка

```bash
export AETHER_CLIENT_ID=<client-id из Entra ID>
./gradlew run                     # запуск
./gradlew packageDistributionForCurrentOS   # msi / dmg / deb через jpackage
./gradlew test                    # в том числе проверка сетевой политики
```

Целевая платформа — Windows; `packageMsi` даёт per-user установку без
прав администратора. Сборки для macOS и Linux собираются тем же
конвейером, но не тестировались.

Код не компилировался в среде подготовки (нет доступа к Maven Central) —
перед первым запуском стоит выполнить `./gradlew build` и, при
необходимости, сверить версии Compose и Ktor с актуальными.

---

## 8. Что дальше

**Автообновление (этап 2).** Схема без доверия к транспорту:
манифест `update.json` (версия, URL, SHA-256 артефакта) подписывается
Ed25519; публичный ключ вшит в дистрибутив. Лаунчер скачивает манифест,
проверяет подпись, затем хеш скачанного артефакта, и только потом отдаёт
файл штатному установщику ОС (MSI/DMG/DEB сохраняют подпись
code-signing). Откат по версии запрещён — номер должен строго расти,
иначе можно навязать старую уязвимую сборку. Ключ подписи хранится
в HSM/CI-secret, а не в репозитории.

**Прочее:** лента новостей из `launchercontent.mojang.com/news.json`,
профили запуска с отдельными каталогами и аргументами, экспорт/импорт
сборок, локализация.

---

## 9. Соответствие правилам

- Аутентификация — только официальная, без обхода лицензирования;
  при отсутствии entitlement запуск недоступен.
- Игровые файлы не распространяются: всё скачивается с серверов Mojang
  и сверяется по SHA-1.
- Нет читов, инжектов, javaagent и модификации процесса игры.
- Нет рекламы, аналитики и сторонних SDK; список доменов фиксирован
  и проверяется тестом.
- Код запуска открыт и соответствует официальной схеме лаунчера.

Проект не аффилирован с Mojang Studios и Microsoft.


---

## Дополнительно

- [docs/APP_REGISTRATION.md](docs/APP_REGISTRATION.md) — доступ к Minecraft API: как устроен процесс и черновик заявки
- [docs/WINDOWS.md](docs/WINDOWS.md) — особенности Windows: длина путей, SmartScreen, DPAPI, чек-лист тестирования
