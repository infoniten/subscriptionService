# REST API

Публичный API живёт целиком внутри namespace подписчика: базовый путь —
`/api/v1/subscribers/{subscriberName}/subscriptions`. `subscriberName` **всегда** берётся из URI и
никогда из тела запроса. Обращение к чужой подписке неотличимо от отсутствующей — `404`.

Внутренний API (`/internal/**`) не входит в публичный namespace, не публикуется в OpenAPI и не
выставляется наружу.

> **Машиночитаемый контракт:** [`openapi.yaml`](openapi.yaml) — экспорт сгенерированной springdoc'ом
> OpenAPI-спеки (только публичный `/api/v1/**`). В рантайме доступна на `/v3/api-docs` (JSON),
> `/v3/api-docs.yaml` (YAML) и в Swagger UI `/swagger-ui.html`.

## Сводка эндпоинтов

| Метод | Путь | Действие | Успех |
|---|---|---|---|
| `POST` | `/api/v1/subscribers/{subscriberName}/subscriptions` | Создать подписку | `201` |
| `GET` | `/api/v1/subscribers/{subscriberName}/subscriptions/{subscriptionId}` | Получить подписку | `200` |
| `GET` | `/api/v1/subscribers/{subscriberName}/subscriptions?status=&topicPostfix=&engine=` | Список с фильтрами | `200` |
| `POST` | `/api/v1/subscribers/{subscriberName}/subscriptions/{subscriptionId}/pause` | `ACTIVE → PAUSED` | `200` |
| `POST` | `/api/v1/subscribers/{subscriberName}/subscriptions/{subscriptionId}/resume` | `PAUSED → ACTIVE` (+ опц. initialization) | `200` |
| `DELETE` | `/api/v1/subscribers/{subscriberName}/subscriptions/{subscriptionId}` | `→ DELETED` (топик не удаляется) | `200` |
| `POST` | `/api/v1/subscribers/{subscriberName}/subscriptions/{subscriptionId}/initialization` | Запуск Initialization Service | `202` |
| `POST` | `/internal/subscriptions/{subscriptionId}/fail` | Внутренний перевод в `FAILED` | `200` |

## Создать подписку

`POST /api/v1/subscribers/{subscriberName}/subscriptions`

Поток: валидация формата → валидация по метамодели → квоты → сохранение в PostgreSQL → запись
runtime-конфигурации в Redis → публикация `CONFIG_CHANGED`. Redis — обязательная часть write-path: при
его недоступности подписка не создаётся (`503`). Создаётся всегда в статусе `ACTIVE`.

### Тело запроса (`CreateSubscriptionRequest`)

| Поле | Тип | Обяз. | Примечания |
|---|---|---|---|
| `topicPostfix` | string | да | Постфикс топика; топик принадлежит паре `subscriberName`+`topicPostfix` |
| `targets` | `TargetRequest[]` | да | Классы-цели (мультикласс); не может быть пустым |
| `fields` | string[] | да | Возвращаемые поля объекта; не может быть пустым |
| `filter` | string | нет | RSQL-фильтр; компиляция — на стороне Engine, здесь валидируются только селекторы |
| `engine` | string | да | Один из `OBJECT_STREAM`, `OBJECT_WITH_PREVIOUS`, `EVENT_WITH_REMOVE`, `OBJECT_BATCH` |

`TargetRequest`:

| Поле | Тип | Обяз. | Примечания |
|---|---|---|---|
| `objectClass` | string | да | Класс объекта (`sourceValue` из метамодели), напр. `FxSpotForwardTrade` |
| `includeSubclasses` | boolean | нет | Включать наследников. По умолчанию `true` (полиморфно); `false` — только точный класс |

Форматные правила (`SubscriptionInputParser`): `subscriberName` — `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`;
`topicPostfix` — `^[a-zA-Z0-9]([a-zA-Z0-9._-]{0,61}[a-zA-Z0-9])?$`; `fields` — `^[a-zA-Z0-9_.]+$`;
`objectClass` — `^[A-Za-z][A-Za-z0-9_]*$`.

```json
{
  "topicPostfix": "prod",
  "targets": [
    { "objectClass": "FxSpotForwardTrade", "includeSubclasses": true }
  ],
  "fields": ["Trade.portfolioId", "FxSpotForwardTrade.baseCurrency.code"],
  "filter": "Trade.portfolioId==P1",
  "engine": "EVENT_WITH_REMOVE"
}
```

### Ответ (`SubscriptionResponse`, 201)

```json
{
  "subscriptionId": "sub-fc78797d-888f-447d-9c63-e24ea9a0aaa0",
  "subscriberName": "risk-service",
  "topicPostfix": "prod",
  "topic": "subscription.risk-service.prod",
  "targets": [ { "objectClass": "FxSpotForwardTrade", "includeSubclasses": true } ],
  "fields": ["Trade.portfolioId", "FxSpotForwardTrade.baseCurrency.code"],
  "filter": "Trade.portfolioId==P1",
  "engine": "EVENT_WITH_REMOVE",
  "status": "ACTIVE",
  "failureReason": null,
  "failureMessage": null,
  "createdAt": "2026-07-17T10:15:30Z",
  "updatedAt": "2026-07-17T10:15:30Z"
}
```

`topic` вычисляется как `subscription.{subscriberName}.{topicPostfix}` (`TopicNameResolver`); сам сервис
топик не создаёт.

## Получить подписку

`GET /api/v1/subscribers/{subscriberName}/subscriptions/{subscriptionId}` → `200` `SubscriptionResponse`.
`404` (`SUBSCRIPTION_NOT_FOUND`), в том числе если подписка принадлежит другому подписчику.

## Список подписок

`GET /api/v1/subscribers/{subscriberName}/subscriptions` → `200` `SubscriptionResponse[]`.

Query-параметры (все опциональны, комбинируются через AND):

| Параметр | Значения | Примечания |
|---|---|---|
| `status` | `ACTIVE` / `PAUSED` / `FAILED` / `DELETED` | Без этого фильтра подписки в статусе `DELETED` скрыты по умолчанию |
| `topicPostfix` | строка | Точное совпадение |
| `engine` | `OBJECT_STREAM` / `OBJECT_WITH_PREVIOUS` / `EVENT_WITH_REMOVE` / `OBJECT_BATCH` | Точное совпадение |

Нераспознанное значение `status` → `400 INVALID_REQUEST`; нераспознанное `engine` → `400
UNSUPPORTED_ENGINE`. Предикаты собирает `SubscriptionSpecifications.forListing`.

## Приостановить / возобновить

`POST .../{subscriptionId}/pause` — `ACTIVE → PAUSED`. Идемпотентна (повторный вызов для уже
приостановленной — no-op). Из любого другого статуса → `409 INVALID_STATUS`.

`POST .../{subscriptionId}/resume` — `PAUSED → ACTIVE`. Идемпотентна, когда уже `ACTIVE`. Тело
опционально (`ResumeRequest`):

```json
{ "runInitialization": true }
```

При `runInitialization=true` после реактивации дополнительно вызывается Initialization Service (учёт
почасовой квоты initialization; `429` при её превышении, `502 INITIALIZATION_FAILED` при недоступности
сервиса). Из статуса, отличного от `ACTIVE`/`PAUSED`, → `409 INVALID_STATUS`.

## Удалить

`DELETE .../{subscriptionId}` — `ACTIVE/PAUSED/FAILED → DELETED`. Идемпотентна (повтор для уже
удалённой — no-op). Удаляет runtime-конфигурацию из Redis и публикует `CONFIG_CHANGED`. Kafka-топик
**не** удаляется.

## Запустить initialization

`POST .../{subscriptionId}/initialization` → `202`. Subscription Service вызывает Initialization Service;
сам выгрузкой данных не занимается. Учитывается почасовая квота initialization. Для подписки в статусе
`DELETED` → `409 INVALID_STATUS`.

## Внутренний: перевод в FAILED

`POST /internal/subscriptions/{subscriptionId}/fail` — используется Engine Service, когда фильтр
подписки перестал компилироваться после изменения модели. Обратите внимание: путь **без** namespace
подписчика и `subscriberName` в нём нет.

Тело (`FailRequest`):

| Поле | Тип | Обяз. |
|---|---|---|
| `reason` | string | да (`@NotBlank`) |
| `message` | string | нет |

Переводит подписку в `FAILED` (из любого статуса, кроме `DELETED` → `409`), сохраняет
`failureReason`/`failureMessage`, удаляет из runtime и публикует `CONFIG_CHANGED`. Возвращает
`SubscriptionResponse`.

## Формат ошибки

Единый ответ (`ErrorResponse`) для всех ошибок:

```json
{ "code": "REDIS_UNAVAILABLE", "message": "Redis unavailable", "details": {} }
```

`details` может содержать пофайловые причины (например, `{"fields":["unknown field ..."]}`).

### Коды ошибок и HTTP-статусы (`ErrorCode`)

| Код | HTTP | Когда |
|---|---|---|
| `INVALID_FILTER` | 400 | Селектор в фильтре не резолвится по метамодели |
| `INVALID_FIELDS` | 400 | Пустой список полей или поле не резолвится / не применимо к таргету |
| `INVALID_TARGETS` | 400 | Пустой список таргетов или неизвестный `objectClass` |
| `INVALID_SUBSCRIBER_NAME` | 400 | `subscriberName` не соответствует формату |
| `INVALID_TOPIC_POSTFIX` | 400 | `topicPostfix` не соответствует формату |
| `UNSUPPORTED_ENGINE` | 400 | Неизвестное значение `engine` (в теле или в фильтре листинга) |
| `INVALID_REQUEST` | 400 | Некорректное тело / параметр (в т.ч. неизвестный `status`) |
| `SUBSCRIPTION_NOT_FOUND` | 404 | Нет такой подписки или она принадлежит другому подписчику |
| `INVALID_STATUS` | 409 | Недопустимый переход статуса |
| `QUOTA_EXCEEDED` | 429 | Превышен один из лимитов (см. [конфигурацию](configuration.md)) |
| `REDIS_UNAVAILABLE` | 503 | Redis недоступен на write-path — операция откачена |
| `METAMODEL_UNAVAILABLE` | 503 | Метамодель не загружена / недоступна |
| `INITIALIZATION_FAILED` | 502 | Не удалось вызвать Initialization Service |
| `INTERNAL_ERROR` | 500 | Непредвиденная ошибка |

## OpenAPI / Swagger

Публикуется только публичный API (`/api/v1/**`, через `springdoc.paths-to-match`); внутренний
`/internal/**` в спеку не попадает.

- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

См. также: [архитектура](architecture.md) · [контракт Redis](redis-contract.md) · [модель данных](data-model.md) · [конфигурация](configuration.md)
