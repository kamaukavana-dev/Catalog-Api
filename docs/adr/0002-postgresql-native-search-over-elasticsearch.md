# ADR-0002: PostgreSQL Native Search over Elasticsearch
## Status: Accepted
## Context
The product catalog requires full-text search and filtering by various attributes. Elasticsearch is the industry standard for search but adds significant operational complexity and data synchronization challenges (N+1 sync problem).

## Decision
We utilize PostgreSQL native full-text search (`tsvector`) and trigram similarity (`pg_trgm`). A denormalized `product_search_projection` table is maintained via domain events to provide a high-performance read model without complex joins.

## Consequences
- Reduced operational cost and complexity.
- Transactional consistency between product data and search index.
- Sufficient performance for the current catalog size (up to 100k products).
