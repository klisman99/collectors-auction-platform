package io.github.klisman99.collectorsauctionplatform.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_mutation_guard")
class IdentityMutationGuard {

    @Id
    private Short id;

    protected IdentityMutationGuard() {
    }
}
