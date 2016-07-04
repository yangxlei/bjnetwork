package io.github.yangxlei.bjnetwork;

/**
 * Created by yanglei on 16/6/27.
 */
public class HttpException extends RuntimeException {
    private final int code;
    private final String message;
    private final Exception originException;
    private final BJResponse mResponse;

    public HttpException(Exception e) {
        this.code = -1;
        this.message = e.getMessage();
        this.originException = e;
        this.mResponse = null;
    }

    public HttpException(BJResponse response) {
        this.code = response.code();
        this.message = response.message();
        this.mResponse = response;
        this.originException = null;
    }

    public HttpException(int code, String message) {
        this.code = code;
        this.message = message;
        this.originException = null;
        this.mResponse = null;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public Exception getOriginException() {
        return originException;
    }

    public BJResponse getResponse() {
        return mResponse;
    }
}

