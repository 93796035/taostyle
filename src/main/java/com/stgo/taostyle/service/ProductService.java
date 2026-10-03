package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

@Service
public class ProductService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public long countProducts() {
        return entityManager.createQuery("SELECT COUNT(o) FROM Product o", Long.class).getSingleResult();
    }

    @Transactional
    public List<Product> findAllProducts() {
        return entityManager.createQuery("SELECT o FROM Product o", Product.class).getResultList();
    }

    @Transactional
    public Product findProduct(Long id) {
        return entityManager.find(Product.class, id);
    }

    @Transactional
    public List<Product> findProductEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM Product o", Product.class)
                .setFirstResult(firstResult)
                .setMaxResults(maxResults)
                .getResultList();
    }

    @Transactional
    public Product findProductByCatalogAndPerson(String catalog, Person person) {
        TypedQuery<Product> query = entityManager.createQuery(
                "SELECT o FROM Product AS o WHERE o.c1 = :catalog AND o.person = :person ORDER BY o.id DESC",
                Product.class);
        query.setParameter("catalog", catalog);
        query.setParameter("person", person);
        return query.getResultList().stream().findFirst().orElse(null);
    }

    @Transactional
    public Product findProductsByPartNoAndPerson(String partNo, Person person) {
        TypedQuery<Product> query = entityManager.createQuery(
                "SELECT o FROM Product AS o WHERE o.partNo = :partNo AND o.person = :person",
                Product.class);
        query.setParameter("partNo", partNo);
        query.setParameter("person", person);
        return query.getResultList().stream().findFirst().orElse(null);
    }

    @Transactional
    public List<Product> findAllProductsByPerson(Person person) {
        return entityManager.createQuery("SELECT o FROM Product o WHERE o.person = :person ORDER BY o.id", Product.class)
                .setParameter("person", person).getResultList();
    }

    @Transactional
    public Product findByLocalIdAndPerson(Long localId, Person person) {
        TypedQuery<Product> query = entityManager.createQuery(
                "SELECT o FROM Product o WHERE o.id = :id AND o.person = :person", Product.class);
        query.setParameter("id", localId).setParameter("person", person);
        return query.getResultList().stream().findFirst().orElse(null);
    }

    @Transactional
    public boolean existsProductForPerson(Person person) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(o) FROM Product AS o WHERE o.person = :person",
                Long.class);
        query.setParameter("person", person);
        return query.getSingleResult() > 0;
    }

    @Transactional
    public void persist(Product product) {
        entityManager.persist(product);
    }

    @Transactional
    public void remove(Product product) {
        if (entityManager.contains(product)) {
            entityManager.remove(product);
        } else {
            Product attached = findProduct(product.getId());
            entityManager.remove(attached);
        }
    }

    @Transactional
    public Product merge(Product product) {
        return entityManager.merge(product);
    }
}
