package io.github.yangxlei.bjnetwork.dns;

import android.text.TextUtils;
import android.util.Log;

import io.github.yangxlei.bjnetwork.BJNetCall;
import io.github.yangxlei.bjnetwork.BJNetRequestManager;
import io.github.yangxlei.bjnetwork.BJNetworkClient;
import io.github.yangxlei.bjnetwork.BJResponse;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by yanglei on 16/6/28.
 * 基于腾讯的 DnsPod 实现的 Dns 服务.
 */
public class DnsPodImpl implements BJDns {

    private BJNetRequestManager mNetRequestManager;

    public DnsPodImpl(File dnsCacheDir) {
        BJNetworkClient client = new BJNetworkClient.Builder()
                .setCacheDir(dnsCacheDir)
                .addNetResponseInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        // 腾讯的接口返回不带 max-age. 手动加上
                        Request request = chain.request();
                        Response response = chain.proceed(request);
                        return response.newBuilder()
                                .header("Cache-Control", "public, max-age=600")
                                .build();
                    }
                })
                .build();

        mNetRequestManager = new BJNetRequestManager(client);
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (hostname == null) {
            throw new UnknownHostException();
        }

        long time = System.currentTimeMillis();
        try {
            if (hostname.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) {
                // hostname 本身为 IP 地址.
                return SYSTEM.lookup(hostname);
            }

            BJNetCall call = mNetRequestManager.newGetCall("http://119.29.29.29/d?dn="+hostname, 600);
            BJResponse response = call.executeSync(null);
            if (! response.isSuccessful()) {
                return SYSTEM.lookup(hostname);
            }
            String ips = response.getResponseString();

            if (TextUtils.isEmpty(ips) || !ips.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) {
                return SYSTEM.lookup(hostname);
            }

            return Arrays.asList(InetAddress.getAllByName(ips));
        } catch (IOException e) {
            e.printStackTrace();
            return SYSTEM.lookup(hostname);
        } catch (Exception e) {
            e.printStackTrace();
            return SYSTEM.lookup(hostname);
        } finally {
            Log.i("DNS(DnsPodImpl)", hostname + " use time: " + (System.currentTimeMillis() - time)+"ms");
        }
    }
}
