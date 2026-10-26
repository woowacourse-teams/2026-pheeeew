package com.pheeeew.notification.application;

import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;

public interface PushSender {

    boolean isAvailable();

    PushSendResult send(String token, PushMessage message);
}
