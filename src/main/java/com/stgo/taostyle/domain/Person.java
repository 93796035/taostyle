package com.stgo.taostyle.domain;

import com.stgo.taostyle.web.TaoDebug;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.List;

@Entity
public class Person {

    @NotNull
    @Column(unique = true)
    private String name;

    private String password;

    private int recordStatus;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

    // Getters and Setters for fields
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if (!StringUtils.isEmpty(password)) {
            this.password = password;
        } else {
            // Your custom logging method
            TaoDebug.error("Attempted to set an empty string into person's password.");
        }
    }

    public int getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(int recordStatus) {
        this.recordStatus = recordStatus;
    }

    // Conversion methods using flexjson
    public String toJson() {
        return new flexjson.JSONSerializer().exclude("*.class").serialize(this);
    }

    public static Person fromJsonToPerson(String json) {
        return new flexjson.JSONDeserializer<Person>().use(null, Person.class).deserialize(json);
    }

    public static String toJsonArray(List<Person> collection) {
        return new flexjson.JSONSerializer().exclude("*.class").serialize(collection);
    }

    public static List<Person> fromJsonArrayToPeople(String json) {
        return new flexjson.JSONDeserializer<List<Person>>().use("values", Person.class).deserialize(json);
    }
}