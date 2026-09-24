# Lab 3 — Reflection

Answers refer to the code in `edu.cit.lobitana.supplier`, the `supplier_orders` table, and
the application logs. Where a number has not been measured yet it is marked **TODO** rather
than guessed.

---

## Q1. LegacySupply never tells you how long a session lasts. Measure your session lifetime from your own logs, state the number, and explain how your adapter decides when to sign in again.

The `<AuthResponse>` carries only `SessionToken` and `IssuedAt` — there is no expiry field at
all, so the lifetime has to be discovered by experiment rather than read. **TODO: state the
measured number here.** My application currently runs on a configured guess of 300 seconds,
and the log line `renewing session in 270s` is simply that guess minus the 30-second refresh
margin; it is the configuration talking back to me, not evidence, so I have not quoted it as
a measurement. To measure it properly I need to sign in once and repeat a cheap authenticated
call every 60 seconds until it comes back 401, then put the elapsed time into
`legacysupply.session-ttl-seconds`.

My adapter decides when to re-authenticate in two ways, both inside
`LegacySupplySessionManager`. Proactively, it records a `renewAt` instant of
`now + (TTL - margin)` at every sign-in and signs in again on the first call after that
passes, so requests are not normally sent on a session that is about to expire. Reactively,
any 401 or 403 response calls `invalidate()`, which discards the token so the next call signs
in again, and that response is also classified as retryable so the in-flight request retries
on the fresh session instead of failing. The reactive path is what makes a wrong TTL
survivable: even if my 300-second guess is badly off, the first rejected request corrects it
automatically, and no token is ever pasted in by hand.

## Q2. The catalog reports PackSize and orders report Uom "CS". Using one of your own orders, show the arithmetic from "units your Inventory needed" to the Qty you sent, and to the units your Inventory received on delivery.

Take P100, the Wireless Mouse, which maps to SupplierSku `GJJ-6351` with a PackSize of 24.
When stock falls to 3 units, my low-stock threshold of 5 fires a `LowStockEvent`, and my
reorder rule tops the shelf back up to a target of 20 units, so the units needed are
20 − 3 = **17**. `UnitTranslator` then converts that into LegacySupply's terms: 17 ÷ 24 =
0.708 cases, which rounds **up** to **Qty = 1**, because a case cannot be broken. My adapter
therefore sends `Qty=1` against `GJJ-6351`, and records `cases = 1` and `units = 24` on the
`supplier_orders` row — 24 being what will actually arrive, not the 17 I asked for.

On delivery, the polling job publishes a `SupplierOrderDeliveredEvent` carrying `units`, and
Inventory restocks **24 units**, overshooting the 17-unit shortfall by 7. **TODO: replace
this paragraph's figures with the real delivered order (BuyerRef, PO number and the observed
restock) once one has completed end to end** — at the time of writing, `RO-1` and `RO-2` are
the only rows in `supplier_orders` and both are `FAILED`, having been sent with placeholder
SKUs before the catalog was fetched, so no order of mine has yet reached delivery. The
important point the arithmetic shows is that rounding up is not a rounding error: my stock
count would drift immediately if Inventory restocked the 17 requested instead of the 24 that
turn up on the pallet.

## Q3. Suppose LegacySupply is replaced next semester by a supplier with a JSON API and different status codes. List every class in your project that would have to change, and explain why your Order and Inventory modules are not on that list (or why they are).

Every class that would change lives inside `edu.cit.lobitana.supplier`: `LegacySupplyClient`
(HTTP calls and content types move from XML to JSON), `XmlCodec`, `XmlSessionRequest`,
`XmlSessionResponse`, `XmlPurchaseOrderRequest` and `XmlPurchaseOrderResponse` (all replaced
by JSON equivalents), `LegacySupplySessionManager` (a different auth endpoint, body and token
header), `LegacySupplyStatusTranslator` (a new code mapping), and `LegacySupplyProperties`
plus the `legacysupply.*` entries in `application.properties`. `LegacySupplyAdapter` would
change least of all — its retry, idempotency and duplicate-guard logic is written in my own
terms, so only the calls it makes into the client would move.

`SupplierGateway`, `ReorderOutcome`, `SupplierOrderStatus`, `SupplierOrder`,
`SupplierOrderStore` and both scheduled jobs stay exactly as they are, because they speak
only my domain types: product ids, units, cases and my own status enum. Order and Inventory
are not on the list either. Order never references the supplier module at all, and
Inventory's only link to it is importing the `SupplierOrderDeliveredEvent` record so it can
restock — it never calls the module, never sees a SupplierSku, a PackSize, an XML class or a
LegacySupply status code. That is exactly what the Anti-Corruption Layer was built to
guarantee: the supplier's vocabulary stops at the gateway, so swapping the supplier is a
change to one package rather than a change to the business modules.
