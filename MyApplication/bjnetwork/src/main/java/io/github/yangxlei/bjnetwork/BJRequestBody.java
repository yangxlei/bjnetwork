package io.github.yangxlei.bjnetwork;

import android.text.TextUtils;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Created by yanglei on 16/6/24.
 */
public class BJRequestBody {

    public static final MediaType MEDIA_TYPE_MARKDOWN = MediaType.parse("text/x-markdown; charset=utf-8");

    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    public static final MediaType MEDIA_TYPE_IMAGE = MediaType.parse("image/*");

    public static final MediaType MEDIA_TYPE_AUDIO = MediaType.parse("audio/*");

    public static final MediaType MEDIA_TYPE_STREAM = MediaType.parse("application/octet-stream");

    private RequestBody mRequestBody;

    public BJRequestBody(RequestBody requestBody) {
        this.mRequestBody = requestBody;
    }

    public RequestBody getRequestBody() {
        return mRequestBody;
    }

    /**
     * 提交一个字符串
     *
     * @param content 字符串
     * @return BJRequestBody
     */
    public static BJRequestBody createWithString(String content) {
        BJRequestBody requestBody = new BJRequestBody(RequestBody.create(MEDIA_TYPE_MARKDOWN, content));
        return requestBody;
    }

    /**
     * 提交 Json 字符串
     *
     * @param json 请求参数
     * @return BJRequestBody
     */
    public static BJRequestBody createWithJson(String json) {
        BJRequestBody requestBody = new BJRequestBody(RequestBody.create(MEDIA_TYPE_JSON, json));
        return requestBody;
    }

    /**
     * 使用 FormBody 的方式提交 (application/x-www-form-urlencoded)
     *
     * @param kv 请求参数
     * @return BJRequestBody
     */
    public static BJRequestBody createWithFormEncode(Map<String, String> kv) {
        if (kv == null || kv.isEmpty()) {
            throw new IllegalArgumentException("kv is empty!");
        }
        FormBody.Builder builder = new FormBody.Builder();
        Iterator<String> iterator = kv.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            String value = kv.get(key);
            builder.add(key, value);
        }

        BJRequestBody requestBody = new BJRequestBody(builder.build());
        return requestBody;
    }

    /**
     * 使用 MultiForm 的方式提交 (multipart/form-data)
     *
     * @param kv 请求参数
     * @return BJRequestBody
     */
    public static BJRequestBody createWithMultiForm(Map<String, String> kv) {
        return createWithMultiForm(kv, null, null, null);
    }


    /**
     * 使用 MultiForm 的方式提交文件 (multipart/form-data)
     *
     * @param kv       请求参数
     * @param fileKey  与 server 约定的 part key.
     * @param file     需要上传的文件
     * @param fileType MediaType
     *                 <ul>
     *                 <li>MEDIA_TYPE_MARKDOWN (文本)</li>
     *                 <li>MEDIA_TYPE_IMAGE (图片文件)</li>
     *                 <li>MEDIA_TYPE_AUDIO (音频文件)</li>
     *                 <li>MEDIA_TYPE_STREAM (流文件)</li>
     *                 </ul>
     * @return BJRequestBody
     */
    public static BJRequestBody createWithMultiForm(Map<String, String> kv, String fileKey, File file, MediaType fileType) {
        MultipartBody.Builder builder = new MultipartBody.Builder();

        if (kv != null && !kv.isEmpty()) {
            Iterator<String> iterator = kv.keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                String value = kv.get(key);
                builder.addFormDataPart(key, value);
            }
        }

        if (!TextUtils.isEmpty(fileKey) && file != null && fileType != null) {
            RequestBody body = RequestBody.create(fileType, file);
            builder.addFormDataPart(fileKey, file.getName(), body);
        }

        BJRequestBody requestBody = new BJRequestBody(builder.build());
        return requestBody;
    }
}
