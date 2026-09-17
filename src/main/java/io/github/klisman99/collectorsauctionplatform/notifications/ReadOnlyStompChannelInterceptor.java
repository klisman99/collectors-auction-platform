package io.github.klisman99.collectorsauctionplatform.notifications;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;

final class ReadOnlyStompChannelInterceptor implements ChannelInterceptor {

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
    if (messageType == SimpMessageType.MESSAGE) {
      throw new AccessDeniedException("STOMP SEND frames are not accepted.");
    }
    return message;
  }
}
