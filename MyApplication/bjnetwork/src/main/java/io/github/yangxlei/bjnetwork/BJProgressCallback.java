package io.github.yangxlei.bjnetwork;

/**
 * Created by yanglei on 16/7/6.
 */
public abstract class BJProgressCallback extends BJNetCallback {

    public abstract void onProgress(long progress, long total);
}
