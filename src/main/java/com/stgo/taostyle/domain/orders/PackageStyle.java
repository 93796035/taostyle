package com.stgo.taostyle.domain.orders;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.stgo.taostyle.domain.Person;

@Entity
public class PackageStyle {

    @NotNull
    @ManyToOne
    private MainOrder mainOrder;

    @NotNull
    private String itemName;

    private String qualitySpecification;

    private String sizeSpecification;

    private String color;

    private int quantity;

    private String location;

    private String remark;

    /**
     */
    @ManyToOne
    private Person person;

    /**
     */
    private int recordStatus;

	public MainOrder getMainOrder() {
        return this.mainOrder;
    }

	public void setMainOrder(MainOrder mainOrder) {
        this.mainOrder = mainOrder;
    }

	public String getItemName() {
        return this.itemName;
    }

	public void setItemName(String itemName) {
        this.itemName = itemName;
    }

	public String getQualitySpecification() {
        return this.qualitySpecification;
    }

	public void setQualitySpecification(String qualitySpecification) {
        this.qualitySpecification = qualitySpecification;
    }

	public String getSizeSpecification() {
        return this.sizeSpecification;
    }

	public void setSizeSpecification(String sizeSpecification) {
        this.sizeSpecification = sizeSpecification;
    }

	public String getColor() {
        return this.color;
    }

	public void setColor(String color) {
        this.color = color;
    }

	public int getQuantity() {
        return this.quantity;
    }

	public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

	public String getLocation() {
        return this.location;
    }

	public void setLocation(String location) {
        this.location = location;
    }

	public String getRemark() {
        return this.remark;
    }

	public void setRemark(String remark) {
        this.remark = remark;
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

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

	@Version
    @Column(name = "version")
    private Integer version;

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


	public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
