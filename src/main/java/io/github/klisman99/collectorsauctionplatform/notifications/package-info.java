@org.springframework.modulith.ApplicationModule(
    displayName = "Notifications",
    allowedDependencies = {
      "identity",
      "catalog",
      "moderation",
      "auctions",
      "bidding",
      "settlement"
    })
package io.github.klisman99.collectorsauctionplatform.notifications;
