## Code Review Instructions

### Project Context
- Spring Boot 4.0.3 with Java 21
- Jackson 3 (not Jackson 2) — use `JacksonJsonSerializer`/`JacksonJsonDeserializer`, not deprecated `JsonSerializer`/`JsonDeserializer`
- Spring Boot 4.x moved Kafka autoconfigure to `org.springframework.boot.kafka.autoconfigure`
- MySQL with Flyway migrations, JPA entities extend a shared `BaseModel`
- Kafka for async event-driven communication (fire-and-forget pattern is intentional)
- OAuth2 resource server with JWT from userservice
- Single currency platform (INR)
- Lombok for boilerplate reduction

### Review Focus
- Spring Boot 4.x / Jackson 3 deprecations and breaking changes
- JPA entity mapping correctness (especially bidirectional relationships)
- Kafka consumer/producer configuration correctness
- SQL migration / JPA entity schema mismatches
- Idempotency and concurrency safety
- Security vulnerabilities (OWASP top 10)

### Do NOT flag
- Missing javadocs/comments — intentionally minimal
- Fire-and-forget Kafka patterns with try/catch — this is the project convention
- Hardcoded "INR" currency — single currency platform by design
- Missing DLQ/retry infrastructure — out of scope
- `BaseModel` equals/hashCode pattern — shared convention across services
