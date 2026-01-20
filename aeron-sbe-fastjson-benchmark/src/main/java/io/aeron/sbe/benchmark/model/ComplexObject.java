package io.aeron.sbe.benchmark.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于基准测试的复杂对象模型
 */
public class ComplexObject {
    private long id;
    private String name;
    private int age;
    private double score;
    private boolean active;
    private long createdAt;
    private List<Address> addresses = new ArrayList<>();
    private List<Contact> contacts = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private List<SubjectScore> scores = new ArrayList<>();

    public ComplexObject() {
    }

    public ComplexObject(long id, String name, int age, double score, boolean active, long createdAt) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.score = score;
        this.active = active;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<Address> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<Address> addresses) {
        this.addresses = addresses;
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<SubjectScore> getScores() {
        return scores;
    }

    public void setScores(List<SubjectScore> scores) {
        this.scores = scores;
    }

    public void addAddress(Address address) {
        this.addresses.add(address);
    }

    public void addContact(Contact contact) {
        this.contacts.add(contact);
    }

    public void addTag(String tag) {
        this.tags.add(tag);
    }

    public void addScore(SubjectScore score) {
        this.scores.add(score);
    }

    @Override
    public String toString() {
        return "ComplexObject{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", age=" + age +
                ", score=" + score +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", addresses=" + addresses +
                ", contacts=" + contacts +
                ", tags=" + tags +
                ", scores=" + scores +
                '}';
    }
}