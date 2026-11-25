# Architecture

## 1. System overview

```mermaid
flowchart LR
    subgraph Users
        C["👤 Customer<br/>(browser / phone GPS)"]
        M["🧑‍🍳 Barista / Manager"]
    end

    subgraph Client["client-app :8080 (nginx)"]
        UI["SPA<br/>Customer view + Manager view"]
    end

    subgraph AI["AI layer (Member 3)"]
        AW["agent-worker :8091<br/>@Scheduled 07:00<br/>POST /trigger-now"]
        MCP["mcp-server :8090<br/>Streamable HTTP /mcp<br/>4 @Tool methods"]
        LLM[("LLM<br/>Groq / OpenAI-compatible")]
    end

    subgraph Backend["Distributed backend (Members 1 & 2)"]
        A["Server A<br/>command-service :8081"]
        B["Server B<br/>query-service :8082"]
        PG[("PostgreSQL<br/>write DB")]
        K{{"Redpanda / Kafka"}}
    end

    C --> UI
    M --> UI
    UI -- "/api/v1/cafes, /batches, /sales" --> B
    UI -- "/api/v1/* (menu, orders, proposals)" --> A
    UI -- "/api/v1/agent/*" --> AW

    AW -- "MCP tools/call" --> MCP
    AW -- "chat + tool calling" --> LLM
    AW -. "GET /api/v1/menu" .-> A
    MCP -- "READ tools" --> B
    MCP -- "WRITE tools (PENDING)" --> A

    A --> PG
    A -- "domain events" --> K
    K -- "projections" --> B
```

## 2. CQRS + AI data flow

```mermaid
flowchart TB
    subgraph Write["Write side"]
        A["Server A<br/>commands"] --> PG[("PostgreSQL")]
    end
    subgraph Read["Read side"]
        B["Server B<br/>read models"]
    end
    A -- "OrderPlaced, BatchDeducted,<br/>ProposalCreated, ProposalApproved" --> K{{"Kafka topic"}}
    K --> B

    MCP["mcp-server"] -- "get_at_risk_batches<br/>get_drink_sales_velocity" --> B
    MCP -- "propose_flash_menu_promo<br/>propose_inter_cafe_transfer" --> A
```

The agent never changes data directly: it can only create **PENDING** proposals. A human approves them in the Manager view, and only then does Server A apply the change (discount or stock move) and publish the event.

## 3. Deployment (docker compose)

```mermaid
flowchart LR
    subgraph Host["Docker host"]
        direction LR
        subgraph net["compose network (default bridge)"]
            ca["client-app<br/>nginx:1.27"]
            aw["agent-worker<br/>temurin 21 JRE"]
            ms["mcp-server<br/>temurin 21 JRE"]
            cs["command-service"]
            qs["query-service"]
            pg[("postgres-write<br/>postgres:16")]
            rp{{"redpanda v24"}}
        end
    end
    browser(["Browser"]) -- ":8080" --> ca
    ca --> cs & qs & aw
    aw -- "http://mcp-server:8090/mcp" --> ms
    ms --> cs & qs
    cs --> pg & rp
    rp --> qs
    aw -- "HTTPS" --> llm(["LLM API"])
```

| Container | Image | Exposed | Depends on |
|---|---|---|---|
| `mcp-server` | built from `mcp-server/Dockerfile` | 8090 | Server A, Server B (resolved at call time) |
| `agent-worker` | built from `agent-worker/Dockerfile` | 8091 | `mcp-server` (healthcheck) |
| `client-app` | built from `client-app/Dockerfile` | 8080 → 80 | proxies to A, B, agent (uses Docker DNS, starts even if one is down) |

## 4. Components – mcp-server

```mermaid
classDiagram
    direction LR
    class CafeNetworkTools {
        +get_at_risk_batches(cafe_id, hours_to_expiry_or_stale)
        +get_drink_sales_velocity(cafe_id, days)
        +propose_flash_menu_promo(cafe_id, menu_item_ids, discount_pct, duration_hours, reason)
        +propose_inter_cafe_transfer(from_cafe_id, to_cafe_id, batch_id, quantity_grams, reason)
    }
    class FreshnessRules {
        +evaluate(batches, windowHours, now) List~AtRiskBatch~
    }
    class VelocityCalculator {
        +compute(cafeId, days, rows) SalesVelocityReport
    }
    class ServerBClient {
        +batches(cafeId)
        +dailySales(cafeId, days)
    }
    class ServerAClient {
        +proposeFlashPromo(request)
        +proposeTransfer(request)
    }
    class McpToolsConfig {
        +cafeNetworkToolCallbacks() ToolCallbackProvider
    }
    McpToolsConfig ..> CafeNetworkTools : registers @Tool methods
    CafeNetworkTools --> FreshnessRules
    CafeNetworkTools --> VelocityCalculator
    CafeNetworkTools --> ServerBClient : READ
    CafeNetworkTools --> ServerAClient : WRITE
```

## 5. Components – agent-worker

```mermaid
classDiagram
    direction LR
    class MorningScheduler {
        +morningRun() daily at 07:00
    }
    class AgentController {
        +triggerNow() POST /api/v1/agent/trigger-now
        +runs() GET /runs
        +latest() GET /runs/latest
        +status() GET /status
    }
    class AgentRunService {
        +runCycle(trigger) Optional~AgentRun~
        +history() List~AgentRun~
    }
    class McpToolGateway {
        +listTools()
        +call(tool, args, recorder)
    }
    class LlmPlanner {
        +plan(snapshotJson, tools) String
    }
    class RuleBasedPlanner {
        +plan(snapshot, executor, warnings) String
    }
    class RecordingMcpToolCallback
    class MenuCatalog {
        +load() List~MenuItemRef~
    }
    MorningScheduler --> AgentRunService
    AgentController --> AgentRunService
    AgentRunService --> McpToolGateway
    AgentRunService --> MenuCatalog
    AgentRunService --> LlmPlanner : API key present
    AgentRunService --> RuleBasedPlanner : fallback
    LlmPlanner --> RecordingMcpToolCallback : tools given to the LLM
    RecordingMcpToolCallback --> McpToolGateway
```
