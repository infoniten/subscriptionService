# Эксплуатация и runbook

## Health-пробы

Actuator включён; пробы разделены на liveness и readiness.

| Проба | Endpoint | Ready когда |
|---|---|---|
| Liveness | `GET /actuator/health/liveness` | Процесс поднят |
| Readiness | `GET /actuator/health/readiness` | Доступны **PostgreSQL (`db`) и Redis** |

`show-details: always` — детали видны в теле ответа. Так как read-операции при недоступности Redis
продолжают работать, но write-path требует Redis, readiness намеренно завязан и на PostgreSQL, и на
Redis: если readiness лежит, в первую очередь проверяйте эти две зависимости.

Отдельно: метамодель грузится на старте fail-fast — при недоступности DataDictionary приложение **не
стартует** вообще (это не readiness, а отказ подъёма контекста).

## Метрики

Actuator отдаёт стандартные метрики Micrometer на `/actuator/prometheus` и `/actuator/metrics`
(JVM, HTTP-сервер, пул соединений, Redis, Liquibase и т.п.). Экспонирование:
`management.endpoints.web.exposure.include = health,info,metrics,prometheus`.

Собственных прикладных метрик (кастомных счётчиков/таймеров бизнес-операций) сервис **не** определяет —
N/A. Мониторить операции подписок можно по стандартным HTTP-метрикам (`http.server.requests` с тегами
`uri`/`status`) и логам.

## Миграции Liquibase

Схему применяет Liquibase при старте из `classpath:db/changelog/db.changelog-master.yaml`. Наборы
изменений:

| Changelog | Что создаёт |
|---|---|
| `changes/001-initial-schema.yaml` | Таблицы `subscription`, `subscription_fields`, индексы |
| `changes/002-subscription-target.yaml` | Таблицу `subscription_target` (мульти-класс таргеты) |

`spring.jpa.hibernate.ddl-auto = validate`: Hibernate схему не меняет, только сверяет с сущностями.
Расхождение схемы и модели приведёт к падению старта — это ожидаемая защита. Новые изменения добавляйте
отдельным changeSet и подключайте в мастер-changelog; существующие changeSet не редактируйте.

## Зависимости

| Зависимость | Роль | Влияние при отказе |
|---|---|---|
| PostgreSQL | Source Of Truth конфигурации | Все операции недоступны; readiness = down |
| Redis | Runtime-контракт + Pub/Sub + счётчики квот | Write-операции → `503` и откат; read-операции работают; readiness = down |
| DataDictionary | Метамодель для валидации (загрузка на старте) | Приложение не стартует (fail-fast); `reload()` при недоступности бросает `METAMODEL_UNAVAILABLE` |
| Initialization Service | Запуск initialization-джоб | `POST .../initialization` и `resume?runInitialization=true` → `502 INITIALIZATION_FAILED` |

## Runbook — частые симптомы

| Симптом | Вероятная причина | Что проверить |
|---|---|---|
| Приложение не стартует | Недоступен DataDictionary или пустая метамодель | Логи `Loading metamodel from DataDictionary...`; связность `DATA_DICTIONARY_URL`; что `/api/search-service/metadata/v3` отдаёт непустой `classes` |
| Старт падает на схеме | Расхождение JPA-модели и БД (`ddl-auto: validate`) | Применены ли оба changelog; лог Liquibase; ручной `SELECT` по `subscription*` |
| Под не становится ready | Недоступны PostgreSQL или Redis | Детали `GET /actuator/health/readiness`; связность `DB_URL` / `REDIS_HOST` |
| Создание/изменение отдаёт `503` | Redis недоступен на write-path (`REDIS_UNAVAILABLE`) | Health Redis; сеть до Redis; после восстановления повторить операцию (в PostgreSQL ничего не осталось — был rollback) |
| Создание отдаёт `400 INVALID_TARGETS/FIELDS/FILTER` | `objectClass`/поле/селектор не резолвятся по метамодели или поле не применимо к таргету | `details` в ответе; актуальность метамодели в DataDictionary; корректность путей `Class.field` |
| Создание отдаёт `429 QUOTA_EXCEEDED` | Превышен лимит | Какой лимит (`message`); значения `subscription.rate-limits.*`; счётчики `quota:*` в Redis |
| Переход статуса отдаёт `409 INVALID_STATUS` | Недопустимый переход (напр. resume не из `PAUSED`, любое действие над `DELETED`) | Текущий `status` подписки через `GET /{id}` |
| Обращение отдаёт `404` для существующей подписки | Подписка принадлежит другому `subscriberName` | Что `subscriberName` в URI совпадает с владельцем |
| Движок не видит подписку | `sub:{id}` отсутствует или подписка не того типа/статуса | `sub:{id}` и членство в `subs:runtime` в Redis; `engine` и `status` подписки; был ли `CONFIG_CHANGED` |
| `resume?runInitialization=true` или initialization отдаёт `502` | Initialization Service недоступен | Связность `INIT_SERVICE_URL`; логи `Initialization job requested` / `could not reach Initialization Service` |

## Проверка контракта в Redis

```bash
redis-cli GET sub:sub-fc78797d-888f-447d-9c63-e24ea9a0aaa0   # JSON конфигурации
redis-cli SMEMBERS subs:runtime                              # id всех runtime-подписок
redis-cli SUBSCRIBE subscriptions:changes                    # наблюдать сигналы CONFIG_CHANGED
```

## Деплой

Самостоятельный Spring Boot-сервис (`:8080`). Сборка и запуск:

```bash
mvn clean package
java -jar target/subscription-service-0.1.0.jar
```

Локальные зависимости — `docker compose up -d` (PostgreSQL + Redis, см. `docker-compose.yml`); плюс
доступный DataDictionary с загруженной метамоделью. Вся конфигурация — через env, см.
[конфигурацию](configuration.md).

См. также: [архитектура](architecture.md) · [API](api.md) · [контракт Redis](redis-contract.md) · [модель данных](data-model.md)
