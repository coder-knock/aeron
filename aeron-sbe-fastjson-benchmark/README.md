# Aeron SBE 与 Fastjson2 序列化性能基准测试

本项目是一个基于JMH（Java Microbenchmark Harness）的基准测试，用于比较Aeron SBE（Simple Binary Encoding）和Fastjson2对复杂对象进行序列化和反序列化的性能差异。

## 项目结构

```
aeron-sbe-fastjson-benchmark/
├── src/
│   └── main/
│       ├── java/
│       │   └── io/aeron/sbe/benchmark/
│       │       ├── model/                  # 复杂对象模型
│       │       │   ├── Address.java
│       │       │   ├── ComplexObject.java
│       │       │   ├── Contact.java
│       │       │   └── SubjectScore.java
│       │       ├── serializer/             # 序列化工具
│       │       │   ├── Fastjson2Serializer.java
│       │       │   └── SbeSerializer.java
│       │       ├── util/                   # 工具类
│       │       │   └── DataGenerator.java
│       │       └── SerializationBenchmark.java  # JMH基准测试
│       └── resources/
│           └── schema/
│               └── complex-object-schema.xml  # SBE消息模板
└── pom.xml                                # Maven配置
```

## 技术栈

- JMH: Java微基准测试框架
- Aeron SBE: 高性能二进制编码库
- Fastjson2: 阿里巴巴开源的JSON处理库
- Maven: 项目构建工具

## 测试内容

本基准测试比较了以下操作的性能：

1. SBE序列化
2. SBE反序列化
3. Fastjson2序列化
4. Fastjson2反序列化

测试使用了一个包含多种数据类型和嵌套结构的复杂对象，以模拟真实场景中的数据结构。

## 运行方法

### 前提条件

- JDK 11或更高版本
- Maven 3.6或更高版本

### 编译和运行

1. 编译项目

```bash
mvn clean package
```

2. 运行基准测试

```bash
java -jar target/benchmarks.jar
```

或者直接通过IDE运行`SerializationBenchmark`类的main方法。

## 结果分析

运行基准测试后，JMH将输出详细的性能数据，包括每个操作的平均执行时间、标准偏差等统计信息。

一般来说，SBE在序列化和反序列化性能上通常优于Fastjson2，但具体差异取决于数据结构的复杂性和大小。

### 结果示例

```
Benchmark                                Mode  Cnt    Score    Error  Units
SerializationBenchmark.sbeSerialization  avgt    5    X.XXX ±  X.XXX  us/op
SerializationBenchmark.sbeDeserialization avgt    5    X.XXX ±  X.XXX  us/op
SerializationBenchmark.fastjson2Serialization avgt    5    X.XXX ±  X.XXX  us/op
SerializationBenchmark.fastjson2Deserialization avgt    5    X.XXX ±  X.XXX  us/op
```

此外，测试还会输出两种序列化方式产生的数据大小，可以比较它们的空间效率：

```
SBE序列化后大小: XXX 字节
Fastjson2序列化后大小: XXX 字节
```

## 自定义测试

如果需要调整测试参数或测试不同的数据结构，可以：

1. 修改`DataGenerator`类生成不同的测试数据
2. 调整`SerializationBenchmark`类中的JMH注解参数
3. 修改SBE消息模板以适应不同的数据结构

## 注意事项

- 运行基准测试时，建议关闭其他占用资源的应用程序，以获得更准确的结果
- JMH测试结果可能会因硬件环境不同而有所差异
- 首次运行时，SBE需要生成代码，可能会花费一些时间