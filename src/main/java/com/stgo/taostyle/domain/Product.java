package com.stgo.taostyle.domain;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.beans.factory.annotation.Configurable;
import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import java.util.Collection;
import java.util.List;

@Entity
public class Product implements MultiLanguageabl {

    @NotNull
    @Column(unique = true)
    @Size(min = 2)
    private String partNo;

    private String name;
    private String producedplace;
    private String c1;
    private String c2;
    private String c3;
    private String Litorweight;
    private String unit;
    private String packagesname;
    private String itemquantity;
    private int lenth;
    private int width;
    private int height;
    private int price1;
    private int price2;
    private int price3;
    // POS inventory fields synchronized by Security/CloudSync.
    private int stock;
    private int stock1;
    private int stock2;
    private int stock3;
    private int weeklySalesQty;
    private int monthlySalesQty;
    private int caseQty;
    private int qst;
    private int deleted;
    private java.math.BigDecimal coverdays;
    private java.util.Date lastModified;
    private String description;

    @ManyToOne
    private Person person;

    private int recordStatus;

    @Transient
    private String localName;

    @Transient
    private String localDescription;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

    // Getters and Setters...
    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    @Transient
    public Object[] getPrices() {
        return new Integer[]{price1, price2, price3};
    }

    @Transient
    public Object[] getCategories() {
        return new String[]{c1, c2, c3};
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public String toJson() {
        return new JSONSerializer().exclude("*.class").serialize(this);
    }

    public static Product fromJsonToProduct(String json) {
        return new JSONDeserializer<Product>().use(null, Product.class).deserialize(json);
    }

    public static String toJsonArray(Collection<Product> collection) {
        return new JSONSerializer().exclude("*.class").serialize(collection);
    }

    public static Collection<Product> fromJsonArrayToProducts(String json) {
        return new JSONDeserializer<List<Product>>().use(null, List.class).use("values", Product.class).deserialize(json);
    }

    public String getPartNo() {
        return this.partNo;
    }

    public String getLocalName() {
        return localName;
    }

    @Override
    public void setLocalName(
            String localName) {
        this.localName = localName;
    }

    public String getLocalDescription() {
        return localDescription;
    }

    @Override
    public void setLocalDescription(
            String localDescription) {
        this.localDescription = localDescription;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public String getDescription() {
        return this.description;
    }

    public void setPartNo(String partNo) {
        this.partNo = partNo;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProducedplace() {
        return this.producedplace;
    }
    public void setDescription(String description) {
        this.description = description;
    }


    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public int getStock1() { return stock1; }
    public void setStock1(int stock1) { this.stock1 = stock1; }
    public int getStock2() { return stock2; }
    public void setStock2(int stock2) { this.stock2 = stock2; }
    public int getStock3() { return stock3; }
    public void setStock3(int stock3) { this.stock3 = stock3; }
    public int getWeeklySalesQty() { return weeklySalesQty; }
    public void setWeeklySalesQty(int weeklySalesQty) { this.weeklySalesQty = weeklySalesQty; }
    public int getMonthlySalesQty() { return monthlySalesQty; }
    public void setMonthlySalesQty(int monthlySalesQty) { this.monthlySalesQty = monthlySalesQty; }
    public int getCaseQty() { return caseQty; }
    public void setCaseQty(int caseQty) { this.caseQty = caseQty; }
    public int getQst() { return qst; }
    public void setQst(int qst) { this.qst = qst; }
    public int getDeleted() { return deleted; }
    public void setDeleted(int deleted) { this.deleted = deleted; }
    public java.math.BigDecimal getCoverdays() { return coverdays; }
    public void setCoverdays(java.math.BigDecimal coverdays) { this.coverdays = coverdays; }
    public java.util.Date getLastModified() { return lastModified; }
    public void setLastModified(java.util.Date lastModified) { this.lastModified = lastModified; }

    public void setProducedplace(String producedplace) {
        this.producedplace = producedplace;
    }

    public String getC1() {
        return this.c1;
    }

    public void setC1(String c1) {
        this.c1 = c1;
    }

    public String getC2() {
        return this.c2;
    }

    public void setC2(String c2) {
        this.c2 = c2;
    }

    public String getC3() {
        return this.c3;
    }

    public void setC3(String c3) {
        this.c3 = c3;
    }

    public String getLitorweight() {
        return this.Litorweight;
    }

    public void setLitorweight(String Litorweight) {
        this.Litorweight = Litorweight;
    }

    public String getUnit() {
        return this.unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getPackagesname() {
        return this.packagesname;
    }

    public void setPackagesname(String packagesname) {
        this.packagesname = packagesname;
    }

    public String getItemquantity() {
        return this.itemquantity;
    }

    public void setItemquantity(String itemquantity) {
        this.itemquantity = itemquantity;
    }

    public int getLenth() {
        return this.lenth;
    }

    public void setLenth(int lenth) {
        this.lenth = lenth;
    }

    public int getWidth() {
        return this.width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = height;
    }
    public int getPrice1() {
        return this.price1;
    }

    public void setPrice1(int price1) {
        this.price1 = price1;
    }

    public int getPrice2() {
        return this.price2;
    }

    public void setPrice2(int price2) {
        this.price2 = price2;
    }

    public int getPrice3() {
        return this.price3;
    }

    public void setPrice3(int price3) {
        this.price3 = price3;
    }
}
