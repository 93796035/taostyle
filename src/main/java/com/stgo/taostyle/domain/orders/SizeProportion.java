package com.stgo.taostyle.domain.orders;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.PersistenceContext;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stgo.taostyle.domain.Person;

@Entity


public class SizeProportion {

    @NotNull
    @ManyToOne
    private MainOrder mainOrder;

    private String Color;

    private int smallsize;

    private int middlesize;

    private int largeSize;

    private int extraLargeSize;

    private int other1;

    private int other2;

    private int other3;

    private int other4;

    private int total;

    private String Remark;

    public static List<SizeProportion> findAllSizeProportionsByMainOrder(
            Long mainOrderId) {
//        MainOrder tMainOrder = MainOrder.findMainOrder(mainOrderId);
//        if (tMainOrder == null) {
            return entityManager().createQuery("SELECT o FROM SizeProportion o", SizeProportion.class).getResultList();
//        } else {
//            EntityManager tEntityManager = entityManager();
//            TypedQuery<SizeProportion> tQuery =
//                    tEntityManager.createQuery("SELECT o FROM SizeProportion AS o WHERE o.mainOrder = :pKey",
//                            SizeProportion.class);
//            tQuery = tQuery.setParameter("pKey", tMainOrder);
//            List<SizeProportion> sizeProportions = new ArrayList<SizeProportion>();
//            try {
//                sizeProportions = tQuery.getResultList();
//            } catch (Exception e) {
//            }
//            return sizeProportions;
//        }
    }

    public static List<SizeProportion> findSizeProportionEntriesByMainOrder(
            Long mainOrderId,
            int firstResult,
            int maxResults) {
//        MainOrder tMainOrder = MainOrder.findMainOrder(mainOrderId);
//        if (tMainOrder == null) {
            return entityManager().createQuery("SELECT o FROM SizeProportion o", SizeProportion.class)
                    .setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
//        } else {
//            EntityManager tEntityManager = entityManager();
//            TypedQuery<SizeProportion> tQuery =
//                    tEntityManager.createQuery("SELECT o FROM SizeProportion AS o WHERE o.mainOrder = :pKey",
//                            SizeProportion.class);
//            tQuery = tQuery.setParameter("pKey", tMainOrder);
//            if (firstResult > -1 && maxResults > 0)
//                tQuery = tQuery.setFirstResult(firstResult).setMaxResults(maxResults);
//            // to make sure not return a null;
//            List<SizeProportion> sizeProportions = new ArrayList<SizeProportion>();
//            try {
//                sizeProportions = tQuery.getResultList();
//            } catch (Exception e) {
//            }
//            return sizeProportions;
//        }
    }

    /**
     */
    @ManyToOne
    private Person person;

    /**
     */
    private int recordStatus;

	public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
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

	@PersistenceContext
    transient EntityManager entityManager;

	public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("mainOrder", "Color", "smallsize", "middlesize", "largeSize", "extraLargeSize", "other1", "other2", "other3", "other4", "total", "Remark", "person", "recordStatus");

	public static final EntityManager entityManager() {
        EntityManager em = new SizeProportion().entityManager;
        if (em == null) throw new IllegalStateException("Entity manager has not been injected (is the Spring Aspects JAR configured as an AJC/AJDT aspects library?)");
        return em;
    }

	@Transactional
    public long countSizeProportions() {
        return findAllSizeProportions().size();
    }

	@Transactional
    public List<SizeProportion> findAllSizeProportions() {
        return entityManager().createQuery("SELECT o FROM SizeProportion o", SizeProportion.class).getResultList();
    }

	@Transactional
    public List<SizeProportion> findAllSizeProportions(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM SizeProportion o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager().createQuery(jpaQuery, SizeProportion.class).getResultList();
    }

	@Transactional
    public SizeProportion findSizeProportion(Long id) {
        if (id == null) return null;
        return entityManager().find(SizeProportion.class, id);
    }

	@Transactional
    public List<SizeProportion> findSizeProportionEntries(int firstResult, int maxResults) {
        return entityManager().createQuery("SELECT o FROM SizeProportion o", SizeProportion.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

	@Transactional
    public List<SizeProportion> findSizeProportionEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM SizeProportion o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager().createQuery(jpaQuery, SizeProportion.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
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
//            SizeProportion attached = SizeProportion.findSizeProportion(this.id);
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
    public SizeProportion merge() {
        if (this.entityManager == null) this.entityManager = entityManager();
        SizeProportion merged = this.entityManager.merge(this);
        this.entityManager.flush();
        return merged;
    }

	public MainOrder getMainOrder() {
        return this.mainOrder;
    }

	public void setMainOrder(MainOrder mainOrder) {
        this.mainOrder = mainOrder;
    }

	public String getColor() {
        return this.Color;
    }

	public void setColor(String Color) {
        this.Color = Color;
    }

	public int getSmallsize() {
        return this.smallsize;
    }

	public void setSmallsize(int smallsize) {
        this.smallsize = smallsize;
    }

	public int getMiddlesize() {
        return this.middlesize;
    }

	public void setMiddlesize(int middlesize) {
        this.middlesize = middlesize;
    }

	public int getLargeSize() {
        return this.largeSize;
    }

	public void setLargeSize(int largeSize) {
        this.largeSize = largeSize;
    }

	public int getExtraLargeSize() {
        return this.extraLargeSize;
    }

	public void setExtraLargeSize(int extraLargeSize) {
        this.extraLargeSize = extraLargeSize;
    }

	public int getOther1() {
        return this.other1;
    }

	public void setOther1(int other1) {
        this.other1 = other1;
    }

	public int getOther2() {
        return this.other2;
    }

	public void setOther2(int other2) {
        this.other2 = other2;
    }

	public int getOther3() {
        return this.other3;
    }

	public void setOther3(int other3) {
        this.other3 = other3;
    }

	public int getOther4() {
        return this.other4;
    }

	public void setOther4(int other4) {
        this.other4 = other4;
    }

	public int getTotal() {
        return this.total;
    }

	public void setTotal(int total) {
        this.total = total;
    }

	public String getRemark() {
        return this.Remark;
    }

	public void setRemark(String Remark) {
        this.Remark = Remark;
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
}
