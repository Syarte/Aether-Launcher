# Доступ к Minecraft API: как устроен процесс и что делать

## Честная картина

Регистрация приложения в Microsoft Entra ID делается за десять минут и
работает сразу — вход Microsoft, Xbox Live и XSTS пройдут без всяких заявок.
Упирается всё в один шаг: `POST /authentication/login_with_xbox` отвечает

```
403 { "errorMessage": "Invalid app registration, see https://aka.ms/AppRegInfo" }
```

для любого client ID, которого нет в списке разрешённых у Minecraft Services.

Что известно про этот список:

- Единственная публичная точка входа — `https://aka.ms/AppRegInfo`. Форма
  подачи существует, но публичного SLA у неё нет.
- Поддержка Microsoft в ответах на Microsoft Q&A по таким обращениям
  отправляет в программу Xbox Developer / ID@Xbox. Насколько это относится
  именно к Minecraft Services, из ответов не следует — команды разные.
- Разработчики независимых лаунчеров публично жалуются, что подавали форму
  по несколько раз и не получали ответа. Одобрения существующих лаунчеров
  (Prism, MultiMC и другие) выданы, но процедура для новых заявителей
  непрозрачна.

Вывод, который стоит принять до старта: **получение доступа не гарантировано
и не имеет предсказуемого срока.** Планировать релиз с датой, завязанной
на одобрение, нельзя. Обходить проверку — тоже: чужой client ID это чужие
учётные данные, а самостоятельный обход означает подделку идентификации
приложения перед сервисом Microsoft.

## Что делать практически

1. **Подать заявку сегодня**, до написания остального кода. Срок ожидания
   не зависит от готовности продукта, а очередь двигается медленно.
2. **Сделать проект публичным до подачи.** Репозиторий с открытым кодом,
   внятный README и страница релизов — это то, чем заявка отличается
   от «дайте доступ, я что-то пишу». Отсутствие обхода лицензирования
   должно быть видно по коду, а не по обещанию в письме.
3. **Разрабатывать параллельно.** До одобрения работает всё, кроме одного
   запроса: список версий, загрузка, установка Forge, интерфейс,
   автообновление. Полезно предусмотреть режим, в котором запуск игры
   отключён с честным объяснением, а не с заглушкой.
4. **Продублировать по нескольким каналам** — форма, ID@Xbox, Microsoft Q&A
   с тегом Entra ID. Одна и та же заявка, одинаковый текст, чтобы обращения
   можно было связать между собой.
5. **Не отправлять секреты.** В переписке допустимы client ID, ссылка на
   репозиторий, correlation ID неудачного запроса и UTC-время. Токены,
   device code и refresh-токены — никогда, даже по прямой просьбе.

## Черновик заявки

Заменить поля в угловых скобках. Английский — Minecraft Services и ID@Xbox
работают на нём.

---

**Subject:** Request for Minecraft Services API access — third-party Java Edition launcher

Hello,

I am requesting that the Entra ID application below be allowed to call
`api.minecraftservices.com/authentication/login_with_xbox`. Microsoft OAuth,
Xbox Live and XSTS all succeed; the request fails only at Minecraft Services
with `403 Invalid app registration (aka.ms/AppRegInfo)`.

**Application details**

- Name: `<название>`
- Purpose: open-source desktop launcher for Minecraft: Java Edition
- Platform: Windows desktop (Kotlin / JVM)
- Entra client type: public client, personal Microsoft accounts only,
  public client flows enabled
- Auth flow: OAuth 2.0 authorization code with PKCE on a loopback redirect
  (RFC 8252), with device authorization grant as a fallback
- Scopes: `XboxLive.signin offline_access`
- Entra Client ID: `<GUID>`
- Source code: `<ссылка на репозиторий>`
- Releases: `<ссылка на страницу релизов>`

**Compliance**

The launcher authenticates only through the official Microsoft, Xbox Live and
Minecraft Services endpoints, and verifies entitlement via
`/entitlements/mcstore` before allowing the game to start. It ships no game
files: the client jar, libraries and assets are downloaded from Mojang's own
metadata endpoints and verified against the published SHA-1 hashes. There is
no offline or "cracked" login path, no modification of the game process
(no javaagent, no injection), and no bundled cheats. The project contains no
advertising, analytics or third-party SDKs; outbound requests are restricted
to a fixed allowlist of Microsoft and Mojang domains, enforced by a test in
the build.

I can supply correlation IDs and UTC timestamps from a fresh reproduction on
request. I will not share access tokens, refresh tokens, device codes or
account credentials.

Thank you,
`<имя, контакт>`

---

## Пока доступа нет

Разумный режим на это время: интерфейс работает полностью, кнопка запуска
показывает, что приложение ожидает одобрения Microsoft, и ссылается на эту
страницу. Для отладки самого конвейера запуска (аргументы, classpath,
процессоры Forge) авторизация не нужна — достаточно подставить любой профиль
и проверить, что процесс JVM стартует и падает уже на проверке сессии.
Так проверяется вся механика, кроме собственно входа.
