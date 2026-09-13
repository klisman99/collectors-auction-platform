package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deliberate identity interface for product modules that must contact a regular account. */
@Service
public class AccountDirectory {

  private final RegularAccountRepository accounts;

  AccountDirectory(RegularAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Transactional(readOnly = true)
  public AccountContact regularAccount(UUID accountId) {
    RegularAccount account = accounts.findById(accountId).orElseThrow();
    return new AccountContact(account.id(), account.normalizedEmail(), account.publicHandle());
  }

  @Transactional(readOnly = true)
  public TradingAccount tradingAccount(UUID accountId) {
    RegularAccount account = accounts.findById(accountId).orElseThrow();
    return new TradingAccount(
        account.id(),
        account.publicHandle(),
        account.status() == AccountStatus.ACTIVE,
        account.isVerified());
  }

  public record AccountContact(UUID accountId, String email, String publicHandle) {}

  public record TradingAccount(
      UUID accountId, String publicHandle, boolean active, boolean verified) {

    public boolean eligible() {
      return active && verified;
    }
  }
}
