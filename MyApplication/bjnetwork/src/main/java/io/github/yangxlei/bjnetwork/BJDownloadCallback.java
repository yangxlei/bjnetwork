package io.github.yangxlei.bjnetwork;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;

/**
 * Created by yanglei on 16/7/5.
 */
public abstract class BJDownloadCallback extends BJProgressCallback {

    protected File mStorageFile;

    @Override
    public void onResponse(final BJResponse response) {

        BufferedSink sink = null;
        try {
             sink = Okio.buffer(Okio.sink(mStorageFile));
            sink.writeAll(response.getResponse().body().source());
            onDownloadFinish(response, mStorageFile);
        } catch (FileNotFoundException e) {
//            e.printStackTrace();
            onFailure(new HttpException(e));
        } catch (IOException e) {
//            e.printStackTrace();
            onFailure(new HttpException(e));
        } finally {
            Util.closeQuietly(sink);
        }
    }

    public abstract void onDownloadFinish(BJResponse response, File file);
}
