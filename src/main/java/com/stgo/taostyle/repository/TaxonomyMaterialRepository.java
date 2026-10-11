package com.stgo.taostyle.repository;

import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.TaxonomyMaterial;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

@Repository
public class TaxonomyMaterialRepository {
    @PersistenceContext
    transient EntityManager entityManager;

    public final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("mainOrder", "itemName", "quality", "specification", "color", "quantity", "location", "remark", "person", "recordStatus", "logtime");

    public List<TaxonomyMaterial> findTaxonomyMaterialByLocation(
            String location) {
        TypedQuery<TaxonomyMaterial> tQuery = entityManager.createQuery(
                "SELECT o FROM TaxonomyMaterial AS o WHERE o.location = :location order by o.id desc",
                TaxonomyMaterial.class);
        tQuery = tQuery.setParameter("location", location);
        List<TaxonomyMaterial> taxonomyMaterials = null;
        try {
            taxonomyMaterials = tQuery.getResultList();
        } catch (Exception e) {
        }
        return taxonomyMaterials;
    }

    public List<TaxonomyMaterial> findAllTaxonomyMaterialsByMainOrder(
        MainOrder mainOrder) {
        TypedQuery<TaxonomyMaterial> query = entityManager.createQuery(
                "SELECT o FROM TaxonomyMaterial AS o WHERE o.mainOrder = :pKey", TaxonomyMaterial.class);
        query = query.setParameter("pKey", mainOrder);
        List<TaxonomyMaterial> taxonomyMaterials = new ArrayList<TaxonomyMaterial>();
        try {
            taxonomyMaterials = query.getResultList();
        } catch (Exception e) {
        }
        return taxonomyMaterials;
    }

    public List<TaxonomyMaterial> findAllTaxonomyMaterialsByMainOrderID(
            MainOrder mainOrder) {
        return findAllTaxonomyMaterialsByMainOrder(mainOrder);
    }

    public List<TaxonomyMaterial> findTaxonomyMaterialEntriesByMainOrder(MainOrder tMainOrder, int firstResult, int maxResults) {
        TypedQuery<TaxonomyMaterial> tQuery = entityManager.createQuery(
                "SELECT o FROM TaxonomyMaterial AS o WHERE o.mainOrder = :pKey", TaxonomyMaterial.class);
        tQuery = tQuery.setParameter("pKey", tMainOrder);
        if (firstResult > -1 && maxResults > 0)
            tQuery = tQuery.setFirstResult(firstResult).setMaxResults(maxResults);
        // to make sure not return a null;
        List<TaxonomyMaterial> taxonomyMaterials = new ArrayList<TaxonomyMaterial>();
        try {
            taxonomyMaterials = tQuery.getResultList();
        } catch (Exception e) {
        }
        return taxonomyMaterials;
    }
    
    public long countTaxonomyMaterials() {
        return findAllTaxonomyMaterials().size();
    }

    
    public List<TaxonomyMaterial> findAllTaxonomyMaterials() {
        return entityManager.createQuery("SELECT o FROM TaxonomyMaterial o", TaxonomyMaterial.class).getResultList();
    }

    
    public List<TaxonomyMaterial> findAllTaxonomyMaterials(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM TaxonomyMaterial o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, TaxonomyMaterial.class).getResultList();
    }

    
    public TaxonomyMaterial findTaxonomyMaterial(Long id) {
        if (id == null) return null;
        return entityManager.find(TaxonomyMaterial.class, id);
    }

    
    public List<TaxonomyMaterial> findTaxonomyMaterialEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM TaxonomyMaterial o", TaxonomyMaterial.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    
    public List<TaxonomyMaterial> findTaxonomyMaterialEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM TaxonomyMaterial o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, TaxonomyMaterial.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    public void persist(TaxonomyMaterial taxonomyMaterial) {
        this.entityManager.persist(taxonomyMaterial);
    }

    
    public void remove(TaxonomyMaterial taxonomyMaterial) {
        if (this.entityManager.contains(taxonomyMaterial)) {
            this.entityManager.remove(taxonomyMaterial);
        } else {
            TaxonomyMaterial attached = findTaxonomyMaterial(taxonomyMaterial.getId());
            this.entityManager.remove(attached);
        }
    }

    
    public void flush() {
        this.entityManager.flush();
    }

    
    public void clear() {
        this.entityManager.clear();
    }

    
    public TaxonomyMaterial merge(TaxonomyMaterial taxonomyMaterial) {
        TaxonomyMaterial merged = this.entityManager.merge(taxonomyMaterial);
        this.entityManager.flush();
        return merged;
    }

}
