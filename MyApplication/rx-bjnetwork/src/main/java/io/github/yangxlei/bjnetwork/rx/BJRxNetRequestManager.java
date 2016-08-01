package io.github.yangxlei.bjnetwork.rx;


import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.yangxlei.bjnetwork.BJNetCall;
import io.github.yangxlei.bjnetwork.BJNetRequestManager;
import io.github.yangxlei.bjnetwork.BJNetworkClient;
import io.github.yangxlei.bjnetwork.BJRequestBody;
import io.github.yangxlei.bjnetwork.BJResponse;
import io.github.yangxlei.bjnetwork.HttpException;
import rx.Observable;
import rx.Producer;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.Exceptions;

/**
 * Created by yanglei on 16/6/25.
 */
public class BJRxNetRequestManager extends BJNetRequestManager {

    private JsonAdapter mJsonAdapter;

    public BJRxNetRequestManager(BJNetworkClient client) {
        this(client, null);
    }

    public BJRxNetRequestManager(BJNetworkClient client, JsonAdapter jsonAdapter) {
        super(client);
        this.mJsonAdapter = jsonAdapter;
    }

    @Override
    public BJNetworkClient getNetworkClient() {
        return super.getNetworkClient();
    }

    public JsonAdapter getJsonAdapter() {
        return mJsonAdapter;
    }

    public <T> Observable<T> rx_newGetCall(String url, Class<T> clazz) {
        return rx_newGetCall(url, null, 0, clazz);
    }

    public <T> Observable<T> rx_newGetCall(String url, int cacheTime, Class<T> clazz) {
        return  rx_newGetCall(url, null, cacheTime, clazz);
    }

    public <T> Observable<T> rx_newGetCall(String url, Map<String, String> headers, Class<T> clazz) {
        return rx_newGetCall(url, headers, 0, clazz);
    }

    public Observable<BJResponse> rx_newGetCall(String url, Map<String, String> headers, int cacheTime) {
        return rx_newGetCall(url, headers, cacheTime, BJResponse.class);
    }

    public <T> Observable<T> rx_newGetCall(String url, Map<String, String> headers, int cacheTime, Class<T> clazz) {
        BJNetCall call = super.newGetCall(url, headers, cacheTime);
        Observable<T> observable = Observable.create(new CallOnSubscribe(call, clazz, mJsonAdapter));
        return observable;
    }

    public <T> Observable<T> rx_newPostCall(String url, BJRequestBody requestBody, Class<T> clazz) {
        return  rx_newPostCall(url, requestBody, null, clazz);
    }

    public Observable<BJResponse> rx_newPostCall(String url, BJRequestBody requestBody, Map<String, String> headers) {
        return rx_newPostCall(url, requestBody, headers, BJResponse.class);
    }

    public <T> Observable<T> rx_newPostCall(String url, BJRequestBody requestBody, Map<String, String> headers, Class<T> clazz) {
        BJNetCall call = super.newPostCall(url, requestBody, headers);
        Observable<T> observable = Observable.create(new CallOnSubscribe(call, clazz, mJsonAdapter));
        return observable;
    }

    static final class CallOnSubscribe<T> implements Observable.OnSubscribe<T> {
        private final BJNetCall originalCall;

        private Class<T> resultClass;
        private JsonAdapter mJsonAdapter;

        CallOnSubscribe(BJNetCall originalCall, Class<T> resultClass, JsonAdapter jsonAdapter) {
            this.originalCall = originalCall;
            this.resultClass = resultClass;
            this.mJsonAdapter = jsonAdapter;
        }

        @Override
        public void call(Subscriber<? super T> subscriber) {
            BJNetCall call = originalCall;

            // Wrap the call in a helper which handles both unsubscription and backpressure.
            RequestArbiter requestArbiter = new RequestArbiter(call, subscriber);
            requestArbiter.resultClass = resultClass;
            requestArbiter.jsonAdapter = mJsonAdapter;
            subscriber.add(requestArbiter);
            subscriber.setProducer(requestArbiter);
        }
    }

    static final class RequestArbiter<T> extends AtomicBoolean implements Subscription, Producer {
        private final BJNetCall call;
        private Class<T> resultClass;
        private JsonAdapter jsonAdapter;
        private final Subscriber<? super T> subscriber;

        RequestArbiter(BJNetCall call, Subscriber<? super T> subscriber) {
            this.call = call;
            this.subscriber = subscriber;
        }

        @Override public void request(long n) {
            if (n < 0) throw new IllegalArgumentException("n < 0: " + n);
            if (n == 0) return; // Nothing to do when requesting 0.
            if (!compareAndSet(false, true)) return; // Request was already triggered.

            try {
                BJResponse response = call.executeSync(null);

                if (! response.isSuccessful()) {
                    throw new HttpException(response);
                }

                if (!subscriber.isUnsubscribed()) {
                    if (jsonAdapter == null && resultClass == null) {
                        throw new NullPointerException("Class<T> is null.");
                    } else if (resultClass.equals(BJResponse.class)) {
                        subscriber.onNext((T)response);
                    } else if (resultClass.equals(String.class)) {
                        subscriber.onNext((T)response.getResponseString());
                    } else {
                        if (jsonAdapter == null) {
                            throw new NullPointerException("JsonAdapter is null");
                        }
                        T t = jsonAdapter.jsonStringToModel(resultClass, response.getResponseString());
                        subscriber.onNext(t);
                    }
                }
            } catch (Throwable t) {
                if (t instanceof  HttpException) {
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onError(t);
                    }
                } else if (t instanceof Exception) {
                    HttpException exception = new HttpException((Exception) t);
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onError(exception);
                    }
                } else  {
                    Exceptions.throwIfFatal(t);
                }
            }

            if (!subscriber.isUnsubscribed()) {
                subscriber.onCompleted();
            }
        }

        @Override public void unsubscribe() {
            call.cancel();
        }

        @Override public boolean isUnsubscribed() {
            return call.isCanceled();
        }
    }
}
