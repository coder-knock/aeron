package io.aeron.sbe.benchmark.util;

import io.aeron.sbe.benchmark.model.Address;
import io.aeron.sbe.benchmark.model.ComplexObject;
import io.aeron.sbe.benchmark.model.Contact;
import io.aeron.sbe.benchmark.model.SubjectScore;

import java.util.Random;

/**
 * 测试数据生成器
 */
public class DataGenerator {
    private static final Random RANDOM = new Random();
    private static final String[] CITIES = {"北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京"};
    private static final String[] COUNTRIES = {"中国", "美国", "英国", "日本", "德国", "法国", "加拿大", "澳大利亚"};
    private static final String[] SUBJECTS = {"数学", "语文", "英语", "物理", "化学", "生物", "历史", "地理"};
    private static final String[] TAGS = {"重要", "紧急", "普通", "低优先级", "已审核", "待处理", "已完成", "已取消"};

    /**
     * 生成测试用的复杂对象
     *
     * @return 复杂对象实例
     */
    public static ComplexObject generateComplexObject() {
        ComplexObject obj = new ComplexObject(
                RANDOM.nextLong(),
                "用户" + RANDOM.nextInt(10000),
                20 + RANDOM.nextInt(50),
                RANDOM.nextDouble() * 100,
                RANDOM.nextBoolean(),
                System.currentTimeMillis()
        );

        // 添加地址信息
        int addressCount = 1 + RANDOM.nextInt(3);
        for (int i = 0; i < addressCount; i++) {
            obj.addAddress(new Address(
                    "街道" + RANDOM.nextInt(100) + "号",
                    CITIES[RANDOM.nextInt(CITIES.length)],
                    String.format("%05d", RANDOM.nextInt(100000)),
                    COUNTRIES[RANDOM.nextInt(COUNTRIES.length)]
            ));
        }

        // 添加联系方式
        int contactCount = 1 + RANDOM.nextInt(2);
        for (int i = 0; i < contactCount; i++) {
            obj.addContact(new Contact(
                    "user" + RANDOM.nextInt(1000) + "@example.com",
                    "1" + String.format("%010d", RANDOM.nextInt(1000000000))
            ));
        }

        // 添加标签
        int tagCount = 1 + RANDOM.nextInt(5);
        for (int i = 0; i < tagCount; i++) {
            obj.addTag(TAGS[RANDOM.nextInt(TAGS.length)]);
        }

        // 添加分数
        int scoreCount = 2 + RANDOM.nextInt(4);
        for (int i = 0; i < scoreCount; i++) {
            obj.addScore(new SubjectScore(
                    SUBJECTS[RANDOM.nextInt(SUBJECTS.length)],
                    60 + RANDOM.nextDouble() * 40
            ));
        }

        return obj;
    }
}