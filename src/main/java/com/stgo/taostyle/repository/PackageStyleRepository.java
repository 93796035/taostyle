package com.stgo.taostyle.repository;

import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.PackageStyle;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PackageStyleRepository {
    @PersistenceContext
    transient EntityManager entityManager;

    public final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("mainOrder", "itemName", "qualitySpecification", "sizeSpecification", "color", "quantity", "location", "remark", "person", "recordStatus");
    
    public List<PackageStyle> findAllPackageStylesByMainOrder(MainOrder mainOrder) {
        EntityManager tEntityManager = entityManager;
        TypedQuery<PackageStyle> tQuery =
                tEntityManager.createQuery("SELECT o FROM PackageStyle AS o WHERE o.mainOrder = :pKey",
                        PackageStyle.class);
        tQuery = tQuery.setParameter("pKey", mainOrder);
        List<PackageStyle> packageStyles = new ArrayList<PackageStyle>();
        try {
            packageStyles = tQuery.getResultList();
        } catch (Exception e) {
        }
        return packageStyles;
    }

    public List<PackageStyle> findPackageStyleEntriesByMainOrder(
            MainOrder tMainOrder,
            int firstResult,
            int maxResults) {
        if (tMainOrder == null) {
            return entityManager.createQuery("SELECT o FROM PackageStyle o", PackageStyle.class)
                    .setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
        } else {
            EntityManager tEntityManager = entityManager;
            TypedQuery<PackageStyle> tQuery =
                    tEntityManager.createQuery("SELECT o FROM PackageStyle AS o WHERE o.mainOrder = :pKey",
                            PackageStyle.class);
            tQuery = tQuery.setParameter("pKey", tMainOrder);
            if (firstResult > -1 && maxResults > 0)
                tQuery = tQuery.setFirstResult(firstResult).setMaxResults(maxResults);
            // to make sure not return a null;
            List<PackageStyle> packageStyles = new ArrayList<PackageStyle>();
            try {
                packageStyles = tQuery.getResultList();
            } catch (Exception e) {
            }
            return packageStyles;
        }
    }

    public long countPackageStyles() {
        return findAllPackageStyles().size();
    }

    
    public List<PackageStyle> findAllPackageStyles() {
        return entityManager.createQuery("SELECT o FROM PackageStyle o", PackageStyle.class).getResultList();
    }

    
    public List<PackageStyle> findAllPackageStyles(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM PackageStyle o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, PackageStyle.class).getResultList();
    }

    
    public PackageStyle findPackageStyle(Long id) {
        if (id == null) return null;
        return entityManager.find(PackageStyle.class, id);
    }

    
    public List<PackageStyle> findPackageStyleEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM PackageStyle o", PackageStyle.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    
    public List<PackageStyle> findPackageStyleEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM PackageStyle o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, PackageStyle.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    public void persist(PackageStyle packageStyle) {
        this.entityManager.persist(packageStyle);
    }

    
    public void remove(PackageStyle packageStyle) {
        if (this.entityManager.contains(this)) {
            this.entityManager.remove(packageStyle);
        } else {
            PackageStyle attached = findPackageStyle(packageStyle.getId());
            this.entityManager.remove(attached);
        }
    }

    
    public void flush() {
        if (this.entityManager == null) this.entityManager = entityManager;
        this.entityManager.flush();
    }

    
    public void clear() {
        this.entityManager.clear();
    }

    
    public PackageStyle merge(PackageStyle packageStyle) {
        PackageStyle merged = this.entityManager.merge(packageStyle);
        this.entityManager.flush();
        return merged;
    }

}
