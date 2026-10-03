package com.stgo.taostyle.repository;

import com.stgo.taostyle.backend.security.TaoEncrypt;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.Material;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MaterialRepository {
    
    public final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("mainOrder", "portionName", "endSubTotal", "MenFu", "color", "location", "remark", "person", "recordStatus");

    @PersistenceContext
    transient EntityManager entityManager;

    public List<Material> findAllMaterialsByPersonAndStatus(Person person, int status) {
        List<Material> materials = new ArrayList<Material>();
        TypedQuery<Material> tQuery = entityManager.createQuery(
                "SELECT o FROM Material AS o WHERE o.person = :person and o.recordStatus = :recordStatus and o.mainOrder is NULL ",
                Material.class);
        tQuery = tQuery.setParameter("person", person);
        tQuery = tQuery.setParameter("recordStatus", status);
        try {
            materials = tQuery.getResultList();
        } catch (Exception e) {
        }
        return materials;
    }

    public List<Material> findAllMaterialsByMainOrderIdAndPrinter(
            MainOrder mainOrder,
            UserAccount printer) {
        
        List<Material> materials = new ArrayList<Material>();
        if (mainOrder != null) {
            TypedQuery<Material> tQuery = entityManager.createQuery(
                    "SELECT o FROM Material AS o WHERE o.mainOrder = :mainOrder and o.MenFu like :printer",
                    Material.class);
            tQuery = tQuery.setParameter("mainOrder", mainOrder);
            tQuery = tQuery.setParameter("printer",
                    printer != null ? "%," + TaoEncrypt.stripUserName(printer.getLoginname()) + ",%" : "");
            try {
                materials = tQuery.getResultList();
            } catch (Exception e) {
            }
        }
        return materials;
    }

    public List<Material> findMaterialEntriesByMainOrderId(
            MainOrder tMainOrder,
            int firstResult,
            int maxResults) {
        TypedQuery<Material> tQuery =
                entityManager.createQuery("SELECT o FROM Material AS o WHERE o.mainOrder = :pKey", Material.class);
        tQuery = tQuery.setParameter("pKey", tMainOrder);
        if (firstResult > -1 && maxResults > 0)
            tQuery = tQuery.setFirstResult(firstResult).setMaxResults(maxResults);
        // to make sure not return a null;
        List<Material> materials = new ArrayList<Material>();
        try {
            materials = tQuery.getResultList();
        } catch (Exception e) {
        }
        return materials;
    }

    public List<Material> findAllMaterialsByMainOrder(MainOrder mainOrder) {
        
        List<Material> materials = new ArrayList<Material>();
        if (mainOrder != null) {
            TypedQuery<Material> tQuery = entityManager.createQuery(
                    "SELECT o FROM Material AS o WHERE o.mainOrder = :pKey ORDER BY o.location", Material.class);
            tQuery = tQuery.setParameter("pKey", mainOrder);
            try {
                materials = tQuery.getResultList();
            } catch (Exception e) {
            }
        }
        return materials;
    }



  
    public long countMaterials() {
        return findAllMaterials().size();
    }

  
    public List<Material> findAllMaterials() {
        return entityManager.createQuery("SELECT o FROM Material o", Material.class).getResultList();
    }

  
    public List<Material> findAllMaterials(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Material o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, Material.class).getResultList();
    }

  
    public Material findMaterial(Long id) {
        if (id == null) return null;
        return entityManager.find(Material.class, id);
    }

  
    public List<Material> findMaterialEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM Material o", Material.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

  
    public List<Material> findMaterialEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Material o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, Material.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    public void persist(Material material) {
        this.entityManager.persist(material);
    }

  
    public void remove(Material material) {
        if (this.entityManager.contains(material)) {
            this.entityManager.remove(material);
        } else {
            Material attached = findMaterial(material.getId());
            this.entityManager.remove(attached);
        }
    }

  
    public void flush() {
        this.entityManager.flush();
    }

  
    public void clear() {
        this.entityManager.clear();
    }

  
    public Material merge(Material material) {
        Material merged = this.entityManager.merge(material);
        this.entityManager.flush();
        return merged;
    }
}
