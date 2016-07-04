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

    public BJRxNetRequestManager(BJNetworkClient client) {
        super(client);
    }

    public Observable<BJResponse> rx_newGetCall(String url) {
        return rx_newGetCall(url, null, 0);
    }

    public Observable<BJResponse> rx_newGetCall(String url, int cacheTime) {
        return  rx_newGetCall(url, null, cacheTime);
    }

    public Observable<BJResponse> rx_newGetCall(String url, Map<String, String> headers) {
        return rx_newGetCall(url, headers, 0);
    }

    public Observable<BJResponse> rx_newGetCall(String url, Map<String, String> headers, int cacheTime) {
        BJNetCall call = super.newGetCall(url, headers, cacheTime);
        Observable<BJResponse> observable = Observable.create(new CallOnSubscribe(call));
        return observable;
    }

    public Observable<BJResponse> rx_newPostCall(String url, BJRequestBody requestBody) {
        return  rx_newPostCall(url, requestBody, null);
    }

    public Observable<BJResponse> rx_newPostCall(String url, BJRequestBody requestBody, Map<String, String> headers) {
        BJNetCall call = super.newPostCall(url, requestBody, headers);
        Observable<BJResponse> observable = Observable.create(new CallOnSubscribe(call));
        return observable;
    }

    static final class CallOnSubscribe implements Observable.OnSubscribe<BJResponse> {
        private final BJNetCall originalCall;

        CallOnSubscribe(BJNetCall originalCall) {
            this.originalCall = originalCall;
        }

        @Override
        public void call(Subscriber<? super BJResponse> subscriber) {
            BJNetCall call = originalCall;

            // Wrap the call in a helper which handles both unsubscription and backpressure.
            RequestArbiter requestArbiter = new RequestArbiter(call, subscriber);
            subscriber.add(requestArbiter);
            subscriber.setProducer(requestArbiter);
        }
    }

    static final class RequestArbiter extends AtomicBoolean implements Subscription, Producer {
        private final BJNetCall call;
        private final Subscriber<? super BJResponse> subscriber;

        RequestArbiter(BJNetCall call, Subscriber<? super BJResponse> subscriber) {
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
                    subscriber.onNext(response);
                }
            } catch (Throwable t) {
                if (!subscriber.isUnsubscribed()) {
                    subscriber.onError(t);
                }

                if (t instanceof  HttpException) {
                    throw (HttpException)t;
                } else if (t instanceof Exception) {
                    HttpException exception = new HttpException((Exception) t);
                    throw exception;
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
