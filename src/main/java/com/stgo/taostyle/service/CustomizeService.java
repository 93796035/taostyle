package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.web.CC;
import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.Collection;
import java.util.List;

@Service
public class CustomizeService {

    @PersistenceContext
    private EntityManager entityManager;
    public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("cusKey", "cusValue", "person", "recordStatus");

    @Autowired
    private PersonService personService;

    @Transactional
    public Customize findCustomize(Long id) {
        if (id == null) return null;
        return entityManager.find(Customize.class, id);
    }
    // 根据key和Person查找Customize
    @Transactional(readOnly = true)
    public Customize findCustomizeByKeyAndPerson(String cusKey, Person person) {
        TypedQuery<Customize> query = entityManager.createQuery(
                "SELECT o FROM Customize o WHERE o.cusKey = :pCusKey and o.person = :person",
                Customize.class);
        query.setParameter("pCusKey", cusKey);
        query.setParameter("person", person);
        try {
            return query.getSingleResult();
        } catch (Exception e) {
            return null; // 未找到时返回null
        }
    }

    // 根据Person查找所有Customize
    @Transactional(readOnly = true)
    public List<Customize> findAllCustomizesByPerson(Person person) {
        TypedQuery<Customize> query = entityManager.createQuery(
                "SELECT o FROM Customize o WHERE o.person = :person ORDER BY o.cusKey",
                Customize.class);
        query.setParameter("person", person);
        return query.getResultList();
    }

    // 获取appWebsiteMap静态值
    private String appWebsiteMap;

    @Transactional(readOnly = true)
    public String getAppWebsiteMap() {
        if (appWebsiteMap == null) {
            appWebsiteMap = findCustomizeByKeyAndPerson(CC.app_Website_map, personService.getAdam()).getCusValue();
        }
        return appWebsiteMap;
    }

    // 获取knownRobotNames静态值
    private String knownRobotNames;

    @Transactional(readOnly = true)
    public String getKnownRobotNames() {
        if (knownRobotNames == null) {
            knownRobotNames = findCustomizeByKeyAndPerson(CC.known_robot_names, personService.getAdam()).getCusValue();
        }
        return knownRobotNames;
    }

    // 清空缓存
    @Transactional
    public void clearCache() {
        appWebsiteMap = null;
        knownRobotNames = null;
    }

    @Transactional
    public void persist(Customize customize) {
        entityManager.persist(customize);
        if (CC.app_Website_map.equals(customize.getCusKey())) {
            appWebsiteMap = null;
        } else if (CC.known_robot_names.equals(customize.getCusKey())) {
            knownRobotNames = null;
        }
    }

    @Transactional
    public Customize merge(Customize customize) {
        return entityManager.merge(customize);
    }

    @Transactional
    public void remove(Customize customize) {
        if (entityManager.contains(customize)) {
            entityManager.remove(customize);
        } else {
            Customize attached = entityManager.find(Customize.class, customize.getId());
            entityManager.remove(attached);
        }
    }
    @Transactional
    public List<Customize> findOrderedCustomizeEntriesByPerson(
            int firstResult,
            int maxResults,
            String sortFieldName,
            String sortOrder,
            Person person) {
        String jpaQuery = "SELECT o FROM Customize o where o.person = :person";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        TypedQuery<Customize> query = entityManager.createQuery(jpaQuery, Customize.class);
        query.setFirstResult(firstResult);
        query.setMaxResults(maxResults);
        query.setParameter("person", person);
        return query.getResultList();
    }

    @Transactional
    public long countCustomizesByPerson(
            Person person) {
        return findAllCustomizesByPerson(person).size();
    }

    @Transactional
    public List<Customize> findAllOrderedCustomizesByPerson(
            String sortFieldName,
            String sortOrder,
            Person person) {
        String jpaQuery = "SELECT o FROM Customize o where o.person = :person";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        TypedQuery<Customize> query = entityManager.createQuery(jpaQuery, Customize.class);
        query.setParameter("person", person);
        return query.getResultList();
    }
    public String toJson(Customize customize) {
        return new JSONSerializer()
                .exclude("*.class").serialize(customize);
    }

    public String toJson(String[] fields, Customize customize) {
        return new JSONSerializer()
                .include(fields).exclude("*.class").serialize(customize);
    }

    public static Customize fromJsonToCustomize(String json) {
        return new JSONDeserializer<Customize>()
                .use(null, Customize.class).deserialize(json);
    }

    public static String toJsonArray(Collection<Customize> collection) {
        return new JSONSerializer()
                .exclude("*.class").serialize(collection);
    }

    public static String toJsonArray(Collection<Customize> collection, String[] fields) {
        return new JSONSerializer()
                .include(fields).exclude("*.class").serialize(collection);
    }
    public static Collection<Customize> fromJsonArrayToCustomizes(String json) {
        return new JSONDeserializer<List<Customize>>()
                .use("values", Customize.class).deserialize(json);
    }
}
