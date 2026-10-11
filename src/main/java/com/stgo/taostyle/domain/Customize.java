package com.stgo.taostyle.domain;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Entity
public class Customize {

    @NotNull
    private String cusKey;

    @NotNull
    private String cusValue;

    @ManyToOne
    private Person person;

    private int recordStatus;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

    // Getters and Setters
    public String getCusKey() {
        return this.cusKey;
    }

    public void setCusKey(String cusKey) {
        this.cusKey = cusKey;
    }

    public String getCusValue() {
        return this.cusValue;
    }

    public void setCusValue(String cusValue) {
        this.cusValue = cusValue;
    }

    public Person getPerson() {
        return this.person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public int getRecordStatus() {
        return this.recordStatus;
    }

    public void setRecordStatus(int recordStatus) {
        this.recordStatus = recordStatus;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return this.version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
