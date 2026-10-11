package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Person;
import org.springframework.stereotype.Service;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class PersonService {

    public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("name", "password", "recordStatus");

    @PersistenceContext
    private EntityManager entityManager;
    private static Person adam;
    private static Person eve;

    public Person getAdam() {
        if(adam != null) {
            return adam;
        }
        TypedQuery<Person> tQuery =
                entityManager.createQuery("SELECT o FROM Person AS o WHERE o.id = 1", Person.class);
        try {
            return tQuery.getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }
    public Person getEve() {
        if(eve != null) {
            return eve;
        }
        TypedQuery<Person> tQuery =
                entityManager.createQuery("SELECT o FROM Person AS o WHERE o.id = 2", Person.class);
        try {
            return tQuery.getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public Person findPerson(Long id) {
        if (id == null) return null;
        return entityManager.find(Person.class, id);
    }

    public Person findPersonByName(String personName) {
        TypedQuery<Person> query = entityManager.createQuery("SELECT o FROM Person o WHERE o.name = :pName", Person.class);
        query.setParameter("pName", personName);
        try {
            return query.getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void persist(Person person) {
        entityManager.persist(person);
    }

    @Transactional
    public Person persistWithSpecifiedId(Long id, String name, String password, int recordStatus) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        entityManager.createNativeQuery(
                        "INSERT INTO Person (id, version, name, password, recordStatus) VALUES (?,?,?,?,?)")
                .setParameter(1, id)
                .setParameter(2, 0)
                .setParameter(3, name)
                .setParameter(4, password)
                .setParameter(5, recordStatus)
                .executeUpdate();
        entityManager.flush();
        return findPerson(id);
    }

    @Transactional
    public void remove(Person person) {
        if (entityManager.contains(person)) {
            entityManager.remove(person);
        } else {
            Person attached = findPerson(person.getId());
            entityManager.remove(attached);
        }
    }

    @Transactional
    public void flush() {
        entityManager.flush();
    }

    @Transactional
    public void clear() {
        entityManager.clear();
    }

    @Transactional
    public Person merge(Person person) {
        Person merged = entityManager.merge(person);
        entityManager.flush();
        return merged;
    }

    @Transactional
    public long countPeople() {
        return findAllPeople().size();
    }

    @Transactional
    public List<Person> findAllPeople() {
        return entityManager.createQuery("SELECT o FROM Person o", Person.class).getResultList();
    }
    @Transactional
    public List<Person> findAllPeople(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Person o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, Person.class).getResultList();
    }

    @Transactional
    public List<Person> findPersonEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM Person o", Person.class)
                .setFirstResult(firstResult)
                .setMaxResults(maxResults)
                .getResultList();
    }

    public List<String> getFieldNamesForSorting() {
        return Arrays.asList("name", "password", "recordStatus");
    }

    @Transactional
    public List<Person> findPersonEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Person o";
        if (getFieldNamesForSorting().contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName + " " + sortOrder;
        }
        return entityManager.createQuery(jpaQuery, Person.class)
                .setFirstResult(firstResult)
                .setMaxResults(maxResults)
                .getResultList();
    }
}
