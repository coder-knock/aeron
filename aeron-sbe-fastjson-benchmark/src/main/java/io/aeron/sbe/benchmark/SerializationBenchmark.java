package io.aeron.sbe.benchmark;

import io.aeron.sbe.benchmark.model.ComplexObject;
import io.aeron.sbe.benchmark.serializer.Fastjson2Serializer;
import io.aeron.sbe.benchmark.serializer.SbeSerializer;
import io.aeron.sbe.benchmark.util.DataGenerator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * SBE和Fastjson2序列化/反序列化性能基准测试
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Threads(4)
@SuppressWarnings("unused")
public class SerializationBenchmark {

    private ComplexObject complexObject;
    private SbeSerializer sbeSerializer;
    private Fastjson2Serializer fastjson2Serializer;
    private byte[] sbeSerializedData;
    private byte[] fastjson2SerializedData;

    @Setup
    public void setup() {
        // 初始化序列化器
        sbeSerializer = new SbeSerializer();
        fastjson2Serializer = new Fastjson2Serializer();

        // 生成测试数据
        complexObject = DataGenerator.generateComplexObject();

        // 预先序列化数据用于反序列化测试
        sbeSerializedData = sbeSerializer.serialize(complexObject);
        fastjson2SerializedData = fastjson2Serializer.serialize(complexObject);

        // 打印序列化后的数据大小，用于比较
        System.out.println("SBE序列化后大小: " + sbeSerializedData.length + " 字节");
        System.out.println("Fastjson2序列化后大小: " + fastjson2SerializedData.length + " 字节");
    }

    @Benchmark
    public void sbeSerialization(Blackhole blackhole) {
        byte[] data = sbeSerializer.serialize(complexObject);
        blackhole.consume(data);
    }

    @Benchmark
    public void sbeDeserialization(Blackhole blackhole) {
        ComplexObject obj = sbeSerializer.deserialize(sbeSerializedData);
        blackhole.consume(obj);
    }

    @Benchmark
    public void fastjson2Serialization(Blackhole blackhole) {
        byte[] data = fastjson2Serializer.serialize(complexObject);
        blackhole.consume(data);
    }

    @Benchmark
    public void fastjson2Deserialization(Blackhole blackhole) {
        ComplexObject obj = fastjson2Serializer.deserialize(fastjson2SerializedData);
        blackhole.consume(obj);
    }

    /**
     * 主方法，用于运行基准测试
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SerializationBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}