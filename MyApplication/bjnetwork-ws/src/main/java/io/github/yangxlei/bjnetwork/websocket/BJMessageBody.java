package io.github.yangxlei.bjnetwork.websocket;

/**
 * Created by yanglei on 16/6/29.
 */
public class BJMessageBody {

    protected int originRetryCount;
    protected BJMessageBody(String content, int retryCount) {
        this.content = content;
        this.originRetryCount = retryCount;
        this.retryCount = originRetryCount;
    }

    private String content;
    protected int retryCount;

    public String getContent() {
        return content;
    }

    public int getRetryCount() {
        return originRetryCount;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("--->");
        builder.append(content);
        builder.append("<---; ");
        builder.append(originRetryCount-retryCount).append(" retry.");
        return builder.toString();
    }
}
