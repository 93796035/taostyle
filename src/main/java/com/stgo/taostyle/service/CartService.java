package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Cart;
import com.stgo.taostyle.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

@Service
public class CartService {

    public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("product", "quantity", "weight", "unit", "createdDate", "status", "owner", "person", "recordStatus");

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public long countCarts() {
        return findAllCarts().size();
    }

    @Transactional
    public List<Cart> findAllCarts() {
        return entityManager.createQuery("SELECT o FROM Cart o", Cart.class).getResultList();
    }

    @Transactional
    public List<Cart> findCartsByUser(UserAccount userAccount) {
        if (userAccount == null) return null;
        TypedQuery<Cart> tQuery = entityManager.createQuery("SELECT o FROM Cart o WHERE o.owner = :tUserAccount", Cart.class);
        tQuery.setParameter("tUserAccount", userAccount);
        return tQuery.getResultList();
    }

    @Transactional
    public Cart findCart(Long id) {
        if (id == null) return null;
        return entityManager.find(Cart.class, id);
    }

    @Transactional
    public void persist(Cart cart) {
        entityManager.persist(cart);
    }

    @Transactional
    public void remove(Cart cart) {
        entityManager.remove(cart);
    }

    @Transactional
    public Cart merge(Cart cart) {
        return entityManager.merge(cart);
    }

    @Transactional
    public List<Cart> findCartEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM Cart o", Cart.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    @Transactional
    public List<Cart> findCartEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM Cart o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, Cart.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

}
