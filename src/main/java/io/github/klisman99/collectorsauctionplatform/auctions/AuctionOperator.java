package io.github.klisman99.collectorsauctionplatform.auctions;

import java.util.UUID;

record AuctionOperator(UUID accountId, Role role) {
  static AuctionOperator moderator(UUID accountId) {
    return new AuctionOperator(accountId, Role.MODERATOR);
  }

  static AuctionOperator administrator(UUID accountId) {
    return new AuctionOperator(accountId, Role.ADMINISTRATOR);
  }

  boolean isAdministrator() {
    return role == Role.ADMINISTRATOR;
  }

  enum Role {
    MODERATOR,
    ADMINISTRATOR
  }
}
