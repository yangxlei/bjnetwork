package io.github.yangxlei.bjnetwork;

import java.io.FileNotFoundException;
import java.io.IOException;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

/**
 * Created by yanglei on 16/7/5.
 */
public class BJProgressResponseBody extends ResponseBody {
    private ResponseBody mResponseBody;
    private BJProgressCallback mDownloadCallback;

    private BufferedSource mBufferedSource;
    private long progress = 0;
    private long contentLength = 0;

    public BJProgressResponseBody(ResponseBody responseBody, Headers headers, BJProgressCallback callback) throws FileNotFoundException {
        mResponseBody = responseBody;
        this.mDownloadCallback = callback;
        contentLength = mResponseBody.contentLength();
//        if (headers.get("Content-Range") != null) {
//            // 解析断点续传参数
//            String value = headers.get("Content-Range");
//            int end = value.indexOf("/");
//            contentLength = Long.parseLong(value.substring(end + 1));
//            value = value.substring("bytes ".length(), end);
//            String[] params = value.split("-");
//            progress = Long.parseLong(params[0]);
//        } else {
//            contentLength = responseBody.contentLength();
//        }

    }

    @Override
    public MediaType contentType() {
        return mResponseBody.contentType();
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public BufferedSource source() {
        if (mBufferedSource == null) {
            mBufferedSource = Okio.buffer(source(mResponseBody.source()));
        }
        return mBufferedSource;
    }

    private Source source(final Source source) {
        return new ForwardingSource(source) {

            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                long bytesRead = super.read(sink, byteCount);
                progress += bytesRead;
                if (bytesRead >= 0) {
                    mDownloadCallback.onProgress(progress, contentLength);
                }

                return bytesRead;
            }
        };
    }
}
