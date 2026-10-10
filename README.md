# tg-bot

Личный Telegram-бот с подключаемыми навыками. Работает через long polling, поэтому подходит для сервера за NAT: нужен только исходящий доступ к `api.telegram.org`.

Навыки — это Spring Boot стартеры из этого же репозитория. Навык может принести:
- команды бота;
- таблицы в БД со своими миграциями;
- раздел Web UI.

## Модули

| модуль | что это |
|---|---|
| `bot-api` | контракт навыков: `Command`, `CommandContext`, `Attachment`, `Reply`, `WebModule`; без зависимостей от Telegram-библиотеки |
| `bot-app` | приложение: long polling (TelegramBots 10), разбор команд, пользователи и их Telegram-аккаунты, вход в Web UI по ссылке из бота, меню команд, главная страница Web UI |
| `starters/receipt-spring-boot-starter` | навык «чеки»: загрузка фото чека через `/bill` или `/receipts`, статус и удаление; пока без распознавания, хранение в памяти |

## Запуск

1. Создайте бота у [@BotFather](https://t.me/BotFather) и получите токен.
2. Скопируйте `.env.example` в `.env` и заполните `TELEGRAM_BOT_TOKEN` и `TGBOT_PUBLIC_URL`.
3. Запустите: `docker compose up -d --build`.
4. Отправьте боту `/ping`. Он ответит «Access denied» и пришлёт ваш Telegram id. Впишите этот id в `TGBOT_ADMIN` и выполните `docker compose up -d`.
   Других пользователей добавляет администратор: человек отправляет боту любую команду и получает свой Telegram id, администратор выполняет `/user add <id>`.
5. Отправьте фото чека с подписью `/bill`. Можно и иначе: отправить фото, а потом ответить на него `/bill add`. Бот ответит номером чека; статус — `/bill status <номер>`.

Web UI: `TGBOT_PUBLIC_URL`, чеки — на `/receipts`. Вход — по логину и паролю. В первый раз (или если пароль забыт) отправьте боту `/login` в личном чате: он пришлёт одноразовую ссылку на 5 минут, после входа по ней откроется страница, где задаются логин и пароль.

Пользователи, чеки и сессии пока хранятся в памяти и теряются при перезапуске.

## Сборка и тесты

```bash
./mvnw verify
```

Локально без Docker: `./mvnw -pl bot-app -am install -DskipTests && ./mvnw -pl bot-app spring-boot:run -Dspring-boot.run.profiles=dev`. В профиле `dev` на странице входа есть ссылка «Войти администратором»: вход без бота, токен Telegram не нужен.

## Как добавить навык

1. Создайте модуль `starters/<name>-spring-boot-starter` с зависимостью `bot-api` и добавьте его в `<modules>` корневого `pom.xml` и в зависимости `bot-app`.
2. Реализуйте `Command`:
   - имя только из `a-z`, `0-9`, `_`;
   - `handle` вызывается в виртуальном потоке, долгая работа допустима;
   - для долгой работы используйте `context.reply(...)` и затем `reply.edit(...)`.
3. Зарегистрируйте бины в `@AutoConfiguration` с `@ConditionalOnProperty(prefix = "tgbot.<name>", name = "enabled", matchIfMissing = true)` и пропишите класс в `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
4. Таблицы:
   - миграции кладите в `db/migration/<name>`;
   - запускайте их своим Flyway со своей таблицей истории (пример — `ReceiptSchemaMigrator`), тогда номера версий навыков не конфликтуют;
   - имена таблиц начинайте с префикса навыка.
5. Web UI:
   - контроллеры — под своим префиксом пути;
   - шаблоны — в `templates/<name>/`, статика — в `static/<name>/`;
   - общие стили приложения подключаются через `<link rel="stylesheet" th:href="@{/css/style.css}">`: карточки (`card`), кнопки (`btn`, `btn-secondary`, `btn-danger`), сообщения (`message`, `success`, `error`), таблицы в `table-wrap`, бэджи (`badge`);
   - бин `WebModule` добавляет раздел на главную страницу.
6. Внешние модели и клиенты навык создаёт сам, а не глобальными бинами (см. `ReceiptAutoConfiguration`), чтобы навыки не мешали друг другу.
