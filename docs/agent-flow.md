# Autonomous agent – morning run

## 1. Sequence

```mermaid
sequenceDiagram
    autonumber
    participant S as Scheduler 07:00<br/>or POST /trigger-now
    participant AW as agent-worker
    participant MCP as mcp-server
    participant B as Server B (query)
    participant A as Server A (command)
    participant LLM as LLM

    S->>AW: runCycle(trigger)
    Note over AW: lock - only one run at a time (409 if busy)
    loop for every cafe
        AW->>MCP: tools/call get_at_risk_batches(cafe_id, 48)
        MCP->>B: GET /api/v1/batches?cafeId={id}
        B-->>MCP: batches
        MCP-->>AW: at-risk report (rules applied)
        AW->>MCP: tools/call get_drink_sales_velocity(cafe_id, 7)
        MCP->>B: GET /api/v1/sales?cafeId={id}&days=7
        B-->>MCP: daily cups
        MCP-->>AW: velocity per drink family
    end
    AW->>A: GET /api/v1/menu
    A-->>AW: menu items (valid menu_item_ids)

    alt AI_API_KEY configured
        AW->>LLM: system prompt + snapshot + 4 MCP tools
        loop until the model is done
            LLM-->>AW: tool call (e.g. propose_flash_menu_promo)
            AW->>MCP: tools/call (recorded)
            MCP->>A: POST /api/v1/proposals/...
            A-->>MCP: proposal {status: PENDING}
            MCP-->>AW: {ok: true, data}
            AW->>LLM: tool result
        end
        LLM-->>AW: summary of decisions
    else no key or LLM error
        AW->>AW: RuleBasedPlanner
        AW->>MCP: tools/call propose_* (same tools)
        MCP->>A: POST /api/v1/proposals/...
    end
    AW-->>S: AgentRun report (mode, summary, tool calls, warnings)
```

## 2. Decision logic

```mermaid
flowchart TD
    start([At-risk batch at cafe X]) --> expired{EXPIRED?}
    expired -- yes --> discard["⚠️ Warning: discard<br/>(never promoted or moved)"]
    expired -- no --> within{"Still within its limit?<br/>AGING_COFFEE /<br/>MATCHA_NEAR_OXIDATION"}
    within -- no --> promo
    within -- yes --> slow{"Drink family<br/>SLOW_MOVING at X?"}
    slow -- no --> promo
    slow -- yes --> near{"Cafe Y within 3 km sells it faster?<br/>(ACCELERATING or avg ≥ 1.5× X)"}
    near -- yes --> transfer["🚚 propose_inter_cafe_transfer<br/>X → Y, ~half the quantity"]
    near -- no --> promo["⚡ propose_flash_menu_promo at X<br/>menu items that use it<br/>25% if HIGH/CRITICAL else 15%, 3 h"]
```

| Ingredient category | Drinks used for the promo |
|---|---|
| `OAT_MILK`, `DAIRY_MILK`, `SOY_MILK` | Matcha lattes, Hot Chocolate |
| `MATCHA_TIN` | Matcha drinks |
| `DECAF_BEAN` | Decaf drinks |
| `ESPRESSO_BEAN`, `FILTER_BEAN` | Espresso / filter coffee |
| `LOOSE_TEA` | Tea |
| `COCOA_BATCH` | Hot Chocolate |

## 3. Freshness rules (`get_at_risk_batches`)

```mermaid
timeline
    title Ingredient life (days since roast / open)
    Coffee beans : 0-3 Resting (degassing) : 4-14 Peak flavour : 15-21 Good : 22+ STALE_COFFEE
    Matcha tin : 0-14 Vibrant : 15-25 Fresh : 26+ OXIDIZING_MATCHA
    Milk : expires in more than 48 h OK : 48 h or less MILK_EXPIRING : past date EXPIRED
```

Batches that will cross a limit **within the requested window** are reported early (`AGING_COFFEE`, `MATCHA_NEAR_OXIDATION`), so the agent can move them while they are still sellable.

## 4. Run report example (`POST /api/v1/agent/trigger-now`)

```json
{
  "runId": "82e0e2c4",
  "trigger": "MANUAL",
  "mode": "RULE_BASED",
  "status": "SUCCESS",
  "cafesAnalysed": ["CAFE-01", "CAFE-02", "CAFE-03"],
  "summary": "Transfer 500 g B-DEC-07 CAFE-01 -> CAFE-02: PENDING\nFlash promo CAFE-01 25% on Iced Matcha Latte & Hot Chocolate: PENDING",
  "proposalsCreated": 2,
  "toolCalls": [ { "tool": "get_at_risk_batches", "write": false, "ok": true }, "..." ],
  "warnings": ["No LLM API key configured (AI_API_KEY) - used rule-based planner"]
}
```
