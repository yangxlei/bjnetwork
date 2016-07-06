package io.github.yangxlei.bjnetwork;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

/**
 * Created by bjhl on 16/7/1.
 * 带进度条
 */
public class BJProgressRequestBody extends RequestBody {
    private final RequestBody requestBody;
    private final BJProgressCallback mProgressCallback;
    private BufferedSink bufferedSink;

    public BJProgressRequestBody(RequestBody requestBody, BJProgressCallback progressCallback) {
        this.requestBody = requestBody;
        this.mProgressCallback = progressCallback;
    }

    public MediaType contentType() {
        return this.requestBody.contentType();
    }

    public long contentLength() throws IOException {
        return this.requestBody.contentLength();
    }

    public void writeTo(BufferedSink sink) throws IOException {
        if(this.bufferedSink == null) {
            this.bufferedSink = Okio.buffer(this.sink(sink));
        }

        this.requestBody.writeTo(this.bufferedSink);
        this.bufferedSink.flush();
    }

    private Sink sink(final Sink sink) {
        return new ForwardingSink(sink) {
            long bytesWritten = 0L;
            long contentLength = 0L;

            public void write(Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);
                if(this.contentLength == 0L) {
                    this.contentLength = contentLength();
                }

                this.bytesWritten += byteCount;
                mProgressCallback.onProgress(this.bytesWritten, this.contentLength);
            }
        };
    }

}
