# Subscription Service

Управление конфигурацией подписок пользователей на поток объектов. Сервис предоставляет REST API
для создания, просмотра, приостановки, возобновления и удаления подписок. Обработкой Kafka-потока,
фильтрацией и компиляцией RSQL занимается Engine Service — здесь этого нет.

## Стек

- Java 17, Spring Boot 3.3
- PostgreSQL — **единственный Source Of Truth** (JPA + Flyway)
- Redis — runtime-конфигурация (`sub:{id}`, `subs:runtime`) + Pub/Sub сигнал (`subscriptions:changes`)
- Maven

## Архитектура (ключевые компоненты)

| Компонент | Ответственность |
|-----------|-----------------|
| `SubscriptionController` | Публичный REST API в namespace `/api/v1/subscribers/{subscriberName}` |
| `InternalSubscriptionController` | Внутренний `POST /internal/subscriptions/{id}/fail` для Engine |
| `SubscriptionService` | Жизненный цикл подписки, транзакционный write-path (PostgreSQL + Redis) |
| `RuntimeConfigStore` / `RedisRuntimeConfigStore` | Runtime-конфигурация в Redis |
| `ConfigChangePublisher` / `RedisConfigChangePublisher` | Pub/Sub сигнал `CONFIG_CHANGED` |
| `RateCounterStore` | Почасовые счётчики лимитов в Redis |
| `SubscriptionValidator` (stub) | Валидация подписки — сейчас заглушка, публичный API от неё не зависит |
| `QuotaService` | Конфигурируемые Rate Limits |
| `TopicNameResolver` | Вычисление имени топика `subscription.{subscriberName}.{topicPostfix}` |
| `InitializationClient` | Вызов Initialization Service |

## Write-path и целостность

Любая операция изменения конфигурации выполняется в одной транзакции, которая пишет и в PostgreSQL,
и в Redis. Redis — **обязательная часть write-path**: при его недоступности выбрасывается
`RedisUnavailableException`, транзакция PostgreSQL откатывается, клиент получает **HTTP 503**, и
подписка **не считается созданной/изменённой**. Read-операции при недоступности Redis продолжают работать.

## REST API

Базовый путь: `/api/v1/subscribers/{subscriberName}/subscriptions`. `subscriberName` всегда берётся
из URI и никогда из тела запроса. Обращение к чужой подписке возвращает `404`.

| Метод | Путь | Действие |
|-------|------|----------|
| `POST` | `/` | Создать подписку (201) |
| `GET` | `/{id}` | Получить подписку |
| `GET` | `/?status=&topicPostfix=&engine=` | Список с фильтрами |
| `POST` | `/{id}/pause` | ACTIVE → PAUSED |
| `POST` | `/{id}/resume` | PAUSED → ACTIVE (`{"runInitialization":true}` → вызов Initialization Service) |
| `DELETE` | `/{id}` | → DELETED (топик не удаляется) |
| `POST` | `/{id}/initialization` | Запуск Initialization Service (202) |
| `POST` | `/internal/subscriptions/{id}/fail` | Внутренний перевод в FAILED |

### Пример

```bash
curl -X POST http://localhost:8080/api/v1/subscribers/risk-service/subscriptions \
  -H 'Content-Type: application/json' \
  -d '{"topicPostfix":"prod","fields":["dealId","portfolioId","status"],
       "filter":"portfolioId==P1","engine":"EVENT_WITH_REMOVE"}'
```

## Формат ошибки

```json
{ "code": "INVALID_FILTER", "message": "Invalid filter", "details": {} }
```

Коды: `INVALID_FILTER`, `INVALID_FIELDS`, `INVALID_SUBSCRIBER_NAME`, `INVALID_TOPIC_POSTFIX`,
`UNSUPPORTED_ENGINE`, `SUBSCRIPTION_NOT_FOUND`, `INVALID_STATUS`, `QUOTA_EXCEEDED`,
`REDIS_UNAVAILABLE`, `INITIALIZATION_FAILED`.

## Rate Limits (конфигурируемые, `subscription.rate-limits.*`)

Подписок на пользователя, топиков на пользователя, создаваемых подписок в час, initialization в час,
максимум полей, максимальная длина фильтра.

## Валидация полей и фильтра (DataDictionary)

Компонент валидации (`SubscriptionValidator`) на старте загружает метамодель из **DataDictionary**
и проверяет по ней список `fields` и селекторы внутри `filter`.

- Источники (оба URL конфигурируемы, `subscription.metamodel.*`):
  - `GET /api/search-service/metadata` — классы (`sourceValue` ↔ canonical), скалярные поля, иерархия;
  - `GET /api/metamodel/export` — связи (`relationAlias` → target-класс) для путей вида `baseCurrency.code`.
- Формат поля: `Class.field` или `Class.relation.field` (обход связей), например
  `Trade.portfolioId`, `Entity.id`, `FxSpotForwardTrade.baseCurrency.code`. Наследованные поля/связи
  видны через иерархию классов.
- Ошибки: неверные поля → `INVALID_FIELDS`, неверные селекторы фильтра → `INVALID_FILTER`.
  RSQL не компилируется — из фильтра лишь извлекаются селекторы `Class.field` (это работа Engine).
- **Fail-fast**: если метамодель не удалось загрузить на старте, приложение не поднимается.
  URL DataDictionary: env `DATA_DICTIONARY_URL` (по умолчанию `http://data-dictionary:8080`).

> Для локального запуска, помимо PostgreSQL и Redis, должен быть доступен DataDictionary
> с загруженной метамоделью (см. соседний проект `DataDictionary`, его `docker-compose.yml` и
> `seed-data/metamodel-seed.json`).

## OpenAPI / Swagger

Документация генерируется автоматически (springdoc-openapi) и охватывает только публичный API
(`/api/v1/**`); внутренний `/internal/**` в спеку не попадает.

- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

## Kubernetes probes

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness` — проверяет **PostgreSQL и Redis**.

## Запуск локально

```bash
docker compose up -d          # PostgreSQL + Redis
mvn spring-boot:run           # сервис на :8080
```

Конфигурация через переменные окружения: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`,
`REDIS_PORT`, `INIT_SERVICE_URL`.

## Тесты

```bash
mvn test
```

Юнит-тесты сервиса/квот/валидации (Mockito), web-слой (`@WebMvcTest`) и слой репозитория
(`@DataJpaTest` на H2) — не требуют Docker.
