# ADR-0001: Modular Monolith over Microservices
## Status: Accepted
## Context
The application is a product catalog API with several logical domains (Products, Inventory, Categories, Brands, Orders, Warehouses). While these domains are distinct, they are highly interdependent. A microservices architecture would introduce significant complexity in distributed transactions, data consistency, and operational overhead.

## Decision
We choose a modular monolith architecture. Modules are separated by packages and communicate primarily through domain events. This ensures strong transactional integrity where needed (within modules) while allowing for eventual consistency and decoupling where appropriate.

## Consequences
- Simplified development and deployment.
- No need for complex service discovery or API gateways at this stage.
- Easy transition to microservices if specific domains need to scale independently in the future.
