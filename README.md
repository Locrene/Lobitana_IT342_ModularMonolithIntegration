# Modular Monolith Integration — Order & Inventory

A single Spring Boot application with two in-process modules — **Order** and **Inventory** — sharing a Supabase (Postgres) database, connected to a React frontend over REST.

## Architecture

```
edu.cit.lobitana/
  ModularmonolithApplication.java   (parent package — scans both modules)
  inventory/
    Inventory.java                  (entity)
    InventoryRepository.java        (JPA repository)
    InventoryService.java           (public interface — the only thing shop can depend on)
    InventoryServiceImpl.java       (package-private implementation)
  shop/
    Order.java                      (entity)
    OrderRepository.java            (JPA repository)
    OrderService.java               (depends only on InventoryService, via constructor injection)
    OrderController.java            (REST endpoint)
  config/
    CorsConfig.java
```

`shop` and `inventory` integrate **in-process** — no HTTP calls between them. `OrderService` is constructor-injected with the `InventoryService` interface only; it can never see or instantiate `InventoryServiceImpl` directly, since that class is package-private to `inventory`.

## Tech Stack

- Backend: Spring Boot (Java 17, Maven)
- Frontend: React + Vite
- Database: Supabase (Postgres)

## Supabase Setup

1. Create a free account and project at [supabase.com](https://supabase.com).
2. In **Project Settings → Database**, note your connection details, or use **Connect → Session pooler** (recommended over Direct connection, since Direct uses IPv6 by default and most networks are IPv4-only).
3. Open the **SQL Editor**, paste the contents of [`sql/schema.sql`](./sql/schema.sql), and run it (choose **"Run without RLS"** when prompted — this app connects directly via a privileged database credential, not Supabase's public client API, so Row Level Security isn't applicable here).
4. Confirm in **Table Editor** that `inventory` has 3 seeded rows and `orders` is empty.

## Backend Setup

1. Set your Supabase database password as an environment variable named `SUPABASE_DB_PASSWORD` (in IntelliJ: Run/Debug Configurations → Environment variables). **Never commit this value to Git.**
2. Update `src/main/resources/application.properties` with your own Supabase host/username if different from the example below:
   ```properties
   spring.datasource.url=jdbc:postgresql://<your-pooler-host>:5432/postgres
   spring.datasource.username=<your-pooler-username>
   spring.datasource.password=${SUPABASE_DB_PASSWORD}
   ```
3. Run the app:
   ```
   cd backend
   mvn spring-boot:run
   ```
4. API available at `http://localhost:8080`.

## Frontend Setup

```
cd frontend
npm install
npm run dev
```

Opens at `http://localhost:5173`. The backend's CORS config allows requests from this origin.

## API

| Method | Endpoint      | Request Body                          | Response                                                    |
|--------|---------------|----------------------------------------|---------------------------------------------------------------|
| POST   | `/api/orders` | `{ "productId": "P100", "quantity": 1 }` | `{ "status": "CONFIRMED"/"REJECTED", "reason": "...", "inventory": {...} }` |

## Network Tab Evidence

**Confirmed order (P100, quantity 1):**

*(insert screenshot here)*

**Rejected order — insufficient stock (P300, quantity 1):**

*(insert screenshot here)*

## Reflection

### 1. In-process vs. separate microservices over a network

Integrating Order and Inventory in-process, as two packages inside one Spring Boot application, means calling `InventoryService.reserve(...)` is just a regular Java method call — synchronous, sharing the same JVM memory space, and wrapped in the same database transaction as the rest of the order-placement logic. We get several things "for free" this way: atomicity (if reserving stock succeeds but saving the order somehow failed, both could be rolled back together in a single transaction, since they share one connection to one database), zero network latency, no serialization/deserialization overhead, and no need to handle partial failures like timeouts, retries, or an inventory service being temporarily unreachable. Deployment is also simpler — one JAR, one process, one set of logs to check.

If Inventory were split into its own microservice reachable over HTTP, we would lose all of that for free and have to explicitly add it back: a network client (REST or gRPC) inside `shop` to call Inventory, timeout and retry policies for when the network is slow or the service is down, a strategy for partial failure (e.g., what happens if the reservation succeeds on Inventory's side but the network call to confirm that back to Order fails?), authentication/authorization between the two services, and likely some form of distributed transaction management or an eventual-consistency pattern (like the Saga pattern) since a single ACID transaction can no longer span two separate databases. We'd also need service discovery, independent versioning of each service's API contract, and separate monitoring/logging per service.

### 2. Why package-private `InventoryServiceImpl` matters

Making `InventoryServiceImpl` package-private (no `public` modifier) is what actually enforces the module boundary at compile time, not just by convention or documentation. Because it's package-private, no class outside `edu.cit.lobitana.inventory` — including everything in `edu.cit.lobitana.shop` — can write `new InventoryServiceImpl(...)` or even reference the type name at all; the code simply won't compile if `OrderService` tries to import it directly. This forces every consumer to depend only on the public `InventoryService` interface, injected by Spring via constructor injection.

If `InventoryServiceImpl` were `public` instead, nothing would stop `OrderService` (or any other class) from bypassing the interface and instantiating or depending on the implementation directly — for example, calling implementation-specific methods that aren't part of the `InventoryService` contract, or new-ing up a second, unmanaged instance of it that isn't wired into Spring's application context and doesn't share the same repository/transaction. That would silently break the module boundary: the "Inventory module" would no longer be swappable, its internal repository access could leak into `shop`, and later extracting Inventory into a separate microservice would require hunting down every place that touched the concrete class instead of just replacing one Spring bean behind an unchanged interface.

### 3. When to extract Inventory into its own microservice

Extraction would make sense once Inventory's scale, change frequency, or team ownership genuinely diverges from Order's — for example, if Inventory needs to be updated by other systems (a warehouse scanner, a supplier integration) far more often than Order changes, if Inventory needs to scale independently under much higher read traffic than Order does, or if a separate team owns and deploys Inventory on its own release schedule and coupling deployments together is slowing both teams down.

To actually do it, `InventoryService` would need to be reimplemented as an HTTP (or gRPC) client instead of a local class — something like `InventoryServiceHttpClient implements InventoryService`, making calls to a new standalone Inventory service and translating its JSON responses back into the same `Inventory` object shape. Because `OrderService` only depends on the `InventoryService` interface already, this swap wouldn't require any changes to `OrderService` or `OrderController` at all — that's exactly the payoff of having defined the module boundary through an interface from the start. We would additionally need to: split the database (Inventory gets its own schema/database rather than sharing tables with Order), add resilience patterns (timeouts, retries, circuit breakers) around the new network calls, decide how to handle the loss of a single shared transaction (likely an eventual-consistency approach, e.g. reserving stock, then confirming or releasing it based on whether the order save ultimately succeeds), and stand up separate deployment, monitoring, and versioning for the now-independent Inventory service.
