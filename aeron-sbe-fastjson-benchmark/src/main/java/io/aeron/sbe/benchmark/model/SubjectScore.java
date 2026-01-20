package io.aeron.sbe.benchmark.model;

/**
 * 科目分数类
 */
public class SubjectScore {
    private String subject;
    private double value;

    public SubjectScore() {
    }

    public SubjectScore(String subject, double value) {
        this.subject = subject;
        this.value = value;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "SubjectScore{" +
                "subject='" + subject + '\'' +
                ", value=" + value +
                '}';
    }
}