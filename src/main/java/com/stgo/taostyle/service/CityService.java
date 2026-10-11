package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.City;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

@Service
public class CityService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public long countCitys() {
        return findAllCitys().size();
    }

    @Transactional
    public List<City> findAllCitys() {
        return entityManager.createQuery("SELECT o FROM City o", City.class).getResultList();
    }

    @Transactional
    public List<City> findAllCitys(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM City o";
        if (City.fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, City.class).getResultList();
    }

    @Transactional
    public City findCity(Long id) {
        if (id == null) return null;
        return entityManager.find(City.class, id);
    }

    @Transactional
    public List<City> findCityEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM City o", City.class)
                .setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    @Transactional
    public void persist(City city) {
        entityManager.persist(city);
    }

    @Transactional
    public void remove(City city) {
        entityManager.remove(city);
    }

    @Transactional
    public City merge(City city) {
        return entityManager.merge(city);
    }
}
