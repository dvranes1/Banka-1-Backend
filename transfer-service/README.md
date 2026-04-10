# Transfer Service

Service for transferring funds between accounts of the same client (internal and cross-currency transfers).

## Docker Compose

Start the service with its dependencies (PostgreSQL, RabbitMQ):

```bash
docker compose -f docker-compose.ci.yml up -d
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8085` | HTTP port |
| `JWT_SECRET` | — | JWT signing secret |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `transferdb` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ user |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `NOTIFICATION_QUEUE` | `notification-service-queue` | Notification queue name |
| `NOTIFICATION_EXCHANGE` | `transfer.events` | RabbitMQ exchange |
| `NOTIFICATION_ROUTING_KEY` | `transfer.#` | RabbitMQ routing key |
| `SKIP_VERIFICATION` | `true` | Skip 2FA verification |
| `CLIENT_SERVICE_HOST` | `localhost` | Client service host |
| `CLIENT_SERVER_PORT` | `8083` | Client service port |
| `ACCOUNT_SERVICE_HOST` | `localhost` | Account service host |
| `ACCOUNT_SERVER_PORT` | `8084` | Account service port |
| `EXCHANGE_SERVICE_HOST` | `localhost` | Exchange service host |
| `EXCHANGE_SERVER_PORT` | `8085` | Exchange service port |
| `VERIFICATION_SERVICE_HOST` | `localhost` | Verification service host |
| `VERIFICATION_SERVER_PORT` | `8086` | Verification service port |

## API Endpoints

Base URL: `http://localhost/api/transfers`

| Method | Path | Description |
|---|---|---|
| `POST` | `/` | Execute a new transfer between two accounts of the same owner |
| `GET` | `/` | Get paginated transfer history for a client |

### Execute Transfer

```http
POST /api/transfers/
Authorization: Bearer <token>
Content-Type: application/json

{
  "fromAccountNumber": "123456789",
  "toAccountNumber": "987654321",
  "amount": 1000.00,
  "memo": "Rent"
}
```

### Get Transfer History

```http
GET /api/transfers/?clientId=1&page=0&size=10
Authorization: Bearer <token>
```

Full API spec available at `docs/openapi.yml` or via Swagger UI at `http://localhost:8085/swagger-ui.html`.