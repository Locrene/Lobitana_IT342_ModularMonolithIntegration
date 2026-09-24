# LegacySupply Integration

Lab 3 — Anti-Corruption Layer between this modular monolith and LegacySupply.

- Base URL: `https://legacysupply.onrender.com/api/v1`
- Client ID: `23-5497-731`
- API key: read from the `LS_API_KEY` environment variable (or the gitignored `.env`), never committed.

Items marked **TODO** are not yet measured. Everything else was observed directly against the
live API on 2026-09-24 or is taken from the code in `edu.cit.lobitana.supplier`.

---

## Section 1 — Product Mapping Table

Taken from `GET /catalog` on 2026-09-24. These values match `legacysupply.products.*` in
`backend/src/main/resources/application.properties`.

| My Product ID | Name | SupplierSku | PackSize | UnitCost |
|---|---|---|---|---|
| P100 | Wireless Mouse | `GJJ-6351` | 24 | PHP 450.00 |
| P200 | Mechanical Keyboard | `GJJ-7266` | 12 | PHP 1,899.00 |
| P300 | USB-C Hub | `GJJ-7179` | 6 | PHP 399.00 |

Nothing in the catalog identifies our products: LegacySupply has never heard of `P100`, and
`GJJ-6351` means nothing to us. The catalog gives free-text descriptions in their own
shorthand, so the match was made by hand and frozen into configuration. Two entries needed
care: `GJJ-4152` (USB-C CABLE 1M BRAIDED) matches the words "USB-C" better than the hub does
but is a cable, and `GJJ-6353` (MOUSE PAD XL) reads like a mouse but is not one. An automatic
description match would have picked either; a human reading the list did not.

## Section 2 — Session lifetime

A session is opened with `POST /auth/token`, carrying an XML body. The client id and API key
are **not** HTTP headers — they go inside the document:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<AuthRequest>
  <ClientId>23-5497-731</ClientId>
  <ApiKey>...</ApiKey>
</AuthRequest>
```

The reply carries the token and nothing about how long it lives:

```xml
<AuthResponse><SessionToken>ec50159f...</SessionToken><IssuedAt>2026-09-24T12:01:50.383Z</IssuedAt></AuthResponse>
```

**There is no expiry field** — only `SessionToken` and `IssuedAt`. The token is then sent on
every later call in the `X-LS-Session` header.

**Measured lifetime: TODO.** The application currently runs on a *configured guess* of 300
seconds (`legacysupply.session-ttl-seconds`). The log line `renewing session in 270s` is that
guess minus the 30-second refresh margin — it is the configuration talking, not a
measurement, and it must not be quoted as one. To measure it for real: sign in, then repeat a
cheap authenticated call every 60 seconds until it fails with 401, and record the elapsed
time. Put that number into `legacysupply.session-ttl-seconds`.

The adapter decides when to sign in again in two ways, both in `LegacySupplySessionManager`:

- **Proactively** — it stores a `renewAt` instant of `now + (TTL - margin)` and signs in
  again on the first call after that passes, so a request is not normally sent on a session
  that is about to die.
- **Reactively** — any 401 or 403 from LegacySupply calls `invalidate()`, which drops the
  token so the next call signs in again. That response is also classed as retryable, so the
  in-flight call retries on the fresh session rather than failing.

The reactive path is what makes the guessed TTL survivable: even if 300 seconds is wrong, the
first rejected call corrects it. No token is ever pasted in by hand.

## Section 3 — Error codes received

| Code | HTTP | What actually caused it |
|---|---|---|
| TODO | 4xx (non-retryable) | Submitting placeholder SKUs (`LS-SKU-P100`, `LS-SKU-P200`) before the real catalog was fetched. Both orders, `RO-1` and `RO-2`, ended `FAILED` in `supplier_orders`, which is the adapter classifying an unknown SKU as permanent rather than wasting retries on it. The exact code and status were not captured at the time. |
| TODO | | |

To fill this in, re-run a request with a deliberately bad SKU and record the response body:

```powershell
# after signing in and capturing $token
Invoke-WebRequest -Uri "$base/purchase-orders" -Method Post -ContentType "application/xml" `
  -Headers @{ "X-LS-Session" = $token; "X-Request-Id" = [guid]::NewGuid() } `
  -Body "<PurchaseOrder><SupplierSku>BAD-SKU</SupplierSku><Qty>1</Qty><BuyerRef>RO-TEST</BuyerRef></PurchaseOrder>"
```

How the adapter treats each class of failure (`LegacySupplyClient.classify`):

| Response | Treatment |
|---|---|
| 5xx, 408, 429 | Retryable — retried with exponential backoff, order stays `PENDING` |
| 401, 403 | Session dropped, then retried on a fresh session |
| Other 4xx | Permanent — order marked `FAILED`, never resent |
| Timeout / connection refused | Retryable — order stays `PENDING` for the scheduled job |

## Section 4 — What Qty and Uom mean

`Qty` is a whole number of **cases**, not a number of items. LegacySupply does not sell single
units: each SKU has a `PackSize` from the catalog, and an order moves whole packs. `Uom` is
the unit of measure that number is counted in. Our system counts single units, so every
reorder must be converted, and the conversion always rounds **up** — you cannot order part of
a case.

The request carries only `SupplierSku`, `Qty` and `BuyerRef`. There is no `Uom` to send: the
SKU already determines what one case holds. (The `Uom` value returned on order responses is
**TODO** — no successful purchase order has been observed yet, so the shape of that reply has
not been confirmed.)

**Worked example.** P100 (`GJJ-6351`) has a PackSize of 24. Stock falls to 3 and the reorder
target is 20 units, so 20 − 3 = **17 units needed**. 17 ÷ 24 = 0.708, which rounds up to
**Qty = 1**. That one case is 24 units, so **24 units arrive** for a 17-unit shortfall — the
smallest order LegacySupply will accept for this product already overshoots the target.

That is why `supplier_orders` stores both numbers: `cases` is what was sent, and `units` is
what will actually arrive (`cases × PackSize`). On delivery Inventory restocks `units` — the
real 24, not the requested 17 — so the stock count stays truthful. The conversion lives in
`UnitTranslator`; no other module ever sees a pack size.

## Section 5 — Unexpected status codes

LegacySupply reports status as a number. The confirmed mapping is:

| LegacySupply code | Our status |
|---|---|
| 10 | `CONFIRMED` |
| 20 | `PICKING` |
| 30 | `SHIPPED` |
| 40 | `DELIVERED` |

Any code outside that table becomes our `UNKNOWN`, which is deliberately an *open* status:

- the order keeps its row and its PO number, and the polling job keeps checking it, so a code
  that later resolves into something recognisable is picked up normally;
- **nothing is restocked.** Only an explicit `DELIVERED` mapping publishes the delivery event,
  so a status we cannot read can never invent inventory;
- `LegacySupplyStatusTranslator` logs a warning naming the exact code and the property to add
  (`legacysupply.status-map.<CODE>=<our status>`), so the fix is one config line, not a rebuild.

There is one deliberate exception. If a purchase order is *accepted* but the reply carries no
readable status, the translator returns `UNKNOWN` and `LegacySupplyAdapter.accept()`
overrides it to `SUBMITTED` — because we are holding a PO number, which is proof the order
landed, whatever the status field said. Treating that as `UNKNOWN` would risk resending an
order that already exists. The same applies to an order LegacySupply stops recognising: it is
logged and left alone rather than resent, because resending could duplicate a real order.

## Section 6 — How the anti-corruption layer is built

Only `SupplierGateway`, `ReorderOutcome`, `SupplierOrderStatus`, `SupplierOrderView` and
`events/SupplierOrderDeliveredEvent` are public. Everything else in
`edu.cit.lobitana.supplier` — the XML classes, the HTTP client, the session manager, the
translators and the adapter — is package-private.

| Concern | Where it lives |
|---|---|
| Low-stock rule placing a real order | `AutoReorderListener` (listens to Inventory's `LowStockEvent`, after commit) |
| Units to cases, product to SKU | `UnitTranslator` |
| LegacySupply codes to our statuses | `LegacySupplyStatusTranslator` |
| XML | `XmlCodec` and the `Xml*` classes |
| Session handling | `LegacySupplySessionManager` |
| Timeout, retry, backoff, request id | `LegacySupplyClient` |
| Never losing a reorder | `PendingOrderRetryJob` |
| Delivery tracking | `DeliveryStatusPollingJob` |

The auto-reorder rule was placed *inside* the supplier module rather than in Inventory:
Inventory publishes `LowStockEvent` and knows nothing about who listens, so the dependency
points one way only. Inventory's single reference to this module is the
`SupplierOrderDeliveredEvent` record it listens for — it never calls the module.

### Never duplicating an order

1. `BuyerRef` is `"RO-" + supplier_orders.id`, unique in the database.
2. `X-Request-Id` is a name-based UUID derived from the `BuyerRef` and **stored on the row**,
   so it is identical on every retry and after a restart.
3. Before any resend, the retry job asks LegacySupply whether that `BuyerRef` already exists
   and adopts the existing PO instead of sending a second one.
4. A product that already has an open supplier order does not get another, so repeated
   low-stock events while a PO is in flight collapse into one order.

GET lookups deliberately use a fresh request id: reads create nothing, and reusing the
order's id on a read could collide with an idempotency cache and return a stale reply.

### Never losing a reorder

A reorder is written to `supplier_orders` as `PENDING` *before* the first HTTP call. If
LegacySupply is slow, refusing or down, the row stays `PENDING` and `PendingOrderRetryJob`
keeps sending it — same `BuyerRef`, same `X-Request-Id` — until it lands. Nothing about a
reorder lives only in memory.

Because the reorder listener runs in the `AFTER_COMMIT` phase, where the original
transaction's resources are still bound to the thread, every write in `SupplierOrderStore` on
that path uses `Propagation.REQUIRES_NEW`. A plain `REQUIRED` transaction would silently join
the already-committed transaction and the rows would never be persisted.

### Staying inside the quota

Both scheduled jobs take a batch size (`legacysupply.retry-job.batch-size`,
`legacysupply.status-job.batch-size`), so one run can never cost more than that many
requests. Only open orders are polled, and each call retries at most
`legacysupply.max-attempts` times with exponential backoff.

## Section 7 — Running it

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

`LS_API_KEY` and `SUPABASE_DB_PASSWORD` are read from the gitignored `.env` at the repository
root via `spring.config.import`, so neither needs to be exported by hand.

Apply `sql/lab3_supplier.sql` to the database before first run (or `sql/schema.sql` for a full
rebuild). Supplier orders are visible at `GET /api/supplier-orders`.
