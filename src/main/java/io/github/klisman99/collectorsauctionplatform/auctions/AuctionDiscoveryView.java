package io.github.klisman99.collectorsauctionplatform.auctions;

import java.util.Locale;

enum AuctionDiscoveryView {
  SCHEDULED,
  LIVE,
  ENDED;

  static AuctionDiscoveryView fromHttp(String value) {
    try {
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw AuctionApiException.invalidDiscoveryView();
    }
  }
}
