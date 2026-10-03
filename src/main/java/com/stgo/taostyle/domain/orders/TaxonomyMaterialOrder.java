package com.stgo.taostyle.domain.orders;

import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.PersistenceContext;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;

@Entity
public class TaxonomyMaterialOrder {

    @ManyToOne
    private MainOrder mainOrder;

    @NotNull
    @ManyToOne
    private UserAccount factory;

    private String produceFormat;

    @Temporal(TemporalType.TIMESTAMP)
    @DateTimeFormat(style = "M-")
    private Date orderDate;

    @ManyToOne
    private UserAccount employee;

    private String itemName;

    private String color;

    private String sizeSpecification;

    @ManyToOne
    private UserAccount supplier;

    @NotNull
    private int quantity;

    private int CONS;

    private String unit;

    private int orderQuantity;

    private int ext_quantity;

    private int realQuantity;

    private int unitPrice;

    private int totalAmount;

    private int recd_qty;

    @Temporal(TemporalType.TIMESTAMP)
    @DateTimeFormat(style = "M-")
    private Date recd_date;

    private String remark;

    @ManyToOne
    private UserAccount approvedManager;

    private String approve_status;

    private String resonForApproveOrDeny;

    @Transient
    private String mainOrderStr;

    public String getMainOrderStr() {
        return mainOrderStr;
    }

    public void setMainOrderStr(
            String mainOrderStr) {
        this.mainOrderStr = mainOrderStr;
    }

    /**
     */
    @ManyToOne
    private Person person;

    /**
     */
    private int status;

    /**
     */
    private int recordStatus;

	public MainOrder getMainOrder() {
        return this.mainOrder;
    }

	public void setMainOrder(MainOrder mainOrder) {
        this.mainOrder = mainOrder;
    }

	public UserAccount getFactory() {
        return this.factory;
    }

	public void setFactory(UserAccount factory) {
        this.factory = factory;
    }

	public String getProduceFormat() {
        return this.produceFormat;
    }

	public void setProduceFormat(String produceFormat) {
        this.produceFormat = produceFormat;
    }

	public Date getOrderDate() {
        return this.orderDate;
    }

	public void setOrderDate(Date orderDate) {
        this.orderDate = orderDate;
    }

	public UserAccount getEmployee() {
        return this.employee;
    }

	public void setEmployee(UserAccount employee) {
        this.employee = employee;
    }

	public String getItemName() {
        return this.itemName;
    }

	public void setItemName(String itemName) {
        this.itemName = itemName;
    }

	public String getColor() {
        return this.color;
    }

	public void setColor(String color) {
        this.color = color;
    }

	public String getSizeSpecification() {
        return this.sizeSpecification;
    }

	public void setSizeSpecification(String sizeSpecification) {
        this.sizeSpecification = sizeSpecification;
    }

	public UserAccount getSupplier() {
        return this.supplier;
    }

	public void setSupplier(UserAccount supplier) {
        this.supplier = supplier;
    }

	public int getQuantity() {
        return this.quantity;
    }

	public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

	public int getCONS() {
        return this.CONS;
    }

	public void setCONS(int CONS) {
        this.CONS = CONS;
    }

	public String getUnit() {
        return this.unit;
    }

	public void setUnit(String unit) {
        this.unit = unit;
    }

	public int getOrderQuantity() {
        return this.orderQuantity;
    }

	public void setOrderQuantity(int orderQuantity) {
        this.orderQuantity = orderQuantity;
    }

	public int getExt_quantity() {
        return this.ext_quantity;
    }

	public void setExt_quantity(int ext_quantity) {
        this.ext_quantity = ext_quantity;
    }

	public int getRealQuantity() {
        return this.realQuantity;
    }

	public void setRealQuantity(int realQuantity) {
        this.realQuantity = realQuantity;
    }

	public int getUnitPrice() {
        return this.unitPrice;
    }

	public void setUnitPrice(int unitPrice) {
        this.unitPrice = unitPrice;
    }

	public int getTotalAmount() {
        return this.totalAmount;
    }

	public void setTotalAmount(int totalAmount) {
        this.totalAmount = totalAmount;
    }

	public int getRecd_qty() {
        return this.recd_qty;
    }

	public void setRecd_qty(int recd_qty) {
        this.recd_qty = recd_qty;
    }

	public Date getRecd_date() {
        return this.recd_date;
    }

	public void setRecd_date(Date recd_date) {
        this.recd_date = recd_date;
    }

	public String getRemark() {
        return this.remark;
    }

	public void setRemark(String remark) {
        this.remark = remark;
    }

	public UserAccount getApprovedManager() {
        return this.approvedManager;
    }

	public void setApprovedManager(UserAccount approvedManager) {
        this.approvedManager = approvedManager;
    }

	public String getApprove_status() {
        return this.approve_status;
    }

	public void setApprove_status(String approve_status) {
        this.approve_status = approve_status;
    }

	public String getResonForApproveOrDeny() {
        return this.resonForApproveOrDeny;
    }

	public void setResonForApproveOrDeny(String resonForApproveOrDeny) {
        this.resonForApproveOrDeny = resonForApproveOrDeny;
    }

	public Person getPerson() {
        return this.person;
    }

	public void setPerson(Person person) {
        this.person = person;
    }

	public int getStatus() {
        return this.status;
    }

	public void setStatus(int status) {
        this.status = status;
    }

	public int getRecordStatus() {
        return this.recordStatus;
    }

	public void setRecordStatus(int recordStatus) {
        this.recordStatus = recordStatus;
    }

	@PersistenceContext
    transient EntityManager entityManager;

	public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("mainOrder", "factory", "produceFormat", "orderDate", "employee", "itemName", "color", "sizeSpecification", "supplier", "quantity", "CONS", "unit", "orderQuantity", "ext_quantity", "realQuantity", "unitPrice", "totalAmount", "recd_qty", "recd_date", "remark", "approvedManager", "approve_status", "resonForApproveOrDeny", "mainOrderStr", "person", "status", "recordStatus");

	public static final EntityManager entityManager() {
        EntityManager em = new TaxonomyMaterialOrder().entityManager;
        if (em == null) throw new IllegalStateException("Entity manager has not been injected (is the Spring Aspects JAR configured as an AJC/AJDT aspects library?)");
        return em;
    }

	@Transactional
    public static long countTaxonomyMaterialOrders() {
        return findAllTaxonomyMaterialOrders().size();
    }

	@Transactional
    public static List<TaxonomyMaterialOrder> findAllTaxonomyMaterialOrders() {
        return entityManager().createQuery("SELECT o FROM TaxonomyMaterialOrder o", TaxonomyMaterialOrder.class).getResultList();
    }

	@Transactional
    public static List<TaxonomyMaterialOrder> findAllTaxonomyMaterialOrders(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM TaxonomyMaterialOrder o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager().createQuery(jpaQuery, TaxonomyMaterialOrder.class).getResultList();
    }

	@Transactional
    public static TaxonomyMaterialOrder findTaxonomyMaterialOrder(Long id) {
        if (id == null) return null;
        return entityManager().find(TaxonomyMaterialOrder.class, id);
    }

	@Transactional
    public static List<TaxonomyMaterialOrder> findTaxonomyMaterialOrderEntries(int firstResult, int maxResults) {
        return entityManager().createQuery("SELECT o FROM TaxonomyMaterialOrder o", TaxonomyMaterialOrder.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

	@Transactional
    public static List<TaxonomyMaterialOrder> findTaxonomyMaterialOrderEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM TaxonomyMaterialOrder o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager().createQuery(jpaQuery, TaxonomyMaterialOrder.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist() {
        if (this.entityManager == null) this.entityManager = entityManager();
        this.entityManager.persist(this);
    }

	@Transactional
    public void remove() {
        if (this.entityManager == null) this.entityManager = entityManager();
        if (this.entityManager.contains(this)) {
            this.entityManager.remove(this);
        } else {
            TaxonomyMaterialOrder attached = TaxonomyMaterialOrder.findTaxonomyMaterialOrder(this.id);
            this.entityManager.remove(attached);
        }
    }

	@Transactional
    public void flush() {
        if (this.entityManager == null) this.entityManager = entityManager();
        this.entityManager.flush();
    }

	@Transactional
    public void clear() {
        if (this.entityManager == null) this.entityManager = entityManager();
        this.entityManager.clear();
    }

	@Transactional
    public TaxonomyMaterialOrder merge() {
        if (this.entityManager == null) this.entityManager = entityManager();
        TaxonomyMaterialOrder merged = this.entityManager.merge(this);
        this.entityManager.flush();
        return merged;
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
