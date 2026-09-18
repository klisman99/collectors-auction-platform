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
    return tradingAccount(account);
  }

  /**
   * Locks a regular account for the duration of a command that could create or change trading
   * state. This serializes the command with account suspension.
   */
  @Transactional
  public TradingAccount lockTradingAccount(UUID accountId) {
    RegularAccount account = accounts.findByIdForUpdate(accountId).orElseThrow();
    return tradingAccount(account);
  }

  /** Locks an existing participant without requiring active trading eligibility. */
  @Transactional
  public AccountContact lockRegularAccount(UUID accountId) {
    RegularAccount account = accounts.findByIdForUpdate(accountId).orElseThrow();
    return new AccountContact(account.id(), account.normalizedEmail(), account.publicHandle());
  }

  private TradingAccount tradingAccount(RegularAccount account) {
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
