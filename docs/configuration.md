# Конфигурация

Все настройки — в `application.yml`: собственный префикс `subscription.*` плюс стандартные `spring.*` и
`management.*`. Значения, привязанные к окружению, переопределяются переменными окружения. В таблицах
указаны свойство, env-override (где есть) и значение по умолчанию — строго из `application.yml` и
`SubscriptionProperties`.

## PostgreSQL

| Свойство | Env | По умолчанию |
|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/subscriptions` |
| `spring.datasource.username` | `DB_USER` | `subscriptions` |
| `spring.datasource.password` | `DB_PASSWORD` | `subscriptions` |
| `spring.jpa.hibernate.ddl-auto` | — | `validate` (схему создаёт Liquibase, не Hibernate) |
| `spring.jpa.open-in-view` | — | `false` |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | — | `UTC` |
| `spring.liquibase.change-log` | — | `classpath:db/changelog/db.changelog-master.yaml` |

## Redis

| Свойство | Env | По умолчанию |
|---|---|---|
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` |
| `spring.data.redis.timeout` | — | `2s` |

## Контракт Redis (`subscription.redis.*`)

| Свойство | По умолчанию | Примечания |
|---|---|---|
| `subscription.redis.channel` | `subscriptions:changes` | Pub/Sub-канал сигнала `CONFIG_CHANGED` |
| `subscription.redis.runtime-set-key` | `subs:runtime` | Множество id runtime-подписок (`ACTIVE`/`PAUSED`) |
| `subscription.redis.config-key-prefix` | `sub:` | Префикс ключей `sub:{id}` |

Подробнее о значениях этих ключей — в [redis-contract.md](redis-contract.md).

## Имя топика (`subscription.topic.*`)

| Свойство | По умолчанию | Примечания |
|---|---|---|
| `subscription.topic.prefix` | `subscription` | Имя топика собирается как `{prefix}.{subscriberName}.{topicPostfix}` |

## Зависимости-сервисы

| Свойство | Env | По умолчанию | Примечания |
|---|---|---|---|
| `subscription.initialization.base-url` | `INIT_SERVICE_URL` | `http://initialization-service` | База Initialization Service (`POST /internal/initialization`) |
| `subscription.metamodel.base-url` | `DATA_DICTIONARY_URL` | `http://data-dictionary:8080` | База DataDictionary |
| `subscription.metamodel.metadata-path` | — | `/api/search-service/metadata/v3` | Эндпоинт метамодели (классы, поля, иерархия, связи) |

Метамодель загружается один раз на старте (fail-fast): при недоступности DataDictionary приложение не
поднимается.

## Лимиты / квоты (`subscription.rate-limits.*`)

Все лимиты вынесены во внешнюю конфигурацию (ConfigMap / env), чтобы менять их без пересборки. Превышение
любого из них → `429 QUOTA_EXCEEDED`.

| Свойство | По умолчанию | Смысл |
|---|---|---|
| `subscription.rate-limits.max-subscriptions-per-subscriber` | `100` | Максимум не-`DELETED` подписок на подписчика |
| `subscription.rate-limits.max-topics-per-subscriber` | `20` | Максимум различных `topicPostfix` (топиков) на подписчика |
| `subscription.rate-limits.max-subscription-creations-per-hour` | `60` | Создаваемых подписок в час (счётчик в Redis, TTL 1 час) |
| `subscription.rate-limits.max-initializations-per-hour` | `20` | Запусков initialization в час (счётчик в Redis, TTL 1 час) |
| `subscription.rate-limits.max-fields` | `100` | Максимум элементов в `fields` |
| `subscription.rate-limits.max-filter-length` | `4096` | Максимальная длина строки `filter` |

Почасовые счётчики хранятся в Redis под ключами `quota:{subscriberName}:{action}:{hourBucket}` (`action`
= `sub-create` / `init`), инкрементируются атомарно и получают TTL при первом инкременте окна.

## Наблюдаемость и health (`management.*`)

| Свойство | По умолчанию | Примечания |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` | |
| `management.endpoint.health.probes.enabled` | `true` | Включает liveness/readiness-пробы |
| readiness-группа | `readinessState, db, redis` | Под ready только когда доступны PostgreSQL и Redis |
| liveness-группа | `livenessState` | |
| `management.endpoint.health.show-details` | `always` | |
| `management.health.redis.enabled` / `management.health.db.enabled` | `true` | Health-индикаторы Redis и БД |

## OpenAPI (`springdoc.*`)

| Свойство | По умолчанию | Примечания |
|---|---|---|
| `springdoc.paths-to-match` | `/api/v1/**` | Документируется только публичный API; `/internal/**` исключён |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | |
| `springdoc.swagger-ui.operations-sorter` | `method` | |
| `springdoc.swagger-ui.tags-sorter` | `alpha` | |

## Логирование

| Свойство | По умолчанию | Примечания |
|---|---|---|
| `logging.level.com.example.subscription` | `INFO` | Поднимите до `DEBUG`, чтобы трейсить операции write-path и валидацию |

## Минимальный пример env для деплоя

```bash
DB_URL=jdbc:postgresql://postgres:5432/subscriptions
DB_USER=subscriptions
DB_PASSWORD=***
REDIS_HOST=redis
REDIS_PORT=6379
DATA_DICTIONARY_URL=http://data-dictionary:8080
INIT_SERVICE_URL=http://initialization-service
```

См. также: [эксплуатация](operations.md) · [архитектура](architecture.md) · [контракт Redis](redis-contract.md)
