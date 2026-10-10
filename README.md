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
| `bot-app` | приложение: long polling (TelegramBots 10), разбор команд, whitelist пользователей, меню команд, главная страница Web UI |
| `starters/receipt-spring-boot-starter` | навык «чеки»: `/add_bill` распознаёт фото чека vision-моделью в Ollama, сохраняет в Postgres, показывает на `/receipts` |

## Запуск

1. Создайте бота у [@BotFather](https://t.me/BotFather) и получите токен.
2. Скопируйте `.env.example` в `.env` и заполните `TELEGRAM_BOT_TOKEN`, `OLLAMA_BASE_URL` и `TGBOT_PUBLIC_URL`.
3. Скачайте модель в Ollama: `ollama pull qwen2.5vl:7b`.
4. Запустите: `docker compose up -d --build`.
5. Отправьте боту `/ping`. Он ответит «Access denied» и пришлёт ваш Telegram id. Впишите этот id в `TGBOT_ALLOWED_USERS` и выполните `docker compose up -d`.
6. Отправьте фото чека с подписью `/add_bill`. Можно и иначе: отправить фото, а потом ответить на него `/add_bill`.

Web UI: `http://<сервер>:8080/`, чеки — на `/receipts`. Авторизации нет, UI рассчитан на локальную сеть.

Если токен пустой, бот к Telegram не подключается, но Web UI работает. Так удобно разрабатывать.

## Сборка и тесты

```bash
./mvnw verify                      # все модули; интеграционные тесты поднимают Postgres через Testcontainers (нужен Docker)
OLLAMA_BASE_URL=http://localhost:11434 ./mvnw test -pl starters/receipt-spring-boot-starter -am \
    -Dtest=ReceiptRecognizerOllamaTest -Dsurefire.failIfNoSpecifiedTests=false   # распознавание настоящей моделью
```

Локально без Docker: Postgres на `localhost:5432` (БД, пользователь и пароль — `tgbot`), затем `./mvnw -pl bot-app spring-boot:run`.

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
   - бин `WebModule` добавляет раздел на главную страницу.
6. Внешние модели и клиенты навык создаёт сам, а не глобальными бинами (см. `ReceiptAutoConfiguration`), чтобы навыки не мешали друг другу.
