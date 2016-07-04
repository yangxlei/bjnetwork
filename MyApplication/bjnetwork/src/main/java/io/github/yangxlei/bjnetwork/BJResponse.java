package io.github.yangxlei.bjnetwork;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.Response;

/**
 * Created by yanglei on 16/6/27.
 */
public class BJResponse {
    private Response mResponse;
    public BJResponse(Response response) {
        this.mResponse = response;
    }

    public String protocol() {
        return mResponse.protocol().name();
    }

    public int code() {
        return mResponse.code();
    }

    public boolean isSuccessful() {
       return mResponse.isSuccessful();
    }

    public String message() {
        return mResponse.message();
    }

    public Map<String, List<String>> headers() {
        Headers headers = mResponse.headers();
        return headers.toMultimap();
    }

    public long sentRequestAtMillis() {
        return mResponse.sentRequestAtMillis();
    }

    public long receivedResponseAtMillis() {
        return mResponse.receivedResponseAtMillis();
    }

    public String getResponseString() throws IOException {
       return mResponse.body().string();
    }

    public InputStream getResponseStream() {
        return mResponse.body().byteStream();
    }

    public Response getResponse() {
        return mResponse;
    }
}
