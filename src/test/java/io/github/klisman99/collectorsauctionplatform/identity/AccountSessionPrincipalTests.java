package io.github.klisman99.collectorsauctionplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AccountSessionPrincipalTests {

  @Test
  void treatsAPreviouslySerializedRegularSessionAsARegularAccount() throws Exception {
    AccountSessionPrincipal legacyPrincipal =
        new AccountSessionPrincipal(
            UUID.randomUUID(),
            "collector_29",
            null,
            AccountStatus.ACTIVE,
            null,
            null,
            true,
            List.of(new SimpleGrantedAuthority("TRADING_ELIGIBLE")));

    AccountSessionPrincipal restored = deserialize(serialize(legacyPrincipal));

    assertThat(restored.accountTypeName()).isEqualTo("REGULAR");
    assertThat(restored.statusName()).isEqualTo("ACTIVE");
    assertThat(restored.canTrade()).isTrue();
  }

  private byte[] serialize(AccountSessionPrincipal principal) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(principal);
    }
    return bytes.toByteArray();
  }

  private AccountSessionPrincipal deserialize(byte[] bytes) throws Exception {
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return (AccountSessionPrincipal) input.readObject();
    }
  }
}
