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

@Entity
public class ShipmentStyle {

    @NotNull
    @ManyToOne
    private MainOrder mainOrder;

    private int quantity;

    @NotNull
    private String shipment;

    @Temporal(TemporalType.TIMESTAMP)
    @DateTimeFormat(style = "M-")
    private Date outdoorDate;

    @Temporal(TemporalType.TIMESTAMP)
    @DateTimeFormat(style = "M-")
    private Date onboardDate;

    private String remark;

    @Transient
    private String mainOrderStr;

    @Transient
    private String outdoorDateStr = "abc";

    @Transient
    private String onboardDateStr;

    public static List<ShipmentStyle> findAllShipmentStylesByMainOrder(
            Long mainOrderId) {
//        MainOrder tMainOrder = MainOrder.findMainOrder(mainOrderId);
//        if (tMainOrder == null) {
            return entityManager().createQuery("SELECT o FROM ShipmentStyle o", ShipmentStyle.class).getResultList();
//        } else {
//            EntityManager tEntityManager = entityManager();
//            TypedQuery<ShipmentStyle> tQuery =
//                    tEntityManager.createQuery("SELECT o FROM ShipmentStyle AS o WHERE o.mainOrder = :pKey",
//                            ShipmentStyle.class);
//            tQuery = tQuery.setParameter("pKey", tMainOrder);
//            List<ShipmentStyle> shipmentStyles = new ArrayList<ShipmentStyle>();
//            try {
//                shipmentStyles = tQuery.getResultList();
//            } catch (Exception e) {
//            }
//            return shipmentStyles;
//        }
    }

    public static List<ShipmentStyle> findShipmentStyleEntriesByMainOrder(
            Long mainOrderId,
            int firstResult,
            int maxResults) {
//        MainOrder tMainOrder = MainOrder.findMainOrder(mainOrderId);
//        if (tMainOrder == null) {
            return entityManager().createQuery("SELECT o FROM ShipmentStyle o", ShipmentStyle.class)
                    .setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
//        } else {
//            EntityManager tEntityManager = entityManager();
//            TypedQuery<ShipmentStyle> tQuery =
//                    tEntityManager.createQuery("SELECT o FROM ShipmentStyle AS o WHERE o.mainOrder = :pKey",
//                            ShipmentStyle.class);
//            tQuery = tQuery.setParameter("pKey", tMainOrder);
//            if (firstResult > -1 && maxResults > 0)
//                tQuery = tQuery.setFirstResult(firstResult).setMaxResults(maxResults);
//            // to make sure not return a null;
//            List<ShipmentStyle> shipmentStyles = new ArrayList<ShipmentStyle>();
//            try {
//                shipmentStyles = tQuery.getResultList();
//            } catch (Exception e) {
//            }
//            return shipmentStyles;
//        }
    }

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
    private int recordStatus;

	public MainOrder getMainOrder() {
        return this.mainOrder;
    }

	public void setMainOrder(MainOrder mainOrder) {
        this.mainOrder = mainOrder;
    }

	public int getQuantity() {
        return this.quantity;
    }

	public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

	public String getShipment() {
        return this.shipment;
    }

	public void setShipment(String shipment) {
        this.shipment = shipment;
    }

	public Date getOutdoorDate() {
        return this.outdoorDate;
    }

	public void setOutdoorDate(Date outdoorDate) {
        this.outdoorDate = outdoorDate;
    }

	public Date getOnboardDate() {
        return this.onboardDate;
    }

	public void setOnboardDate(Date onboardDate) {
        this.onboardDate = onboardDate;
    }

	public String getRemark() {
        return this.remark;
    }

	public void setRemark(String remark) {
        this.remark = remark;
    }

	public String getOutdoorDateStr() {
        return this.outdoorDateStr;
    }

	public void setOutdoorDateStr(String outdoorDateStr) {
        this.outdoorDateStr = outdoorDateStr;
    }

	public String getOnboardDateStr() {
        return this.onboardDateStr;
    }

	public void setOnboardDateStr(String onboardDateStr) {
        this.onboardDateStr = onboardDateStr;
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

	@PersistenceContext
    transient EntityManager entityManager;

	public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("mainOrder", "quantity", "shipment", "outdoorDate", "onboardDate", "remark", "mainOrderStr", "outdoorDateStr", "onboardDateStr", "person", "recordStatus");

	public static final EntityManager entityManager() {
        EntityManager em = new ShipmentStyle().entityManager;
        if (em == null) throw new IllegalStateException("Entity manager has not been injected (is the Spring Aspects JAR configured as an AJC/AJDT aspects library?)");
        return em;
    }

	@Transactional
    public long countShipmentStyles() {
        return findAllShipmentStyles().size();
    }

	@Transactional
    public List<ShipmentStyle> findAllShipmentStyles() {
        return entityManager().createQuery("SELECT o FROM ShipmentStyle o", ShipmentStyle.class).getResultList();
    }

	@Transactional
    public List<ShipmentStyle> findAllShipmentStyles(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM ShipmentStyle o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager().createQuery(jpaQuery, ShipmentStyle.class).getResultList();
    }

	@Transactional
    public ShipmentStyle findShipmentStyle(Long id) {
        if (id == null) return null;
        return entityManager().find(ShipmentStyle.class, id);
    }

	@Transactional
    public List<ShipmentStyle> findShipmentStyleEntries(int firstResult, int maxResults) {
        return entityManager().createQuery("SELECT o FROM ShipmentStyle o", ShipmentStyle.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

	@Transactional
    public List<ShipmentStyle> findShipmentStyleEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM ShipmentStyle o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager().createQuery(jpaQuery, ShipmentStyle.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist() {
        if (this.entityManager == null) this.entityManager = entityManager();
        this.entityManager.persist(this);
    }

	@Transactional
    public void remove() {
//        if (this.entityManager == null) this.entityManager = entityManager();
//        if (this.entityManager.contains(this)) {
            this.entityManager.remove(this);
//        } else {
//            ShipmentStyle attached = ShipmentStyle.findShipmentStyle(this.id);
//            this.entityManager.remove(attached);
//        }
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
    public ShipmentStyle merge() {
        if (this.entityManager == null) this.entityManager = entityManager();
        ShipmentStyle merged = this.entityManager.merge(this);
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
