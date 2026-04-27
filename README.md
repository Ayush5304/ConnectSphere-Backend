# ConnectSphere Backend

A **Microservices-based Social Media Platform** backend built with **Spring Boot 3.2** and **Java 21**.

## 🏗️ Architecture

```
                    ┌─────────────────┐
                    │   API Gateway   │ :8080
                    │  (JWT Validation)│
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
         ┌────▼────┐    ┌───▼────┐    ┌───▼────┐
         │ Auth    │    │ Post   │    │ Like   │
         │ Service │    │ Service│    │ Service│
         │  :8081  │    │  :8082 │    │  :8084 │
         └─────────┘    └────────┘    └────────┘
              │              │              │
         ┌────▼─────────────▼──────────────▼────┐
         │        Eureka Server :8761            │
         │         (Service Registry)            │
         └───────────────────────────────────────┘
```

## 🎯 Microservices

| Service | Port | Database | Responsibility |
|---|---|---|---|
| **eureka-server** | 8761 | - | Service registry & discovery |
| **api-gateway** | 8080 | - | Single entry point, JWT validation, routing |
| **auth-service** | 8081 | authdb | User auth, JWT, OAuth2, profiles |
| **post-service** | 8082 | postdb | Posts, feed, Redis caching |
| **comment-service** | 8083 | commentdb | Comments & nested replies |
| **like-service** | 8084 | likedb | 6 reaction types on posts/comments |
| **follow-service** | 8085 | followdb | Follow/unfollow, suggestions |
| **notification-service** | 8086 | notificationdb | RabbitMQ-based notifications |
| **media-service** | 8087 | mediadb | File uploads, 24hr stories |
| **search-service** | 8088 | searchdb | Search users/posts, trending hashtags |
| **payment-service** | 8089 | paymentdb | Razorpay integration |

## 🛠️ Tech Stack

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Database | MySQL 8.0 (per service) |
| Cache | Redis 7 |
| Message Broker | RabbitMQ 3 |
| Authentication | JWT (HS256) + OAuth2 |
| Payment | Razorpay |
| Build Tool | Maven |
| Containerization | Docker |

## 📦 Key Dependencies

- `spring-boot-starter-web` — REST APIs
- `spring-boot-starter-data-jpa` — Database ORM
- `spring-boot-starter-security` — Security & JWT
- `spring-cloud-starter-netflix-eureka-client` — Service discovery
- `spring-cloud-starter-gateway` — API Gateway
- `spring-boot-starter-amqp` — RabbitMQ
- `spring-boot-starter-data-redis` — Redis caching
- `spring-boot-starter-mail` — Email notifications
- `razorpay-java` — Payment integration
- `jjwt` — JWT token generation/validation
- `springdoc-openapi` — Swagger API docs

## 🚀 Quick Start

### Prerequisites
- Java 21
- Maven 3.8+
- Docker Desktop
- MySQL 8.0
- RabbitMQ 3
- Redis 7

### 1. Start Infrastructure (Docker)

```bash
docker compose -f docker-compose-infra.yml up -d
```

This starts MySQL, RabbitMQ, and Redis.

### 2. Build All Services

```bash
# Windows
build-all.bat

# Linux/Mac
mvn clean package -DskipTests
```

### 3. Start All Services

```bash
# Windows
start-all.bat

# Linux/Mac - start each service manually
cd eureka-server && java -jar target/*.jar &
cd api-gateway && java -jar target/*.jar &
cd auth-service && java -jar target/*.jar &
# ... repeat for all services
```

### 4. Verify Services

- Eureka Dashboard: http://localhost:8761
- API Gateway Health: http://localhost:8080/actuator/health
- Swagger (Auth): http://localhost:8081/swagger-ui/index.html

## 🌿 Branch Strategy

| Branch | Purpose |
|---|---|
| `main` | Production-ready stable code |
| `dev` | Development integration branch |
| `feature/auth` | Authentication & user management |
| `feature/posts` | Post creation, feed, caching |
| `feature/social` | Likes, comments, follows |
| `feature/notifications` | RabbitMQ notifications |
| `feature/media` | File uploads & stories |
| `feature/payment` | Razorpay integration |

## 🔐 Security

- **JWT Authentication** — HS256 signed tokens, 24hr expiry
- **BCrypt Password Hashing** — One-way hashing
- **API Gateway** — Centralized JWT validation
- **CORS** — Configured for frontend origin
- **OAuth2** — Google login integration
- **HMAC-SHA256** — Razorpay signature verification

## 📊 Database Schema

Each service has its own database (Database per Service pattern):

- **authdb** — users table
- **postdb** — posts table
- **commentdb** — comments table
- **likedb** — likes table
- **followdb** — follows table
- **notificationdb** — notifications table
- **mediadb** — stories, story_views tables
- **searchdb** — hashtags table
- **paymentdb** — payments table

## 🔄 Inter-Service Communication

**Synchronous (REST):**
- post-service → auth-service (resolve @mentions)
- payment-service → auth-service (grant verified badge)
- All services → eureka-server (service discovery)

**Asynchronous (RabbitMQ):**
- like-service → notification-service (like events)
- comment-service → notification-service (comment events)
- post-service → notification-service (mention events)

## 📝 API Documentation

Each service exposes Swagger UI at `/swagger-ui/index.html`

Example: http://localhost:8081/swagger-ui/index.html

## 🐳 Docker Deployment

```bash
# Build all service images
docker compose build

# Start everything
docker compose up -d
```

## 🔧 Configuration

Each service has `application.yml` with:
- Database connection
- Eureka client config
- Service-specific properties
- Actuator endpoints

**Important:** Never commit real credentials. Use environment variables in production.

## 📈 Monitoring

- **Spring Boot Actuator** — Health checks, metrics
- **Spring Boot Admin** — Centralized monitoring dashboard (port 9090)
- **Eureka Dashboard** — Service registry status

## 🧪 Testing

```bash
# Run tests for all services
mvn test

# Run tests for specific service
cd auth-service && mvn test
```

## 🚀 Deployment

Recommended platforms:
- **Railway** — Microservices deployment
- **AWS ECS** — Container orchestration
- **Oracle Cloud** — Free tier (4 CPU + 24GB RAM)

## 📄 License

MIT License

## 👥 Author

Ayush Mishra

## 🔗 Frontend Repository

[ConnectSphere-Frontend](https://github.com/Ayush5304/ConnectSphere-Frontend)
