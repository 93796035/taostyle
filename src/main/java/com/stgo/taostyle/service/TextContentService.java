package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.TextContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

@Service
public class TextContentService {
    public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("posInPage", "content", "publisher", "markDate", "person", "recordStatus");

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public TextContent findTextContent(Long id) {
        if (id == null) return null;
        return entityManager.find(TextContent.class, id);
    }

    public TextContent findContentsByKeyAndPerson(String pKey, Person person) {
        TypedQuery<TextContent> tQuery = entityManager.createQuery(
                "SELECT o FROM TextContent AS o WHERE o.posInPage = :pKey and o.person = :person ORDER BY o.markDate DESC, o.id DESC",
                TextContent.class);
        tQuery.setParameter("pKey", pKey);
        tQuery.setParameter("person", person);
        tQuery.setMaxResults(1);
        try {
            List<TextContent> results = tQuery.getResultList();
            return results == null || results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public String findTextByKeyAndPerson(String pKey, Person person) {
        TypedQuery<String> tQuery = entityManager.createQuery(
                "SELECT o.content FROM TextContent AS o WHERE o.posInPage = :pKey and o.person = :person", String.class);
        tQuery.setParameter("pKey", pKey);
        tQuery.setParameter("person", person);
        try {
            return tQuery.getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    public List<TextContent> findContentsByMediaIdAndPerson(String pKey, Person person) {
        TypedQuery<TextContent> tQuery = entityManager.createQuery(
                "SELECT o FROM TextContent AS o WHERE o.posInPage = :pKey and o.person = :person ORDER BY o.markDate DESC",
                TextContent.class);
        tQuery.setParameter("pKey", pKey);
        tQuery.setParameter("person", person);
        try {
            return tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }
    }

    public List<TextContent> findAllMatchedTextContents(String pKey, String sorter, Person person) {
        if (sorter == null) {
            sorter = " ORDER BY o.markDate DESC";
        } else {
            sorter = " ORDER BY o." + sorter;
        }

        TypedQuery<TextContent> tQuery = entityManager.createQuery(
                "SELECT o FROM TextContent AS o WHERE o.posInPage LIKE :pKey and o.person = :person" + sorter,
                TextContent.class);
        tQuery.setParameter("pKey", pKey);
        tQuery.setParameter("person", person);
        try {
            return tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }
    }

    public List<TextContent> findAllMatchedTextContents(
            String pKey,
            Person person) {
        EntityManager tEntityManager = entityManager;

        TypedQuery<TextContent> tQuery =
                tEntityManager.createQuery(
                        "SELECT o FROM TextContent AS o WHERE o.posInPage LIKE :pKey and o.person = :person",
                        TextContent.class);
        tQuery = tQuery.setParameter("pKey", pKey);
        tQuery = tQuery.setParameter("person", person);
        List<TextContent> tObeFR;
        try {
            tObeFR = tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }
        return tObeFR;
    }

    @Transactional
    public void persist(TextContent textContent) {
        entityManager.persist(textContent);
    }

    @Transactional
    public TextContent merge(TextContent textContent) {
        return entityManager.merge(textContent);
    }

    @Transactional
    public void remove(TextContent textContent) {
        entityManager.remove(textContent);
    }

    public List<String> findAllMatchedContent(
            String pKey,
            String field,
            Person person) {
        EntityManager tEntityManager = entityManager;
        TypedQuery<String> tQuery =
                tEntityManager
                        .createQuery(
                                "SELECT o."
                                        + field
                                        + " FROM TextContent AS o WHERE o.posInPage LIKE :pKey and o.person = :person ORDER BY o.posInPage",
                                String.class);
        tQuery = tQuery.setParameter("pKey", pKey + "%");
        tQuery = tQuery.setParameter("person", person);
        List<String> tObeFR;
        try {
            tObeFR = tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }
        return tObeFR;
    }

    @Transactional
    public long countTextContents() {
        return findAllTextContents().size();
    }

    @Transactional
    public List<TextContent> findAllTextContents() {
        return entityManager.createQuery("SELECT o FROM TextContent o", TextContent.class).getResultList();
    }

    @Transactional
    public List<TextContent> findTextContentEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM TextContent o", TextContent.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    @Transactional
    public List<TextContent> findTextContentEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM TextContent o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, TextContent.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }
}


