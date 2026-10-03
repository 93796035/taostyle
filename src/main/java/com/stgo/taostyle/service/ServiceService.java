package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.Service;
import com.stgo.taostyle.web.CC;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Service
public class ServiceService {

    private static volatile boolean extendedSchemaEnsured = false;

    @PersistenceContext
    private EntityManager entityManager;

    public Service findService(Long id) {
        ensureExtendedSchema();
        if (id == null) return null;
        return entityManager.find(Service.class, id);
    }

    @Transactional
    public Service findServiceByCatalogAndPerson(String catalog, Person person) {
        ensureExtendedSchema();
        if (catalog.startsWith("service_")) {
            catalog = catalog.substring(8);
        }
        TypedQuery<Service> query = entityManager.createQuery(
                "SELECT o FROM Service AS o WHERE o.c1 = :catalog AND o.person = :person ORDER BY o.id DESC",
                Service.class);
        query.setParameter("catalog", catalog);
        query.setParameter("person", person);
        return query.getResultList().stream().findFirst().orElse(null);
    }

    @Transactional
    public List<Service> findServiceByPerson(Person person) {
        ensureExtendedSchema();
        TypedQuery<Service> query = entityManager.createQuery(
                "SELECT o FROM Service AS o WHERE o.person = :person ORDER BY o.id DESC",
                Service.class);
        query.setParameter("person", person);
        return query.getResultList();
    }


    @Transactional
    public List<Service> searchServicesByBarcodeOrMnemonic(String search, Person person) {
        ensureExtendedSchema();
        if (person == null || search == null || search.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String normalizedSearch = "%" + search.trim().toLowerCase() + "%";
        TypedQuery<Service> query = entityManager.createQuery(
                "SELECT o FROM Service AS o WHERE o.person = :person " +
                        "AND (LOWER(COALESCE(o.c2, '')) LIKE :search OR LOWER(COALESCE(o.name, '')) LIKE :search) " +
                        "ORDER BY o.recordStatus, o.id DESC",
                Service.class);
        query.setParameter("person", person);
        query.setParameter("search", normalizedSearch);
        return query.getResultList();
    }
    @Transactional
    public long countServices() {
        ensureExtendedSchema();
        return entityManager.createQuery("SELECT COUNT(o) FROM Service o", Long.class).getSingleResult();
    }

    @Transactional
    public void saveService(Service service) {
        ensureExtendedSchema();
        if (service.getId() == null) {
            entityManager.persist(service);
        } else {
            entityManager.merge(service);
        }
    }

    @Transactional
    public void deleteService(Service service) {
        ensureExtendedSchema();
        if (entityManager.contains(service)) {
            entityManager.remove(service);
        } else {
            Service attached = entityManager.find(Service.class, service.getId());
            entityManager.remove(attached);
        }
    }
    @Transactional
    public List<Service> findAllMatchedServices(String pKey, Person person) {
        ensureExtendedSchema();
        TypedQuery<Service> query = entityManager.createQuery(
                "SELECT o FROM Service AS o WHERE o.c1 LIKE :pKey AND o.person = :person ORDER BY o.recordStatus",
                Service.class);
        query.setParameter("pKey", pKey);
        query.setParameter("person", person);
        return query.getResultList();
    }

    //注意这里的返回的key不是排序的，_10会在_2前面
    public List<String> listAllServiceKeyByKeyAndPerson(String pKey, Person person) {
        ensureExtendedSchema();
        // note "ORDER BY o.filepath" is useless, because _10 will be infront of _2.
        TypedQuery<String> tQuery = entityManager.createQuery(
                "SELECT o.c1 FROM Service AS o WHERE o.c1 LIKE :pKey and o.person = :person",	String.class);
        if(!pKey.endsWith("_0")) {
            pKey = pKey + "_0";
        }
        if(pKey.toUpperCase().startsWith(CC.SERVICE)) {
            pKey = pKey.substring(8);
        }
        tQuery.setParameter("pKey", pKey + "_%");
        tQuery.setParameter("person", person);
        try {
            return tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }
    }

    public long countServicesByKeyAndPerson(String key, Person person) {
        ensureExtendedSchema();
        EntityManager tEntityManager = entityManager;
        TypedQuery<Long> tQuery = tEntityManager.createQuery(
                "SELECT COUNT(o) FROM Service AS o WHERE o.c1 LIKE :pKey and o.person = :person", Long.class);
        if(key.startsWith("service_")) {
            key = key.substring(8);
        }
        tQuery.setParameter("pKey", key + "_%");
        tQuery.setParameter("person", person);
        long tCount = 0;
        try {
            tCount = tQuery.getSingleResult();
        } catch (Exception e) {
            tCount = 0;
        }
        return tCount;
    }
    @Transactional
    public void persist(Service service) {
        ensureExtendedSchema();
        entityManager.persist(service);
    }

    @Transactional
    public Service merge(Service service) {
        ensureExtendedSchema();
        Service merged = entityManager.merge(service);
        this.entityManager.flush();
        return merged;
    }

    public List<Service> findServiceEntries(int firstResult, int maxResults) {
        ensureExtendedSchema();
        return entityManager.createQuery("SELECT o FROM Service o", Service.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }
    public List<Service> findAllServices() {
        ensureExtendedSchema();
        return entityManager.createQuery("SELECT o FROM Service o", Service.class).getResultList();
    }

    @Transactional
    public void ensureExtendedSchema() {
        if (extendedSchemaEnsured) {
            return;
        }
        synchronized (ServiceService.class) {
            if (extendedSchemaEnsured) {
                return;
            }
            ensureColumnExists("price", "ALTER TABLE Service ADD COLUMN price INT");
            ensureColumnExists("cost", "ALTER TABLE Service ADD COLUMN cost INT");
            ensureColumnExists("stock", "ALTER TABLE Service ADD COLUMN stock INT");
            ensureColumnExists("weeklySalesQty", "ALTER TABLE Service ADD COLUMN weeklySalesQty INT");
            ensureColumnExists("monthlySalesQty", "ALTER TABLE Service ADD COLUMN monthlySalesQty INT");
            extendedSchemaEnsured = true;
        }
    }

    private void ensureColumnExists(String columnName, String alterSql) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                                "WHERE table_schema = DATABASE() AND table_name = 'Service' AND column_name = ?")
                .setParameter(1, columnName)
                .getSingleResult();
        if (count != null && count.intValue() == 0) {
            entityManager.createNativeQuery(alterSql).executeUpdate();
        }
    }

    @Transactional
    public int updateRemotePosProduct(String barcode, Person person, String name, Integer price, Integer cost, Integer stock,
                                      Integer weeklySalesQty) {
        if (person == null || barcode == null || barcode.trim().isEmpty()) {
            return 0;
        }

        String normalizedBarcode = barcode.trim();
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (name != null && !name.trim().isEmpty()) {
            String trimmedName = name.trim();
            assignments.add("mnemonic = ?");
            params.add(trimmedName);
            assignments.add("subject = ?");
            params.add(trimmedName);
        }
        if (price != null) {
            assignments.add("price = ?");
            params.add(price);
        }
        if (cost != null) {
            assignments.add("cost = ?");
            params.add(cost);
        }
        if (stock != null) {
            assignments.add("stock = ?");
            params.add(stock);
        }
        if (weeklySalesQty != null) {
            assignments.add("weekly_sales_qty = ?");
            params.add(weeklySalesQty);
        }

        if (assignments.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE Product SET " + String.join(", ", assignments) + " WHERE person_id = ? AND code = ?";
        Query query = entityManager.createNativeQuery(sql);
        int idx = 1;
        for (Object param : params) {
            query.setParameter(idx++, param);
        }
        query.setParameter(idx++, person.getId());
        query.setParameter(idx, normalizedBarcode);
        return query.executeUpdate();
    }
}
