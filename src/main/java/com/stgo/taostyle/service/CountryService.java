package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Country;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

@Service
public class CountryService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public long countCountries() {
        return entityManager.createQuery("SELECT COUNT(o) FROM Country o", Long.class).getSingleResult();
    }

    @Transactional
    public List<Country> findAllCountries() {
        return entityManager.createQuery("SELECT o FROM Country o", Country.class).getResultList();
    }

    @Transactional
    public List<Country> findAllCountries(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Country o";
        if (Country.fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery += " ORDER BY " + sortFieldName + " " + sortOrder;
        }
        return entityManager.createQuery(jpaQuery, Country.class).getResultList();
    }

    @Transactional
    public Country findCountry(Long id) {
        return entityManager.find(Country.class, id);
    }

    @Transactional
    public List<Country> findCountryEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM Country o", Country.class)
                .setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    @Transactional
    public List<Country> findCountryEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Country o";
        if (Country.fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery += " ORDER BY " + sortFieldName + " " + sortOrder;
        }
        return entityManager.createQuery(jpaQuery, Country.class)
                .setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    @Transactional
    public void persist(Country country) {
        entityManager.persist(country);
    }

    @Transactional
    public void remove(Country country) {
        entityManager.remove(country);
    }

    @Transactional
    public Country merge(Country country) {
        return entityManager.merge(country);
    }
}
