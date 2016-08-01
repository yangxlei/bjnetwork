package io.github.yangxlei.bjnetwork.websocket;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import io.github.yangxlei.bjnetwork.BJNetworkClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.ws.WebSocket;
import okhttp3.ws.WebSocketCall;
import okhttp3.ws.WebSocketListener;
import okio.Buffer;

/**
 * Created by yanglei on 16/6/28.
 */
public class BJWebSocketClient {

    public enum State {
        Offline, Connecting, Connected
    }

    public enum LogLevel {
        None, Info, Body
    }

    public static int ERROR_CODE_CLOSE_BY_USER = 3998;
    public static int ERROR_CODE_CLIENT_EXCEPTION = 3999;

    public static int MESSAGE_SEND_RETRY_COUNT = 5;

    private BJNetworkClient mNetworkClient;
    private WebSocketCall mWebSocketCall;
    private WebSocket mWebSocket;
    private BJWebSocketListener mListener;
    private WSListener mWSListener;
    private SendMessageThread mSendMessageThread;

    private State mState = State.Offline;
    private String address;

    private String mClientName;

    private LogLevel mLogLevel = LogLevel.Info;

    public BJWebSocketClient(String name) {
        this(name, null);
    }

    public BJWebSocketClient(String name, BJNetworkClient networkClient) {
        if (networkClient == null) {
            mNetworkClient = new BJNetworkClient.Builder()
                    .setReadTimeoutAtSeconds(600)
                    .build();
        } else {
            mNetworkClient = networkClient;
        }

        mSendMessageThread = new SendMessageThread();
        mClientName = name;
    }

    private ReconnectSignalHandler mReconnectSignalHandler;

    private static class ReconnectSignalHandler extends Handler  {
        private WeakReference<BJWebSocketClient> mWebSocketClient ;

        private HandlerThread mHandlerThread;

        private ReconnectSignalHandler(BJWebSocketClient client, HandlerThread handlerThread) {
            super(handlerThread.getLooper());
            mWebSocketClient = new WeakReference<>(client);
            mHandlerThread = handlerThread;
        }

        private void sendReconnectSignal() {
            if (! mHandlerThread.isAlive()) return;
            Message message = new Message();
            message.what = 0;
            sendMessageDelayed(message, 500);
        }

        private void quitReconnect() {
            removeMessages(0);
            mHandlerThread.quit();
        }

        @Override
        public void handleMessage(Message msg) {
            if (mWebSocketClient.get() == null) return;
            BJWebSocketClient client = mWebSocketClient.get();
            client.connect();
        }
    }

    public void setClientName(String clientName) {
        mClientName = clientName;
    }

    public String getClientName() {
        if (mClientName == null) {
            mClientName = "BJWebSocketClient";
        }
        return mClientName;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public void setListener(BJWebSocketListener listener) {
        mListener = listener;
    }

    public State getState() {
        return mState;
    }

    public List<BJMessageBody> getRequestQueue() {
        return new ArrayList<>(mSendMessageThread.mMessageQueue);
    }

    public void setLogLevel(LogLevel logLevel) {
        assert (logLevel != null);
        mLogLevel = logLevel;
    }

    private void setAndNotifyStateChanged(State state) {
        if (mState == state) return;

        mState = state;
       if (mListener != null) {
           mListener.onStateChanged(this, mState);
       }
    }

    public void connect() {
        logInfo("connect() while environment is " +
                "(state="+mState+", address="+address+", " +
                "SendMsgQueueSize="+mSendMessageThread.mMessageQueue.size()+")");

        if (mState != State.Offline) return;

        if (TextUtils.isEmpty(address)) {
            throw new NullPointerException("address is empty!");
        }

        if (mReconnectSignalHandler == null) {
            HandlerThread handlerThread = new HandlerThread("ReconnectSignalHandlerThread");
            handlerThread.start();
            mReconnectSignalHandler = new ReconnectSignalHandler(this, handlerThread);
        }

        setAndNotifyStateChanged(State.Connecting);

        Request request = new Request.Builder().url(getAddress())
                .build();
        mWebSocketCall = WebSocketCall.create(mNetworkClient.getOkHttpClient(),
                request);
        mWSListener = new WSListener();
        mWebSocketCall.enqueue(mWSListener);

        if (mSendMessageThread.getState() != Thread.State.NEW) {
            mSendMessageThread = mSendMessageThread.clone();
        }
    }

    public void disconnect() {
        disconnect(ERROR_CODE_CLOSE_BY_USER, "user close ws client.");
    }

    private synchronized void disconnect(int code, String reason) {
        logInfo(" disconnect("+code+", "+reason+") " +
                "while environment is (state="+mState+", address="+address+", " +
                "SendMsgQueueSize="+mSendMessageThread.mMessageQueue.size()+")");

        if (mSendMessageThread != null) {
            mSendMessageThread.interrupt();
        }

        if (code == ERROR_CODE_CLOSE_BY_USER) {
            if (mReconnectSignalHandler != null) {
                mReconnectSignalHandler.quitReconnect();
            }
            mReconnectSignalHandler = null;
        }

        if (mState == State.Offline) return;

        setAndNotifyStateChanged(State.Offline);

        try {
            if (mWebSocket != null) {
                mWebSocket.close(code, reason);
            } else {
                if (mWebSocketCall != null) {
                    mWebSocketCall.cancel();
                    mWebSocketCall = null;
                }
                if (code != ERROR_CODE_CLOSE_BY_USER) {
                    // 非用户主动退出
                    if (mListener != null) {
                        mListener.onReconnect(this);
                    }
//                    connect();
                    if (mReconnectSignalHandler != null) {
                        mReconnectSignalHandler.sendReconnectSignal();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (mWebSocketCall != null) {
                mWebSocketCall.cancel();
                mWebSocketCall = null;
            }
            if (code != ERROR_CODE_CLOSE_BY_USER) {
                // 非用户主动退出
                if (mListener != null) {
                    mListener.onReconnect(this);
                }
//                connect();
                if (mReconnectSignalHandler != null) {
                    mReconnectSignalHandler.sendReconnectSignal();
                }
            }
        }
    }

    public void sendMessage(String messag) {
        sendMessage(messag, MESSAGE_SEND_RETRY_COUNT);
    }

    public void sendMessage(String message, int retryCount) {
        assert (message != null);
        assert (retryCount >= 0);
        mSendMessageThread.add(message, retryCount);
    }

    private class WSListener implements WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            logInfo(" onOpen() while environment is " +
                    "(state="+mState+", address="+address+", " +
                    "SendMsgQueueSize="+mSendMessageThread.mMessageQueue.size()+")");

            setAndNotifyStateChanged(State.Connected);

            mWebSocket = webSocket;
            mSendMessageThread.start();
        }

        @Override
        public void onFailure(IOException e, Response response) {
            logException(e, " onFailure() while environment is " +
                    "(state="+mState+", address="+address+", " +
                    "SendMsgQueueSize="+mSendMessageThread.mMessageQueue.size()+")");

            e.printStackTrace();
            disconnect(ERROR_CODE_CLIENT_EXCEPTION, e.getMessage());
        }

        @Override
        public void onMessage(ResponseBody message) throws IOException {
            logInfo(" onMessage("+message+") while environment is " +
                    "(state="+mState+", address="+address+", " +
                    "SendMsgQueueSize="+mSendMessageThread.mMessageQueue.size()+")");

            try {
                if (mListener != null) {
                    if (message.contentType() == WebSocket.TEXT) {
                        String result = message.string();
                        logData("onMessage() recv TEXT: " + result);
                        mListener.onMessage(BJWebSocketClient.this, result);
                    } else {
                        mListener.onMessage(BJWebSocketClient.this, message.byteStream());
                    }
                }

                message.close();
            } catch (IOException e) {
                e.printStackTrace();

                logException(e, "onMessage()");
            }

        }

        @Override
        public void onPong(Buffer payload) {

        }

        @Override
        public void onClose(int code, String reason) {
            logInfo(" onClose("+code+", "+reason+") while environment is " +
                    "(state="+mState+", address="+address+", " +
                    "SendMsgQueueSize="+mSendMessageThread.mMessageQueue.size()+")");

            if (mWebSocketCall != null) {
                mWebSocketCall.cancel();
                mWebSocketCall = null;
            }

            setAndNotifyStateChanged(State.Offline);

            if (code != ERROR_CODE_CLOSE_BY_USER) {
                // 非用户主动退出
                if (mListener != null) {
                    mListener.onReconnect(BJWebSocketClient.this);
                }
                if (code != ERROR_CODE_CLIENT_EXCEPTION) {
                    disconnect(code, reason);
                }
//                connect();
                if (mReconnectSignalHandler != null) {
                    mReconnectSignalHandler.sendReconnectSignal();
                }
            } else {
                if (mListener != null) {

                    if(mSendMessageThread.mMessageQueue.size() > 0) {
                        BJMessageBody body = mSendMessageThread.mMessageQueue.poll();
                        while (body != null) {
                            mListener.onSentMessageFailure(BJWebSocketClient.this, body);

                            body = mSendMessageThread.mMessageQueue.poll();
                        }
                    }
                    mListener.onClose(BJWebSocketClient.this);
                }
            }
        }
    }

    private void logInfo(String log) {
        if (mLogLevel == LogLevel.Info || mLogLevel == LogLevel.Body) {
            Log.i(getClientName(), log);
        }
    }

    private void logData(String data) {
        if (mLogLevel == LogLevel.Body) {
            Log.i(getClientName(), data);
        }
    }

    private void logException(Exception e, String ext) {
        String message = e.getMessage();
        if (message == null && e.getCause() != null) {
            message = e.getCause().getMessage();
        }

        if (message == null) {
            message = e.toString();
        }
       Log.e(getClientName(), ext + " " +  message);
    }

    private class SendMessageThread extends Thread implements Cloneable {

        private LinkedBlockingQueue<BJMessageBody> mMessageQueue =  new LinkedBlockingQueue<>();

        private SendMessageThread() {
            super("SendMessageThread");
            setDaemon(true);
        }

        public void add(String message, int retryCount) {
            BJMessageBody messageBody = new BJMessageBody(message, retryCount);
            messageBody.retryCount = retryCount;
            mMessageQueue.add(messageBody);

        }

        @Override
        public void run() {

            BJMessageBody message = null;

            while (! interrupted()) {

                if (message != null) {
                    try {
                        RequestBody body = RequestBody.create(WebSocket.TEXT, message.getContent());
                        mWebSocket.sendMessage(body);
                        if (mLogLevel == LogLevel.Info) {
                            logInfo("sendMessage()  BJMessageBody(" + message.hashCode() + ", " + (message.originRetryCount - message.retryCount) +" retry)");
                        } else {
                            logData("sendMessage()  BJMessageBody(" + message + ")");
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                        if (message.retryCount >= 0) {
                            // 发送失败继续重试
                            message.retryCount -- ;
                            mMessageQueue.add(message);
                        } else {
                            if (mListener != null) {
                                mListener.onSentMessageFailure(BJWebSocketClient.this, message);
                            }
                        }

                    }
                }

                try {
                    message = mMessageQueue.take();
                } catch (InterruptedException ignore) {
                    break;
                }
            }
        }

        @Override
        protected SendMessageThread clone() {
            SendMessageThread thread = new SendMessageThread();
            thread.mMessageQueue = new LinkedBlockingQueue<>(mMessageQueue);
            return thread;
        }
    }
}
