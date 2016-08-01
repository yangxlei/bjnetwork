package io.github.yangxlei.bjnetwork.rx;

/**
 * Created by yanglei on 16/7/13.
 */
public interface JsonAdapter {

    <T> T jsonStringToModel(Class<T> clazz, String json);

    String modelToJsonString(Object object);
}
