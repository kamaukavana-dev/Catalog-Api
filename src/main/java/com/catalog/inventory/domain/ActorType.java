package com.catalog.inventory.domain;

public enum ActorType {
    SYSTEM,          // Automated application logic
    ADMIN_USER,      // Human admin (actor_id = user UUID, from security context)
    SCHEDULED_JOB,   // @Scheduled tasks (reservation cleanup)
    ERP_INTEGRATION, // External ERP bulk import
    API_CLIENT       // External API client
}

