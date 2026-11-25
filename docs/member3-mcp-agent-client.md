# Member 3 – Remote MCP Server, Autonomous Agent & Client App

```
 Client SPA (nginx :8080) ──► /api/v1/{cafes,batches,sales} ──► Server B  query-service   (Member 2, :8082)
        │                 └─► /api/v1/*        ──► Server A  command-service (Member 1, :8081)
        └──► /api/v1/agent/* ──► agent-worker (:8091) ──MCP / Streamable HTTP──► mcp-server (:8090)
                                    │  @Scheduled 07:00                           │ 4 @Tool methods
                                    └─ LLM (Groq, OpenAI-compatible)              ├─ READ  ──► Server B
                                                                                  └─ WRITE ──► Server A
```

| Module | Container | Port | Stack |
|---|---|---|---|
| `mcp-server/` | `mcp-server` | 8090 | Spring Boot 3.5, `spring-ai-starter-mcp-server-webmvc` (Spring AI 1.1), **Streamable HTTP** at `/mcp` (not stdio) |
| `agent-worker/` | `agent-worker` | 8091 | Spring Boot 3.5, `spring-ai-starter-mcp-client`, `spring-ai-starter-model-openai` |
| `client-app/` | `client-app` | 8080 | Vanilla JS SPA + Leaflet/OpenStreetMap, served by nginx (which also proxies the APIs) |

## 1. Remote MCP server – tools

| Tool | Kind | Calls |
|---|---|---|
| `get_at_risk_batches(cafe_id, hours_to_expiry_or_stale)` | read | `GET {B}/api/v1/batches?cafeId={cafeId}` → applies the rules: coffee > 21 days since roast, matcha tin open > 25 days, milk/perishables expiring within the window (default 48 h). Batches that cross a limit inside the window are flagged too (`AGING_COFFEE`, `MATCHA_NEAR_OXIDATION`). |
| `get_drink_sales_velocity(cafe_id, days)` | read | `GET {B}/api/v1/sales?cafeId={cafeId}&days=N` → cups per family (ESPRESSO, DECAF, MATCHA, TEA, HOT_CHOCOLATE), avg/day, trend, status `SLOW_MOVING` / `STEADY` / `ACCELERATING`. |
| `propose_flash_menu_promo(cafe_id, menu_item_ids, discount_pct, duration_hours, reason)` | write | `POST {A}/api/v1/proposals/flash-promo` |
| `propose_inter_cafe_transfer(from_cafe_id, to_cafe_id, batch_id, quantity_grams, reason)` | write | `POST {A}/api/v1/proposals/inter-cafe-transfer` |

Thresholds and endpoint paths can be changed in `mcp-server/src/main/resources/application.yaml` (`cafe.*`), so they can follow whatever Members 1 and 2 actually expose.

## 2. Agent worker

* `@Scheduled(cron = "0 0 7 * * *", zone = "Europe/Bucharest")` → `MorningScheduler`
* `POST /api/v1/agent/trigger-now` → runs the cycle right away (for the live demo) and returns the run report
* `GET /api/v1/agent/runs`, `GET /api/v1/agent/runs/latest`, `GET /api/v1/agent/status`

Cycle:
1. For every cafe in `agent.cafes`, call `get_at_risk_batches` and `get_drink_sales_velocity` through MCP.
2. Load the menu from Server A (`GET /api/v1/menu`) so promos use real `menu_item_ids`.
3. **LLM mode** (when `AI_API_KEY` is set): the model gets the snapshot, the cafe distance matrix and the 4 MCP tools. It decides between promos and transfers and calls the write tools, putting its reasoning in `reason`.
4. **Rule-based fallback** (no key, or the LLM call fails): a deterministic planner applies the same rules, so the demo always works.
5. Every MCP tool call is recorded and returned in the run report.

## 3. Client app

* **Customer** (`#customer`): uses browser GPS (falls back to Timișoara centre), shows the closest cafes on a map and in a list sorted by distance, filters by Coffee / Decaf / Matcha / Tea / Hot Chocolate, shows freshness badges ("Espresso: Roasted 9 days ago – Peak Flavor") and places orders.
* **Barista / Manager** (`#manager`): shows the agent's proposals with the LLM reasoning, has Approve / Reject buttons and a "Run AI agent now" button.
* If the backend can't be reached, the UI switches to clearly labelled offline demo data.

## 4. REST contract with Members 1 & 2

Status as of the latest sync with the teammates' branches:

| Endpoint | Owner | Status |
|---|---|---|
| `GET /api/v1/menu` | Member 1 | ✅ exists |
| `POST /api/v1/orders?cafeId=&menuItemId=&quantity=` → `"Order placed successfully. ID: ORD-…"` | Member 1 | ✅ exists (client sends one call per cart line) |
| `GET /api/v1/batches/{batchId}` | Member 2 | ✅ exists (on branch `member2-geo-discovery-and-velocity-read-service`) |
| `GET /api/v1/batches?cafeId=` | Member 2 | ⏳ needed (same resource, list by cafe) |
| `GET /api/v1/sales?cafeId=&days=` | Member 2 | ⏳ needed (projection of `OrderPlacedEvent`) |
| `GET /api/v1/cafes` | Member 2 | ⏳ needed (geo discovery) |
| `POST /api/v1/proposals/flash-promo`, `/inter-cafe-transfer` | Member 1 | ⏳ needed |
| `GET /api/v1/proposals?status=&cafeId=`, `POST /api/v1/proposals/{id}/approve` and `/reject` | Member 1 | ⏳ needed |

**Data formats accepted.** Both teammates' shapes are parsed, so nobody has to rename fields:

```
Batch (Member 2, BatchRegisteredEvent):  { batchId, cafeId, ingredientCategory, ingredientName, quantity, unit,
                                           roastDate, openedAt, expiryDate }            dates as ISO strings
Batch (Member 1, IngredientBatch /       { batchId, cafeId, category, name, remainingQuantity,
       BatchStockUpdatedEvent):            roastOrOpenDate, expiryDate }
Sales row:                               { date, beverageGroup, cups }  or raw OrderPlacedEvent
                                         { timestamp, beverageGroup, cupsSold }  (summed per day)
Cafe:                                    { cafeId, name, address, lat, lng }
```

Coffee age is counted from `roastDate`; matcha and milk from `openedAt`.

**Proposal payloads (Server A):**

```
POST /api/v1/proposals/flash-promo          { cafeId, menuItemIds[], discountPct, durationHours, reason, proposedBy }
POST /api/v1/proposals/inter-cafe-transfer  { fromCafeId, toCafeId, batchId, quantityGrams, reason, proposedBy }
     → { proposalId, status:"PENDING", ... }
GET  /api/v1/proposals?status=PENDING&cafeId=CAFE-01
POST /api/v1/proposals/{id}/approve | /reject   { decidedBy }
```

All paths are configurable in `mcp-server/src/main/resources/application.yaml` (`cafe.server-a.*`, `cafe.server-b.*`).

## 5. Run

```bash
cp .env.example .env        # put your own AI_API_KEY in .env (git-ignored), or leave it empty for the rule-based mode
docker compose up --build
```

* Client: http://localhost:8080 (`#customer`, `#manager`)
* Trigger the agent: `curl -X POST http://localhost:8091/api/v1/agent/trigger-now`
* MCP endpoint (for MCP Inspector, etc.): `http://localhost:8090/mcp`

> Security: the repository is public. API keys are only read from environment variables / a local `.env`; nothing secret is committed.
