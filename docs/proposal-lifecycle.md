# Proposal lifecycle (human in the loop)

## 1. State machine

```mermaid
stateDiagram-v2
    [*] --> PENDING : AI agent calls propose_* via MCP<br/>(Server A stores it)
    PENDING --> APPROVED : Manager clicks Approve
    PENDING --> REJECTED : Manager clicks Reject
    APPROVED --> ACTIVE : Flash promo start time reached /<br/>transfer dispatched
    ACTIVE --> COMPLETED : Promo window ends /<br/>stock received
    REJECTED --> [*]
    COMPLETED --> [*]
```

> `PENDING`, `APPROVED` and `REJECTED` are used by the client and the agent. `ACTIVE` and `COMPLETED` are optional states that Server A (Member 1) can add.

## 2. Approve / reject flow

```mermaid
sequenceDiagram
    autonumber
    actor M as Barista / Manager
    participant UI as client-app (#manager)
    participant A as Server A
    participant K as Kafka
    participant B as Server B

    UI->>A: GET /api/v1/proposals?status=PENDING&cafeId=...
    A-->>UI: proposals + AI reasoning
    M->>UI: reads the reasoning, clicks Approve
    UI->>A: POST /api/v1/proposals/{id}/approve {decidedBy}
    A->>A: apply change (set activeDiscountPct /<br/>move batch quantity)
    A->>K: ProposalApproved event
    K->>B: update read models
    A-->>UI: 200 OK
    UI->>UI: refresh list, toast "Proposal approved"
```

## 3. Proposal payloads

```mermaid
classDiagram
    class Proposal {
        +String proposalId
        +ProposalType type
        +Status status
        +String reason
        +String proposedBy
        +Instant createdAt
    }
    class FlashPromoProposal {
        +String cafeId
        +List~String~ menuItemIds
        +int discountPct
        +int durationHours
    }
    class TransferProposal {
        +String fromCafeId
        +String toCafeId
        +String batchId
        +double quantityGrams
    }
    Proposal <|-- FlashPromoProposal
    Proposal <|-- TransferProposal
```

Validation is done twice: in the MCP tool, which returns `{ok:false, message}` so the LLM can correct itself, and again in Server A.
