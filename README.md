# 🛒 E-Commerce Microservices

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen?style=flat-square&logo=springboot)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.0-brightgreen?style=flat-square&logo=spring)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?style=flat-square&logo=postgresql)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-FF6600?style=flat-square&logo=rabbitmq)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

A microservices-based e-commerce backend built with Spring Boot 3.x and Spring Cloud as a portfolio project. All client requests enter through a single API Gateway which handles JWT validation and routes to the appropriate service. Services discover each other via Eureka and communicate either synchronously (Feign) or asynchronously (RabbitMQ).

---

## 🏗️ Architecture Overview

```
Client → API Gateway (8080)
           ↓ JWT validation
           ↓ Eureka lookup
    ┌──────┼──────────┬──────────┐
    ↓      ↓          ↓          ↓
user   product    order     payment
(8081)  (8082)    (8083)    (8084)
                    ↓ RabbitMQ
                 payment → notification
                  (8084)     (8085)
```

---

## 🧩 Services

| Service | Port | Database | Description |
|---|---|---|---|
| `eureka-server` | 8761 | — | Service discovery registry |
| `config-server` | 8888 | — | Centralized configuration |
| `api-gateway` | 8080 | — | Single entry point, JWT filter, routing |
| `user-service` | 8081 | user_db | Auth, registration, profiles |
| `product-service` | 8082 | product_db | Catalog, categories, inventory |
| `order-service` | 8083 | order_db | Cart, orders, lifecycle |
| `payment-service` | 8084 | payment_db | Payment simulation |
| `notification-service` | 8085 | notification_db | Email alerts via RabbitMQ |

---

## ✨ Features

- **API Gateway** — single entry point, JWT validation, Eureka-based routing
- **Service Discovery** — Netflix Eureka, no hardcoded service URLs
- **Centralized Config** — Spring Cloud Config server for all services
- **Sync communication** — OpenFeign (order → product stock check)
- **Async messaging** — RabbitMQ (order → payment → notification)
- **Distributed tracing** — Zipkin + Micrometer across all services
- **JWT Auth** — issued by user-service, validated at gateway, forwarded as headers
- **Fully Dockerized** — one `docker-compose up` starts everything

---

## 🗂️ Project Structure

```
ecommerce-microservices/
├── docker-compose.yml
├── init-db.sql
├── pom.xml                         ← parent POM
├── eureka-server/
├── config-server/
│   └── src/main/resources/
│       └── config/                 ← per-service config files
├── api-gateway/
│   └── filter/AuthFilter.java      ← JWT validation filter
├── user-service/
├── product-service/
├── order-service/
│   └── client/ProductClient.java   ← Feign client
├── payment-service/
└── notification-service/
```

---

## 🔄 Request Flows

### Auth Flow

```
Client → POST /api/users/login
       → API Gateway (public path — no JWT check)
       → user-service validates credentials
       → signs JWT with secret
       → returns token to client
```

### Place Order Flow

```
Client → POST /api/orders  { Bearer token }
       → API Gateway
           → validates JWT
           → extracts userId, role
           → forwards X-User-Id header downstream
       → order-service
           → Feign → product-service (check stock)
           → Feign → product-service (reduce stock)
           → saves order  { status: PENDING }
           → publishes PaymentRequestEvent → RabbitMQ
       ← returns order response immediately

[ Async — after response returned ]
RabbitMQ → payment-service
         → simulates payment (80% success rate)
         → saves payment record
         → publishes PaymentResultEvent

RabbitMQ → order-service
         → updates order status (CONFIRMED / CANCELLED)

RabbitMQ → notification-service
         → logs email alert to user
```

---

## 📡 API Reference

### User Service — `/api/users`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/users/register` | ❌ Public | Register new user, returns JWT |
| POST | `/api/users/login` | ❌ Public | Login, returns JWT |
| GET | `/api/users/profile` | ✅ Bearer | Get current user profile |

**Register / Login request body:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "secret123"
}
```

**Auth response:**
```json
{
  "token": "eyJhbGci...",
  "name": "John Doe",
  "role": "CUSTOMER"
}
```

---

### Product Service — `/api/products`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/products/categories` | ✅ Bearer | Create category |
| GET | `/api/products/categories` | ✅ Bearer | Get all categories |
| POST | `/api/products` | ✅ Bearer | Create product |
| GET | `/api/products` | ✅ Bearer | Get all products (paginated) |
| GET | `/api/products/{id}` | ✅ Bearer | Get product by ID |
| GET | `/api/products/category/{categoryId}` | ✅ Bearer | Get products by category |
| PUT | `/api/products/{id}` | ✅ Bearer | Update product |
| DELETE | `/api/products/{id}` | ✅ Bearer | Delete product |
| PUT | `/api/products/{id}/stock` | ✅ Internal | Reduce stock (called by order-service) |

**Create product request body:**
```json
{
  "name": "iPhone 15",
  "description": "Apple smartphone",
  "price": 999.99,
  "stock": 50,
  "categoryId": 1
}
```

Pagination: `?page=0&size=10`

---

### Order Service — `/api/orders`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/orders` | ✅ Bearer | Place new order |
| GET | `/api/orders/{id}` | ✅ Bearer | Get order by ID |
| GET | `/api/orders/my-orders` | ✅ Bearer | Get all orders for current user |
| PUT | `/api/orders/{id}/status` | ✅ Bearer | Update order status |

**Place order request body:**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

**Order status lifecycle:** `PENDING → CONFIRMED → SHIPPED → DELIVERED → CANCELLED`

Update status: `PUT /api/orders/{id}/status?status=CONFIRMED`

---

### Payment Service

Payment service is **event-driven** — no REST endpoints for placing payments.

| Queue | Direction | Description |
|---|---|---|
| `payment.request` | Consumed | Receives payment request from order-service |
| `payment.result` | Published | Sends success/failure back to order-service |
| `notification.queue` | Published | Sends result to notification-service |

---

### Notification Service

Notification service is **fully event-driven** — no REST endpoints.

| Queue | Direction | Description |
|---|---|---|
| `notification.queue` | Consumed | Receives payment result, logs email alert |

---

## 📨 RabbitMQ Events

**Exchange:** `ecommerce.exchange` (TopicExchange)

| Queue | Publisher | Consumer | Payload |
|---|---|---|---|
| `payment.request` | `order-service` | `payment-service` | `PaymentRequestEvent` |
| `payment.result` | `payment-service` | `order-service` | `PaymentResultEvent` |
| `notification.queue` | `payment-service` | `notification-service` | `PaymentResultEvent` |

**PaymentRequestEvent:**
```json
{ "orderId": 1, "userId": 1, "amount": 999.99 }
```

**PaymentResultEvent:**
```json
{ "orderId": 1, "userId": 1, "success": true, "message": "Payment successful" }
```

---

## 🔐 JWT Auth Pattern

- JWT is **issued** by `user-service` on login/register
- JWT is **validated** by `api-gateway` on every non-public request
- `user-service` and `api-gateway` share the **same JWT secret**
- After validation, gateway forwards identity as headers to downstream services:
  - `X-User-Id` — user's database ID
  - `X-User-Role` — user's role (`CUSTOMER` / `ADMIN`)
- Downstream services read headers directly — **no re-validation needed**

**Public paths (no token required):**
```
POST /api/users/register
POST /api/users/login
```

---

## 🔗 Inter-Service Communication

| From | To | Method | When |
|---|---|---|---|
| `api-gateway` | All services | HTTP routing | Every request |
| `order-service` | `product-service` | Feign (sync) | On order placement |
| `order-service` | `payment-service` | RabbitMQ (async) | After order saved |
| `payment-service` | `order-service` | RabbitMQ (async) | After payment processed |
| `payment-service` | `notification-service` | RabbitMQ (async) | After payment processed |
| All services | `eureka-server` | HTTP | On startup + heartbeat |
| All services | `config-server` | HTTP | On startup |
| All services | `zipkin` | HTTP | Per request (tracing) |

---

## 🐳 Docker

### Start the entire system

```bash
docker-compose up --build
```

### Stop everything

```bash
docker-compose down
```

### Container ports

| Container | Port(s) |
|---|---|
| `postgres` | 5432 |
| `rabbitmq` | 5672, 15672 |
| `zipkin` | 9411 |
| `eureka-server` | 8761 |
| `config-server` | 8888 |
| `api-gateway` | 8080 |
| `user-service` | 8081 |
| `product-service` | 8082 |
| `order-service` | 8083 |
| `payment-service` | 8084 |
| `notification-service` | 8085 |

### Inside Docker — service URLs

Services communicate by **container name**, not `localhost`:

```
Eureka:     http://eureka-server:8761/eureka/
Config:     http://config-server:8888
RabbitMQ:   rabbitmq:5672
Zipkin:     http://zipkin:9411/api/v2/spans
PostgreSQL: jdbc:postgresql://postgres:5432/{db_name}
```

---

## 🛠️ Local Setup (without Docker)

**Prerequisites:** Java 21, Maven 3.x, PostgreSQL 16, RabbitMQ, Redis

```bash
# 1. Clone the repo
git clone <repo-url>
cd ecommerce-microservices

# 2. Start infrastructure (PostgreSQL, RabbitMQ, Zipkin)
# — or use Docker just for infra:
docker-compose up postgres rabbitmq zipkin

# 3. Start services in order:
cd eureka-server && mvn spring-boot:run &
cd config-server && mvn spring-boot:run &
cd api-gateway && mvn spring-boot:run &
cd user-service && mvn spring-boot:run &
cd product-service && mvn spring-boot:run &
cd order-service && mvn spring-boot:run &
cd payment-service && mvn spring-boot:run &
cd notification-service && mvn spring-boot:run &
```

---

## 📊 Monitoring & Observability

| Tool | URL | Credentials |
|---|---|---|
| Eureka Dashboard | http://localhost:8761 | — |
| RabbitMQ Management | http://localhost:15672 | guest / guest |
| Zipkin Tracing | http://localhost:9411 | — |

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Language |
| Spring Boot | 3.4.1 | Service framework |
| Spring Cloud Gateway | 2024.0.0 | API Gateway |
| Netflix Eureka | 2024.0.0 | Service discovery |
| Spring Cloud Config | 2024.0.0 | Centralized config |
| OpenFeign | 2024.0.0 | Sync inter-service HTTP |
| RabbitMQ | 3.x | Async messaging |
| Zipkin / Micrometer | 1.4.x | Distributed tracing |
| PostgreSQL | 16 | Per-service databases |
| Docker + Compose | Latest | Containerization |
| JWT (jjwt) | 0.11.5 | Authentication |
| Lombok | 1.18.36 | Boilerplate reduction |
| Maven | 3.x | Build tool |

---

## 📚 Key Concepts

### Service Discovery — Eureka
Every service registers itself with Eureka on startup (name + IP + port). When service A needs to call service B, it asks Eureka for B's current address instead of hardcoding it — making the system resilient to port/IP changes.

### Centralized Config — Spring Cloud Config
One config server holds `application.yml` files for all services in `src/main/resources/config/`. Each service fetches its config on startup. Update config in one place, restart the service — no redeployment needed.

### API Gateway — Spring Cloud Gateway
A reactive proxy in front of all services. Validates JWT once for all downstream services, routes using Eureka (`lb://service-name`), and forwards `X-User-Id` / `X-User-Role` headers so downstream services don't need to re-validate.

### OpenFeign — Sync Inter-Service Calls
Feign generates HTTP clients from annotated interfaces — call another service's REST API as a local Java method:
```java
@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/api/products/{id}")
    ProductResponse getProductById(@PathVariable Long id);
}
```

### RabbitMQ — Async Messaging
Decouples services using a message broker. A service publishes a message to a queue and moves on — another service consumes it independently. Used here for the order → payment → notification flow, so placing an order responds instantly without waiting for payment processing.

### Distributed Tracing — Zipkin
Assigns a unique `traceId` to every incoming request, propagated via HTTP headers across all services. Search by `traceId` in Zipkin UI to see the full journey and timing of any request across the entire system.

---

## 💡 Potential Improvements

- **Global exception handling** — consistent error response shape across all services
- **Admin role enforcement** — check `X-User-Role` header before allowing product/category creation
- **Order cancellation** — restore stock in product-service when order is cancelled
- **Idempotency** — handle duplicate RabbitMQ messages in payment-service
- **Circuit Breaker** — add Resilience4j for graceful degradation when Feign calls fail
- **Rate limiting** — add at API Gateway level
- **Health checks** — use Spring Actuator `/actuator/health` with Docker Compose `depends_on: condition: service_healthy`
- **Real email** — replace notification logging with JavaMailSender

---

## 📄 License

MIT
