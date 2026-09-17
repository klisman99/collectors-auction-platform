package io.github.klisman99.collectorsauctionplatform.auctions;

record AuctionAccess(
    Auction auction,
    boolean exactReserveVisible,
    boolean internalNotesVisible,
    String winningBidderHandle) {}
