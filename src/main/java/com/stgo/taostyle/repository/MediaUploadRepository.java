package com.stgo.taostyle.repository;

import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.web.CC;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

@Repository
public class MediaUploadRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public long countMediaUploadsByKeyAndPerson(String pKey, Person person) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(o) FROM MediaUpload AS o WHERE o.filepath LIKE :pKey and o.person = :person", Long.class);
        query.setParameter("pKey", pKey + "_%");
        query.setParameter("person", person);
        return query.getSingleResult();
    }

    // List all media uploads by key and person
    public List<MediaUpload> listAllMediaUploadsByKeyAndPerson(String pKey, Person person) {
        TypedQuery<MediaUpload> query = entityManager.createQuery(
                "SELECT o FROM MediaUpload AS o WHERE o.filepath LIKE :pKey and o.person = :person", MediaUpload.class);
        query.setParameter("pKey", pKey + "_%");
        query.setParameter("person", person);
        return query.getResultList();
    }

    public List<MediaUpload> listAllMediaUploadsByPositionAndPerson(String pKey, Person person) {
        TypedQuery<MediaUpload> query = entityManager.createQuery(
                "SELECT o FROM MediaUpload AS o WHERE o.filepath LIKE :pKey and o.person = :person", MediaUpload.class);
        query.setParameter("pKey", "%" + pKey + "_%");
        query.setParameter("person", person);
        return query.getResultList();
    }

    public MediaUpload findMediaUpload(Long id) {
        return entityManager.find(MediaUpload.class, id);
    }

    // Find media upload by key and person
    public MediaUpload findMediaByKeyAndPerson(String key, Person person) {
        TypedQuery<MediaUpload> query = entityManager.createQuery(
                "SELECT o FROM MediaUpload AS o WHERE o.filepath = :pKey and o.person = :person ORDER BY o.id DESC", MediaUpload.class);
        query.setParameter("pKey", key);
        query.setParameter("person", person);
        List<MediaUpload> mediaUploads = query.getResultList();
        return mediaUploads.isEmpty() ? null : mediaUploads.get(0);
    }

    public List<MediaUpload> findAllMediaUploads() {
        return entityManager.createQuery("SELECT o FROM MediaUpload o order by person_id", MediaUpload.class).getResultList();
    }

    // Find media uploads by audient and person
    public List<MediaUpload> findMediaByAudientAndPerson(UserAccount usr, Customize customize) {
        TypedQuery<MediaUpload> query;
        if (customize != null && usr.getLoginname().equalsIgnoreCase(customize.getCusValue())) {
            query = entityManager.createQuery(
                    "SELECT o FROM MediaUpload AS o WHERE o.publisher IS NOT NULL ORDER BY o.submitDate DESC", MediaUpload.class);
        } else {
            query = entityManager.createQuery(
                    "SELECT o FROM MediaUpload AS o WHERE o.audient = :audient ORDER BY o.submitDate DESC", MediaUpload.class);
            query.setParameter("audient", usr);
        }
        return query.getResultList();
    }

    public List<String> listAllMediaUploadsKeyByKeyAndPerson(String pKey, Person person) {
        // note "ORDER BY o.filepath" is useless, because _10 will be infront of _2.
        TypedQuery<String> tQuery = entityManager.createQuery(
                "SELECT o.filepath FROM MediaUpload AS o WHERE o.filepath LIKE :pKey and o.person = :person",
                String.class);
        tQuery.setParameter("pKey", pKey + "_%");
        tQuery.setParameter("person", person);
        try {
            return tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }
    }

    // Find media uploads by author
    public List<MediaUpload> findMediaByAuthor(UserAccount usr) {
        TypedQuery<MediaUpload> query = entityManager.createQuery(
                "SELECT o FROM MediaUpload AS o WHERE o.publisher = :publisher ORDER BY o.submitDate DESC", MediaUpload.class);
        query.setParameter("publisher", usr);
        return query.getResultList();
    }

    // Find all media uploads by person
    public List<MediaUpload> findAllMediaUploadByPerson(Person person) {
        TypedQuery<MediaUpload> query = entityManager.createQuery(
                "SELECT o FROM MediaUpload AS o WHERE o.person = :person ORDER BY o.submitDate DESC", MediaUpload.class);
        query.setParameter("person", person);
        return query.getResultList();
    }

    // Persist a media upload
    public void persist(MediaUpload mediaUpload) {
        entityManager.persist(mediaUpload);
    }

    // Remove a media upload
    public void remove(MediaUpload mediaUpload) {
        if (entityManager.contains(mediaUpload)) {
            entityManager.remove(mediaUpload);
        } else {
            MediaUpload attached = entityManager.find(MediaUpload.class, mediaUpload.getId());
            entityManager.remove(attached);
        }
    }

    // Merge a media upload
    public MediaUpload merge(MediaUpload mediaUpload) {
        return entityManager.merge(mediaUpload);
    }

}
