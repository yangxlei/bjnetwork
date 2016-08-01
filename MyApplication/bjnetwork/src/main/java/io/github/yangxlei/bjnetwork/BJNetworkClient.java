package io.github.yangxlei.bjnetwork;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.yangxlei.bjnetwork.dns.BJDns;
import io.github.yangxlei.cache.disk.DiskCache;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.internal.Util;

/**
 * Created by yanglei on 16/6/21.
 */
public class BJNetworkClient {

    private OkHttpClient mOkHttpClient;
    private DiskCache mCookieCache;
    private File cacheDir;
    private boolean enableHttp2x;
    private boolean enableLog;
    private int readTimeout;
    private int writeTimeout;
    private int connectTimeout;
    private BJDns dns;
    private List<Interceptor> mInterceptors;
    private List<Interceptor> mNetResponseInterceptors;

    public BJNetworkClient(Builder builder) {

        this.cacheDir = builder.cacheDir;
        this.enableHttp2x = builder.enableHttp2x;
        this.enableLog = builder.enableLog;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
        this.connectTimeout = builder.connectTimeout;
        this.dns = builder.mDns;
        this.mInterceptors = builder.mInterceptors;
        this.mNetResponseInterceptors = builder.mNetResponseInterceptors;

        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();

        buildCache(httpBuilder, builder.cacheDir);

        // 协议
        buildProtocol(httpBuilder, builder.enableHttp2x);

        // 日志
        buildLog(httpBuilder, builder.enableLog);

        // 超时
        httpBuilder.readTimeout(Math.max(10, builder.readTimeout), TimeUnit.SECONDS);
        httpBuilder.writeTimeout(Math.max(10, builder.writeTimeout), TimeUnit.SECONDS);
        httpBuilder.connectTimeout(Math.max(10, builder.connectTimeout), TimeUnit.SECONDS);

        // dns
        buildDns(httpBuilder, builder.mDns);

        // interceptors
        buildInterceptors(httpBuilder, builder.mInterceptors, builder.mNetResponseInterceptors);

        mOkHttpClient = httpBuilder.build();
    }

    public Call newCall(Request request) {
        return mOkHttpClient.newCall(request);
    }


    public OkHttpClient getOkHttpClient() {
        return mOkHttpClient;
    }

    private void buildCache(OkHttpClient.Builder builder, File cacheDir) {
        if (cacheDir == null) return;

        // cache
        Cache cache = new Cache(cacheDir, 1024 * 1024 * 100); // 100 MB
        builder.cache(cache);


        try {
            File cookieDir = new File(cacheDir, "cookies/");
            mCookieCache = DiskCache.create(cookieDir, BuildConfig.VERSION_CODE, 1024 * 1024 * 50);
        } catch (IOException e) {
            e.printStackTrace();
            mCookieCache = null;
        }

        // cookie
        builder.cookieJar(new CookieJar() {
            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                if (mCookieCache == null) return;
                if (cookies == null || cookies.size() == 0) return;

                String key = url.url().toString();
                mCookieCache.delete(key);

                ArrayList<SerializableOkHttpCookies> list = new ArrayList<>(cookies.size());
                for (Cookie cookie : cookies) {
                    SerializableOkHttpCookies sCookie = new SerializableOkHttpCookies(cookie);
                    list.add(sCookie);
                }

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ObjectOutputStream outputStream = null;
                try {
                    outputStream = new ObjectOutputStream(os);
                    outputStream.writeObject(list);
                } catch (IOException e) {
                } finally {
                    Util.closeQuietly(outputStream);
                }

                ByteArrayInputStream bais = new ByteArrayInputStream(os.toByteArray());
                Util.closeQuietly(os);
                mCookieCache.put(key, bais);
                Util.closeQuietly(bais);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                if (mCookieCache == null) return Collections.emptyList();

                InputStream inputStream = mCookieCache.getInputStream(url.url().toString());
                if (inputStream == null) return Collections.emptyList();

                ObjectInputStream objectInputStream = null;
                try {
                    objectInputStream = new ObjectInputStream(inputStream);
                    ArrayList<SerializableOkHttpCookies> sCookies = (ArrayList<SerializableOkHttpCookies>) objectInputStream.readObject();

                    ArrayList<Cookie> list = new ArrayList<>(sCookies.size());
                    for (SerializableOkHttpCookies sCookie : sCookies) {
                        list.add(sCookie.getCookies());
                    }
                    return list;

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    Util.closeQuietly(objectInputStream);
                }
                return Collections.emptyList();
            }
        });
    }

    private void buildProtocol(OkHttpClient.Builder builder, boolean enableHttp2x) {
        if (enableHttp2x) {
            builder.protocols(Util.immutableList(Protocol.HTTP_2, Protocol.SPDY_3, Protocol.HTTP_1_1));
        } else {
            builder.protocols(Util.immutableList(Protocol.HTTP_1_1));
        }
    }

    private void buildLog(OkHttpClient.Builder builder, boolean enableLog) {
        if (enableLog) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(log);
        }
    }

    private void buildDns(OkHttpClient.Builder build, Dns dns) {
        if (dns != null) {
            build.dns(dns);
        }
    }

    private void buildInterceptors(OkHttpClient.Builder builder, List<Interceptor> interceptors,  List<Interceptor> networkInterceptors) {
       if (interceptors != null && interceptors.size() > 0) {
           for (Interceptor interceptor : interceptors) {
               builder.addInterceptor(interceptor);
           }
       }

        if(networkInterceptors != null && networkInterceptors.size() > 0) {
            for (Interceptor interceptor : networkInterceptors) {
                builder.addNetworkInterceptor(interceptor);
            }
        }
    }

    public Builder newBuilder() {
        return new Builder(this);
    }


    public static class Builder {
        private boolean enableLog = false;
        private File cacheDir = null;
        private boolean enableHttp2x = false;
        private BJDns mDns= null;
        private int readTimeout;
        private int writeTimeout;
        private int connectTimeout;

        private List<Interceptor> mInterceptors;
        private List<Interceptor> mNetResponseInterceptors;

        public Builder() {
        }

        public Builder(BJNetworkClient client) {
            this.enableHttp2x = client.enableHttp2x;
            this.enableLog = client.enableLog;
            this.cacheDir = client.cacheDir;
            this.mDns = client.dns;
            this.readTimeout = client.readTimeout;
            this.writeTimeout = client.writeTimeout;
            this.connectTimeout = client.connectTimeout;
            this.mInterceptors = client.mInterceptors;
            this.mNetResponseInterceptors = client.mNetResponseInterceptors;
        }

        /**
         * 是否开启日志
         * @param enableLog default true
         * @return Builder
         */
        public Builder setEnableLog(boolean enableLog) {
            this.enableLog = enableLog;
            return this;
        }

        /**
         * 缓存路径 (cache, cookie)
         * @param cacheDir
         * @return Builder
         */
        public Builder setCacheDir(File cacheDir) {
            this.cacheDir = cacheDir;
            return this;
        }

        /**
         * 是否需要支持 http2(SPDY) 协议
         * @param enableHttp2x default true
         * @return Builder
         */
        public Builder setEnableHttp2x(boolean enableHttp2x) {
            this.enableHttp2x = enableHttp2x;
            return this;
        }

        public Builder setReadTimeoutAtSeconds(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder setWriteTimeoutAtSeconds(int writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public Builder setConnectTimeoutAtSeconds(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * 设置 DNS 实现接口.默认使用 SYSTEM.
         * @param dns
         * @return Builder
         */
        public Builder setDns(BJDns dns) {
            checkNotNull(dns);
            this.mDns = dns;
            return this;
        }

        /**
         * 添加请求拦截器
         * @param interceptor
         * @return Builder
         */
        public Builder addInterceptor(Interceptor interceptor) {
            checkNotNull(interceptor);
            if (mInterceptors == null) {
                mInterceptors = new ArrayList<>();
            }
            mInterceptors.add(interceptor);
            return this;
        }

        /**
         * 添加响应拦截器
         * @param interceptor
         * @return Builder
         */
        public Builder addNetResponseInterceptor(Interceptor interceptor) {
            checkNotNull(interceptor);
            if (mNetResponseInterceptors == null) {
                mNetResponseInterceptors = new ArrayList<>();
            }
            mNetResponseInterceptors.add(interceptor);
            return this;
        }



        public BJNetworkClient build() {
            return new BJNetworkClient(this);
        }

        private void checkNotNull(Object object) {
            if (object == null) {
                throw new IllegalArgumentException();
            }
        }
    }
}
