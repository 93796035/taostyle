package com.stgo.taostyle.domain;

import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Entity
public class Cart {

    @ManyToOne
    private Product product;

    private int quantity;

    private int weight;

    private String unit;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    private String status;

    @NotNull
    @ManyToOne
    private UserAccount owner;

    @ManyToOne
    private Person person;

    private int recordStatus;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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

    public Integer getVersion() {
        return this.version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Product getProduct() {
        return this.product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getWeight() {
        return this.weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getUnit() {
        return this.unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Date getCreatedDate() {
        return this.createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UserAccount getOwner() {
        return this.owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
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
    public String toJson() {
        return new JSONSerializer().exclude("*.class").serialize(this);
    }

    public static Cart fromJsonToCart(String json) {
        return new JSONDeserializer<Cart>().use(null, Cart.class).deserialize(json);
    }

    public static String toJsonArray(Collection<Cart> collection) {
        return new JSONSerializer().exclude("*.class").serialize(collection);
    }

    public static Collection<Cart> fromJsonArrayToCarts(String json) {
        return new JSONDeserializer<List<Cart>>().use(null, ArrayList.class).use("values", Cart.class).deserialize(json);
    }

    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
