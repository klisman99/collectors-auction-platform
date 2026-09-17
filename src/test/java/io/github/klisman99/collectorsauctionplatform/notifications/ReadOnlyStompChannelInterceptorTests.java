package io.github.klisman99.collectorsauctionplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

class ReadOnlyStompChannelInterceptorTests {

  private final ReadOnlyStompChannelInterceptor interceptor = new ReadOnlyStompChannelInterceptor();

  @Test
  void anonymousClientMayConnectAndSubscribeToPublicAuctionTopics() {
    Message<byte[]> connect = frame(SimpMessageType.CONNECT, null);
    Message<byte[]> subscribe =
        frame(SimpMessageType.SUBSCRIBE, "/topic/auctions/55d176df-5230-49f1-b950-45c039f92f2e");

    assertThat(interceptor.preSend(connect, null)).isSameAs(connect);
    assertThat(interceptor.preSend(subscribe, null)).isSameAs(subscribe);
  }

  @Test
  void noClientSendFrameCanExecuteOrBroadcastACommand() {
    Message<byte[]> command =
        frame(SimpMessageType.MESSAGE, "/app/auctions/55d176df-5230-49f1-b950-45c039f92f2e/bids");
    Message<byte[]> brokerSend =
        frame(SimpMessageType.MESSAGE, "/topic/auctions/55d176df-5230-49f1-b950-45c039f92f2e");

    assertThatThrownBy(() -> interceptor.preSend(command, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> interceptor.preSend(brokerSend, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  private Message<byte[]> frame(SimpMessageType type, String destination) {
    SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
    headers.setDestination(destination);
    headers.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
  }
}
