# Event-Driven Order Processing Platform

Модульная платформа обработки заказов на базе Java 17+, Spring Boot 3, Spring Security 6, Kafka, PostgreSQL, MongoDB, Redis, Docker и Kubernetes.

Фронтенд/UI не входит в комплектацию. Вся функциональность доступна через REST/gRPC API.

## Содержание
- [Введение](#введение)
- [Цели проекта](#цели-проекта)
- [Область применения](#область-применения)
- [Общая архитектура](#общая-архитектура)
- [Функциональные требования](#функциональные-требования)
- [Интеграции Kafka и события](#интеграции-kafka-и-события)
- [Хранение данных](#хранение-данных)


## 🚀 Введение
Образовательный проект для Java Middle разработчиков. Практика с современным стеком корпоративных технологий: Java 17+ (Stream API), Spring Boot 3, Spring Security 6, Apache Kafka, PostgreSQL/MongoDB, Redis, Docker/Kubernetes. Взаимодействие через событийную шину Kafka и REST/gRPC API.

## 🎯 Цели проекта
- Проектирование распределённых систем на Spring‑стеке
- Реактивное и событийное программирование (Stream API, Kafka)
- JWT и OAuth 2.1 через Spring Security
- Схемы данных для PostgreSQL и MongoDB
- Redis для кэширования и блокировок
- CI/CD, Docker, Kubernetes/Helm

## 🛒 Область применения
Каталог товаров, управление пользователями и ролями, оформление и жизненный цикл заказов, управление запасами, уведомления о статусе заказа.

## 🏗️ Общая архитектура
| Сервис | Ответственность | БД | Протоколы | Зависимости |
|---|---|---|---|---|
| api-gateway | Единая точка входа, rate-limiting | — | REST, gRPC | auth-service |
| auth-service | JWT/OAuth 2.1, токены | PostgreSQL | REST | kafka-broker |
| user-service | CRUD пользователей, RBAC | PostgreSQL | REST, Kafka | kafka-broker |
| product-service | Каталог товаров | MongoDB | REST, Kafka | kafka-broker |
| inventory-service | Запасы и резервы | PostgreSQL | gRPC, Kafka | product-service |
| order-service | Жизненный цикл заказа | PostgreSQL | REST, gRPC, Kafka | inventory-service |
| notification-service | Отправка уведомлений | MongoDB | Kafka | — |

Сервисы контейнеризированы (Docker), оркестрация — Helm-чарты. Секреты — Kubernetes Secrets (sealed-secrets).

## 📋 Функциональные требования

###  Пользователи
- Регистрация с подтверждением e‑mail
- OAuth 2.1 (Google, GitHub)
- Роли: ROLE_USER, ROLE_MANAGER, ROLE_ADMIN

###  Товары
- CRUD для каталога (администраторы)
- Публикация/скрытие товаров
- Поиск и фильтрация (цена, категория, текст)

###  Заказы
- Оформление (ROLE_USER)
- Проверка наличия через Kafka request/response или gRPC
- Статусы: NEW → RESERVED → PAID → SHIPPED → COMPLETED / CANCELLED
- Сага для компенсации при недостатке товара

###  Уведомления
- Email о статусе заказа
- Rate‑limiting через Redis (leaky bucket)

## 📢 Интеграции Kafka и события
| Топик | Ключ | Схема | Продюсер | Подписчики |
|---|---|---|---|---|
| user.created | userId | Avro | auth‑service | user‑service |
| order.created | orderId | Avro | order‑service | inventory, notification |
| inventory.reserved | orderId | Avro | inventory‑service | order‑service |
| order.status‑changed | orderId | Avro | order‑service | notification‑service |

Схемы — Confluent Schema Registry.

## 🗄️ Хранение данных
- **PostgreSQL 15** — транзакционные данные (пользователи, заказы, запасы)
- **MongoDB 7** — документы (каталог, шаблоны уведомлений, логи)
- **Redis 7** — кэш, распределённые блокировки (Redlock)
