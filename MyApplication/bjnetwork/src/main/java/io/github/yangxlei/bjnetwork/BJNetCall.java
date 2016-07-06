package io.github.yangxlei.bjnetwork;

import java.io.IOException;

import okhttp3.Response;

/**
 * Created by yanglei on 16/6/24.
 */
public interface BJNetCall {

    void cancel();

    /**
     * 同步执行请求
     * @param tag 请求所属的 tag. 如果 tag 被 JVM 回收, 会自动关闭对应的请求
     *            如果 tag 是调用者本身, 例如传的是 this. 会导致调用者本身不会被回收.
     * @return Response {@link Response}
     * @throws IOException
     */
    BJResponse executeSync(Object tag) throws IOException;

    /**
     * 异步执行请求
     * @param tag 请求所属的 tag. 如果 tag 被 JVM 回收, 会自动关闭对应的请求
     * @param callback {@link BJNetCallback}, {@link BJProgressCallback}, {@link BJDownloadCallback}
     */
    void executeAsync(Object tag, BJNetCallback callback);

    boolean isCanceled();

    boolean isExecuted();
}
