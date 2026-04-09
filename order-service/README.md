# Order Service

Mikroservis koji pokriva upravljanje aktuarima, berzanskim nalozima, portfoliom klijenata i praćenjem poreza.

For shared project setup, git hooks, and infrastructure details, see the [root README](../README.md).

## Overview

The Order Service is a Spring Boot microservice responsible for managing brokerage orders, investment portfolios, tax calculations, and actuary (agent/supervisor) account limits and permissions. It integrates with multiple services including account-service for cash settlement, stock-service for market data, and exchange-service for currency conversion.

## Current Scope

This module implements comprehensive order management functionality including:

**Implemented:**

- Spring Boot 4.0.3 application with JWT authentication
- Liquibase database migration support (Postgres)
- Complete Order entity with support for MARKET, LIMIT, STOP, and STOP_LIMIT order types
- Buy and sell order creation with validation and approval workflows
- Order execution engine with partial fill support
- Portfolio tracking with position management and OTC trading support
- Tax calculation and collection (15% capital gains tax)
- Actuary information management (daily limits, approval requirements)
- RabbitMQ producer for order and tax notifications
- RestClient adapters for all dependent services:
  - account-service (cash settlement, balance verification)
  - employee-service (employee data, role verification)
  - client-service (customer information)
  - exchange-service (currency conversion)
  - stock-service (market data, listing information)
- Scheduled tasks for tax collection, pending order expiration, and actuary updates
- Actuator health endpoints
- Comprehensive exception handling with custom exceptions
- Security configuration with role-based access control

## Architecture

### Layered Architecture

The service follows a standard Spring Boot layered architecture:

- **Controllers** (`controller/`): REST API endpoints for orders, portfolio, tax, and actuary management
- **Services** (`service/`): Business logic interfaces and implementations
- **Repositories** (`repository/`): Data access layer using Spring Data JPA
- **Entities** (`entity/`): Domain models (Order, Portfolio, Transaction, ActuaryInfo, TaxCharge)
- **DTOs** (`dto/`): Data Transfer Objects for request/response serialization
- **Clients** (`client/`): Microservice integration adapters
- **Configuration** (`config/`): Security, REST client, RabbitMQ, scheduler configuration
- **Exceptions** (`exception/`): Custom exception types for error handling
- **Schedulers** (`scheduler/`): Scheduled background tasks

### Core Components

#### Order Management
- **Order**: Represents a single brokerage order (BUY/SELL, MARKET/LIMIT/STOP/STOP_LIMIT)
- **OrderCreationService**: Handles order creation, validation, and approval workflow
- **OrderExecutionService**: Executes pending orders when market conditions are met
- **OrderController**: REST API for order operations

#### Portfolio Management
- **Portfolio**: Represents a user's position in a single security
- **PortfolioService**: Manages portfolio positions, public OTC listings, and option exercise
- **PortfolioController**: REST API for portfolio operations

#### Tax Management
- **TaxCharge**: Tracks capital gains tax charged on portfolio sales
- **TaxService**: Calculates and collects monthly tax (15% rate)
- **TaxScheduler**: Automated monthly tax collection
- **TaxController**: REST API for tax tracking and debt inquiry

#### Actuary Management
- **ActuaryInfo**: Stores daily trading limits and approval requirements for agents/supervisors
- **ActuaryService**: Manages actuary limits and permissions
- **ActuaryScheduler**: Resets daily limits and refreshes data from employee-service
- **ActuaryController**: REST API for actuary management

### Service Integration

The Order Service integrates with several dependent microservices via REST clients:

| Service | Purpose | Key Operations |
|---------|---------|-----------------|
| account-service | Cash settlement and balance management | Verify balance, deduct fees, settle transactions |
| employee-service | Employee and role data | Fetch employee details, verify supervisors |
| client-service | Customer information | Retrieve client details for order restrictions |
| exchange-service | Currency conversion | Convert foreign currency to RSD |
| stock-service | Market data and listings | Fetch security details, current prices, exchange status |

## Docker Compose

To run the service locally with its own database and RabbitMQ:

```bash
cd order-service
docker compose up --build
```

To run the full system including all services:

```bash
docker compose -f setup/docker-compose.yml up -d
```

## API Endpoints

### Order Management (`/orders`)

| Endpoint | Method | Role | Description |
|----------|--------|------|-------------|
| `/orders` | GET | SUPERVISOR | Get paginated overview of all orders with optional status filter |
| `/orders/buy` | POST | CLIENT_TRADING, AGENT, SUPERVISOR | Create a new buy order |
| `/orders/sell` | POST | CLIENT_TRADING, AGENT, SUPERVISOR | Create a new sell order |
| `/orders/{id}/confirm` | POST | CLIENT_TRADING, AGENT, SUPERVISOR | Confirm a draft order and finalize validation |
| `/orders/{id}/cancel` | POST | CLIENT_TRADING, AGENT, SUPERVISOR | Cancel an order (user's own order) |
| `/orders/{id}/cancel` | PUT | SUPERVISOR | Partially or fully cancel an order (supervisor action) |
| `/orders/{id}/approve` | PUT | SUPERVISOR | Approve a pending order requiring supervisor approval |
| `/orders/{id}/decline` | PUT | SUPERVISOR | Decline a pending order |

### Portfolio Management (`/portfolio`)

| Endpoint | Method | Role | Description |
|----------|--------|------|-------------|
| `/portfolio` | GET | CLIENT_BASIC, CLIENT_TRADING, AGENT, SUPERVISOR | Get full portfolio with enriched market data and metrics |
| `/portfolio/{id}/set-public` | PUT | CLIENT_BASIC, CLIENT_TRADING, AGENT, SUPERVISOR | Set public quantity for OTC trading (STOCK only) |
| `/portfolio/{id}/exercise-option` | POST | AGENT, SUPERVISOR | Exercise an option contract |

### Tax Management (`/tax`)

| Endpoint | Method | Role | Description |
|----------|--------|------|-------------|
| `/tax/debts` | GET | SUPERVISOR | Get all user tax debts with pagination |
| `/tax/debts/{userId}` | GET | SUPERVISOR | Get tax debt for a specific user |
| `/tax/tracking` | GET | SUPERVISOR | Get tax tracking rows with filters (userType, firstName, lastName) |
| `/tax/collect-manually` | POST | SUPERVISOR | Trigger monthly tax collection manually |

### Actuary Management (`/actuaries`)

| Endpoint | Method | Role | Description |
|----------|--------|------|-------------|
| `/actuaries` | GET | SUPERVISOR | Get all actuaries with their limits and approval requirements |
| `/actuaries/{id}/limit` | PUT | SUPERVISOR | Update an actuary's daily trading limit |

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `ORDER_SERVER_PORT` | Port the service listens on | `8088` |
| `ORDER_DB_HOST` | PostgreSQL host | `localhost` |
| `ORDER_DB_PORT` | PostgreSQL port | `5432` |
| `ORDER_DB_NAME` | Database name | `orderdb` |
| `ORDER_DB_USER` | Database user | `postgres` |
| `ORDER_DB_PASSWORD` | Database password | `postgres` |
| `JWT_SECRET` | Shared HMAC secret for JWT signing and verification | — |
| `RABBITMQ_HOST` | RabbitMQ host | `localhost` |
| `RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `RABBITMQ_USERNAME` | RabbitMQ user | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `guest` |
| `NOTIFICATION_EXCHANGE` | RabbitMQ exchange name | `employee.events` |
| `NOTIFICATION_QUEUE` | RabbitMQ queue name | `notification-service-queue` |
| `ACCOUNT_SERVICE_HOST` | account-service hostname | `localhost` |
| `ACCOUNT_SERVER_PORT` | account-service port | `8084` |
| `EMPLOYEE_SERVICE_HOST` | employee-service hostname | `localhost` |
| `USER_SERVER_PORT` | employee-service port | `8081` |
| `CLIENT_SERVICE_HOST` | client-service hostname | `localhost` |
| `CLIENT_SERVER_PORT` | client-service port | `8083` |
| `EXCHANGE_SERVICE_HOST` | exchange-service hostname | `localhost` |
| `EXCHANGE_SERVER_PORT` | exchange-service port | `8085` |
| `STOCK_SERVICE_HOST` | stock-service hostname | `localhost` |
| `STOCK_SERVICE_PORT` | stock-service port | `8090` |

## API Gateway

The service is reachable via the API Gateway at:

```
http://localhost/order/
```

## Health Check

```
GET /actuator/health/liveness
```

```json
{ "status": "UP" }
```

## Events

The service publishes notifications to the `employee.events` RabbitMQ exchange using the following routing keys:

| Routing Key | Trigger | Payload |
|---|---|---|
| `order.approved` | A brokerage order has been approved and executed | OrderNotificationPayload |
| `order.declined` | A brokerage order has been declined | OrderNotificationPayload |
| `tax.collected` | Tax has been collected from a portfolio transaction | TaxCollectedPayload |

## Data Models

### Order Entity
- Represents a single brokerage order placed by a client or agent
- Supports four order types: MARKET, LIMIT, STOP, STOP_LIMIT
- Directions: BUY or SELL
- Statuses: DRAFT, PENDING_APPROVAL, PENDING_EXECUTION, PARTIALLY_FILLED, EXECUTED, CANCELLED, DECLINED
- Tracks remaining portions for partial fills and execution state

### Portfolio Entity
- Represents a position in a single security (STOCK, FUTURES, FOREX, OPTION)
- Includes quantity held, reserved quantity (for pending sales), and average purchase price
- Supports public OTC exposure for STOCK listings
- Automatically tracks last modification timestamp

### ActuaryInfo Entity
- Stores trading limits and approval requirements for employees who are actuaries
- Daily limit in RSD (null for supervisors)
- Used limit tracks consumption within the day
- Reserved limit tracks pending orders awaiting execution
- needApproval flag determines if supervisor approval is required for all trades

### TaxCharge Entity
- Tracks capital gains tax collected from each portfolio sale transaction
- Status: PENDING, CHARGED, PAID
- Stored with exact tax amount and timestamp

### Transaction Entity
- Internal record of executed trades
- Links to orders and portfolio positions
- Stores quantity executed, price per unit, and timestamp

## Order Processing Workflow

### Create Order
1. User submits CreateBuyOrderRequest or CreateSellOrderRequest
2. Validation: User exists, listing exists, quantity > 0, sufficient permissions
3. For buy orders: Verify account has sufficient funds (or margin available)
4. For sell orders: Verify portfolio has sufficient quantity (accounting for reservations)
5. Create Order entity with DRAFT status
6. Check if approval is required based on:
   - Agent's needApproval flag
   - Daily limit usage
   - Order amount (converted to RSD)
7. If approval needed: status → PENDING_APPROVAL; if not: status → PENDING_EXECUTION
8. Return OrderResponse

### Confirm Order
1. Retrieve draft order
2. Re-validate all conditions (balance, portfolio quantity, market conditions)
3. Reserve funds/quantity as needed
4. Trigger order execution if conditions are met
5. Return updated OrderResponse

### Order Execution
- Triggered when:
  - MARKET orders are confirmed (execute immediately)
  - LIMIT orders: market price reaches limit
  - STOP orders: market price falls below stop value
  - STOP_LIMIT orders: market price reaches stop, then activates limit
- Marks order as EXECUTED or PARTIALLY_FILLED depending on fill amount
- Updates portfolio positions
- Creates transaction records
- Calculates and deducts fees

### Cancel Order
- Can be cancelled by owner (any status except DONE)
- Supervisors can partially cancel remaining portions
- Releases reserved funds/quantity
- Returns reserved amounts to account

### Approve/Decline Order (Supervisor)
- Supervisor reviews pending approval orders
- Approve: status → PENDING_EXECUTION, proceeds to execution
- Decline: status → DECLINED, releases reservations, sends notification

## Tax Calculation

- Capital gains tax rate: 15% of profit from sale transactions
- Monthly calculation: processes all SELL trades from previous month
- Conversion: profit in foreign currency converted to RSD via exchange-service
- Settlement: tax amount transferred to state account via account-service
- Tracking: TaxCharge records mark trades as processed to prevent duplication

## Security

- JWT-based authentication with role-based access control
- Supported roles: CLIENT_BASIC, CLIENT_TRADING, AGENT, SUPERVISOR
- Annotations: @PreAuthorize on endpoints restrict access
- Service JWT auth interceptor for inter-service communication
- Encrypted password storage via BouncyCastle

## Testing

Run unit and integration tests:

```bash
./gradlew test
```

Generate test coverage report:

```bash
./gradlew jacocoTestReport
```

The test coverage report is generated in `build/reports/jacoco/test/html/`.

## Dependencies

- **Spring Boot 4.0.3**: Web framework and dependency injection
- **Spring Data JPA**: ORM and database access
- **Spring Security**: JWT authentication and authorization
- **Spring AMQP**: RabbitMQ integration
- **Liquibase**: Database migrations
- **Lombok**: Reduce boilerplate code
- **Jackson**: JSON serialization
- **SpringDoc OpenAPI**: API documentation (OpenAPI 3.0)
- **PostgreSQL Driver**: Production database
- **H2**: In-memory database for testing
- **JUnit 5**: Test framework

## Building and Deployment

### Build the service

```bash
./gradlew clean build
```

### Build Docker image

```bash
docker build -t order-service:latest .
```

### Run with Docker Compose

```bash
docker compose up --build
```

## Troubleshooting

### Order not executing
- Check that exchange is open (exchangeClosed flag)
- Verify account has sufficient balance/margin
- Check portfolio has sufficient quantity for sells
- Review market conditions against order type (LIMIT, STOP, etc.)

### Tax not collecting
- Verify exchange-service is accessible for currency conversion
- Check account-service is accessible for tax settlement
- Ensure previous month's transactions exist
- Review TaxCharge status to avoid duplicate processing

### Connection issues
- Verify all dependent services are running
- Check environment variables for host/port configuration
- Review RabbitMQ connection settings
- Verify database connectivity (PostgreSQL)
