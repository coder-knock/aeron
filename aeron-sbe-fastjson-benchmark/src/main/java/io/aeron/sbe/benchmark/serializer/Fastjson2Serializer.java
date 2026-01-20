package io.aeron.sbe.benchmark.serializer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import io.aeron.sbe.benchmark.model.ComplexObject;

/**
 * Fastjson2序列化和反序列化工具类
 */
public class Fastjson2Serializer {

    /**
     * 序列化复杂对象到字节数组
     *
     * @param object 要序列化的对象
     * @return 序列化后的字节数组
     */
    public byte[] serialize(ComplexObject object) {
        return JSON.toJSONBytes(object, JSONWriter.Feature.WriteClassName);
    }

    /**
     * 反序列化字节数组到复杂对象
     *
     * @param data 要反序列化的字节数组
     * @return 反序列化后的对象
     */
    public ComplexObject deserialize(byte[] data) {
        return JSON.parseObject(data, ComplexObject.class);
    }
}