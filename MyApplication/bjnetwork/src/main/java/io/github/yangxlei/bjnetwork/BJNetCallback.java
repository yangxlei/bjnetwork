package io.github.yangxlei.bjnetwork;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by yanglei on 16/6/24.
 */
public abstract class BJNetCallback implements Callback {

    @Override
    public void onFailure(Call call, IOException e) {
        onFailure(e);
    }

    @Override
    public void onResponse(Call call, Response response) throws IOException {
        onResponse(new BJResponse(response));
        response.close();
    }

    public abstract void onFailure(Exception e);

    public abstract void onResponse(BJResponse response);
}
