package io.aeron.sbe.benchmark.serializer;

import io.aeron.sbe.benchmark.model.Address;
import io.aeron.sbe.benchmark.model.ComplexObject;
import io.aeron.sbe.benchmark.model.Contact;
import io.aeron.sbe.benchmark.model.SubjectScore;
import io.aeron.sbe.benchmark.schema.*;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * SBE序列化和反序列化工具类
 */
public class SbeSerializer {
    private static final int BUFFER_SIZE = 4096;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ComplexObjectEncoder encoder = new ComplexObjectEncoder();
    private final ComplexObjectDecoder decoder = new ComplexObjectDecoder();
    private final MutableDirectBuffer encodeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_SIZE));

    /**
     * 序列化复杂对象到字节数组
     *
     * @param object 要序列化的对象
     * @return 序列化后的字节数组
     */
    public byte[] serialize(ComplexObject object) {
        // 重置缓冲区位置
        encodeBuffer.wrap(ByteBuffer.allocateDirect(BUFFER_SIZE));

        // 编码消息头
        encoder.wrapAndApplyHeader(encodeBuffer, 0, headerEncoder);

        // 编码基本字段
        encoder.id(object.getId())
                .age((short) object.getAge())
               .score(object.getScore())
               .active(object.isActive() ? BooleanType.TRUE : BooleanType.FALSE)
               .createdAt(object.getCreatedAt());



        // 编码分数列表
        ComplexObjectEncoder.ScoresEncoder scoresEncoder = encoder.scoresCount(object.getScores().size());
        for (SubjectScore score : object.getScores()) {
            scoresEncoder.next()
                    .value(score.getValue());
        }

        // 计算编码长度并返回字节数组
        int encodedLength = headerEncoder.encodedLength() + encoder.encodedLength();
        byte[] result = new byte[encodedLength];
        encodeBuffer.getBytes(0, result);
        return result;
    }

    /**
     * 反序列化字节数组到复杂对象
     *
     * @param data 要反序列化的字节数组
     * @return 反序列化后的对象
     */
    public ComplexObject deserialize(byte[] data) {
        DirectBuffer decodeBuffer = new UnsafeBuffer(data);

        // 解码消息头
        headerDecoder.wrap(decodeBuffer, 0);

        // 验证模板ID
        final int templateId = headerDecoder.templateId();
        if (templateId != ComplexObjectDecoder.TEMPLATE_ID) {
            throw new IllegalStateException("Template ids do not match: " + templateId);
        }

        // 解码消息体
        decoder.wrap(
                decodeBuffer,
                headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version()
        );

        // 创建对象并设置基本字段
        ComplexObject result = new ComplexObject();
        result.setId(decoder.id());
        result.setName(decoder.name().toString());
        result.setAge(decoder.age());
        result.setScore(decoder.score());
        result.setActive(decoder.active() == BooleanType.TRUE);
        result.setCreatedAt(decoder.createdAt());


        // 解码分数列表
        ComplexObjectDecoder.ScoresDecoder scoresDecoder = decoder.scores();
        for (ComplexObjectDecoder.ScoresDecoder scores : scoresDecoder) {
            SubjectScore score = new SubjectScore(
                    scores.subject().toString(),
                    scores.value()
            );
            result.addScore(score);
        }

        return result;
    }
}