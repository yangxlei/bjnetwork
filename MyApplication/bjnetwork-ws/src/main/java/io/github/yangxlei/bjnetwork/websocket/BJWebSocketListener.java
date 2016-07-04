package io.github.yangxlei.bjnetwork.websocket;

import java.io.InputStream;

/**
 * Created by yanglei on 16/6/28.
 */
public interface BJWebSocketListener {

    void onReconnect(BJWebSocketClient client);

    void onClose(BJWebSocketClient client);

    void onSentMessageFailure(BJWebSocketClient client, BJMessageBody messageBody);

    void onMessage(BJWebSocketClient client, String message);

    void onMessage(BJWebSocketClient client, InputStream inputStream);

    void onStateChanged(BJWebSocketClient client, BJWebSocketClient.State state);
}
