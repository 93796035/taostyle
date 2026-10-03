package com.stgo.taostyle.domain;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.List;

@Entity
public class Feature {
    public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("id", "menuIdx", "groupTitle", "refMenuIdx", "itemsToShow", "person");

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @NotNull
    private String menuIdx;

    @NotNull
    private String refMenuIdx;

    private String itemsToShow;

    @NotNull
    @ManyToOne
    private Person person;

    @Version
    @Column(name = "version")
    private Integer version;

    // Getters and Setters
    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMenuIdx() {
        return this.menuIdx;
    }

    public void setMenuIdx(String menuIdx) {
        this.menuIdx = menuIdx;
    }

    public String getRefMenuIdx() {
        return this.refMenuIdx;
    }

    public void setRefMenuIdx(String refMenuIdx) {
        this.refMenuIdx = refMenuIdx;
    }

    public String getItemsToShow() {
        return this.itemsToShow;
    }

    public void setItemsToShow(String itemsToShow) {
        this.itemsToShow = itemsToShow;
    }

    public Person getPerson() {
        return this.person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Integer getVersion() {
        return this.version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
