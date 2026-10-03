package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Feature;
import com.stgo.taostyle.domain.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class FeatureService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public long countFeatures() {
        return entityManager.createQuery("SELECT COUNT(o) FROM Feature o", Long.class).getSingleResult();
    }

    @Transactional
    public List<Feature> findAllFeatures() {
        return entityManager.createQuery("SELECT o FROM Feature o", Feature.class).getResultList();
    }

    @Transactional
    public List<Feature> findAllFeatures(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Feature o";
        if (Feature.fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery += " ORDER BY " + sortFieldName + " " + sortOrder;
        }
        return entityManager.createQuery(jpaQuery, Feature.class).getResultList();
    }

    @Transactional
    public Feature findFeature(Long id) {
        return entityManager.find(Feature.class, id);
    }

    @Transactional
    public List<Feature> findFeatureEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM Feature o", Feature.class)
                .setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    @Transactional
    public List<Feature> findFeatureEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Feature o";
        if (Feature.fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery += " ORDER BY " + sortFieldName + " " + sortOrder;
        }
        return entityManager.createQuery(jpaQuery, Feature.class)
                .setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    @Transactional
    public List<Feature> findAllFeaturesByPerson(Person person) {
        TypedQuery<Feature> tQuery = entityManager.createQuery(
                "SELECT o FROM Feature AS o WHERE o.person = :person", Feature.class);
        tQuery.setParameter("person", person);
        return tQuery.getResultList();
    }

    @Transactional
    public List<String> findAllVisibleServiceKeysByPerson(Person person) {
        List<Feature> features = findAllFeaturesByPerson(person);
        Set<String> keys = new LinkedHashSet<>();
        for (Feature feature : features) {
            String itemsToShow = feature.getItemsToShow();
            if (itemsToShow == null) {
                continue;
            }
            for (String item : itemsToShow.split(",")) {
                if (item == null) {
                    continue;
                }
                String key = item.trim();
                if (key.isEmpty()) {
                    continue;
                }
                if (key.startsWith("service_")) {
                    key = key.substring(8);
                }
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }

    @Transactional
    public List<Feature> findAllFeaturesByMenuIdxAndPerson(String menuIdx, Person person) {
        TypedQuery<Feature> tQuery = entityManager.createQuery(
                "SELECT o FROM Feature AS o WHERE o.menuIdx = :menuIdx and o.person = :person ORDER BY o.id", Feature.class);
        tQuery.setParameter("menuIdx", menuIdx);
        tQuery.setParameter("person", person);
        return tQuery.getResultList();
    }

    @Transactional
    public void persist(Feature feature) {
        entityManager.persist(feature);
    }

    @Transactional
    public void remove(Feature feature) {
        entityManager.remove(feature);
    }

    @Transactional
    public Feature merge(Feature feature) {
        return entityManager.merge(feature);
    }
}
