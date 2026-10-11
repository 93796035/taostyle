package com.stgo.taostyle.repository;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.web.TaoDebug;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Repository
public class MainOrderRepository {

    public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("client", "clientSideOrderNumber", "clientSideModelNumber", "model", "picture", "productName", "contactPerson", "quantity", "payCondition", "colorProportionReceived", "delieverdate", "sizeTable", "colorCard", "testRequirement", "sampleRequirement", "Remark", "person", "recordStatus","total_after_tax");

    @PersistenceContext
    private EntityManager entityManager;

    public long countMainOrders() {
        return findAllMainOrders().size();
    }

    public List<MainOrder> findAllMainOrders() {
        return entityManager.createQuery("SELECT o FROM MainOrder o", MainOrder.class).getResultList();
    }

    public List<MainOrder> findAllMainOrders(String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM MainOrder o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, MainOrder.class).getResultList();
    }

    public MainOrder findMainOrder(Long id) {
        if (id == null) return null;
        return entityManager.find(MainOrder.class, id);
    }

    public List<MainOrder> findMainOrderByCON(String pClientSideOrderNumber) {
        TypedQuery<MainOrder> tQuery =
                entityManager.createQuery(
                        "SELECT o FROM MainOrder AS o WHERE UPPER(o.clientSideOrderNumber) = UPPER(:tCON)",
                        MainOrder.class);
        tQuery = tQuery.setParameter("tCON", pClientSideOrderNumber);
        List<MainOrder> tList = tQuery.getResultList();
        return tList;
    }

    public List<MainOrder> finMainOrdersBySizeTableAndPerson(
            String SizeTable,
            Person person) {
        TypedQuery<MainOrder> tQuery =
                entityManager
                        .createQuery(
                                "SELECT o FROM MainOrder AS o WHERE o.sizeTable = :sizeTable and o.person = :person and o.recordStatus = 0 order by o.id desc",
                                MainOrder.class);
        tQuery = tQuery.setParameter("sizeTable", SizeTable);
        tQuery = tQuery.setParameter("person", person);
        return tQuery.getResultList();
    }

    public List<MainOrder> findMainOrdersByPerson(
            Person person,
            String order) {
        TypedQuery<MainOrder> tQuery =
                entityManager.createQuery("SELECT o FROM MainOrder AS o WHERE o.person = :person ORDER BY o.id "
                        + order, MainOrder.class);
        tQuery = tQuery.setParameter("person", person);

        List<MainOrder> mainOrders = null;
        try {
            mainOrders = tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }

        return mainOrders;
    }

    public List<MainOrder> findMainOrdersByStatusAndPerson(
            int recordStatus,
            Person person,
            String order) {
        TypedQuery<MainOrder> tQuery =
                entityManager.createQuery(
                        "SELECT o FROM MainOrder AS o WHERE o.person = :person and o.recordStatus = :recordStatus ORDER BY o.id "
                                + order, MainOrder.class);
        tQuery = tQuery.setParameter("person", person);
        tQuery = tQuery.setParameter("recordStatus", recordStatus);

        List<MainOrder> mainOrders = null;
        try {
            mainOrders = tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }

        return mainOrders;
    }

    public List<MainOrder> findCompletedMainOrdersByPerson(
            Person person,
            String order) {
        TypedQuery<MainOrder> tQuery =
                entityManager.createQuery(
                        "SELECT o FROM MainOrder AS o WHERE o.person = :person and o.recordStatus < 0 ORDER BY o.id "
                                + order, MainOrder.class);
        tQuery = tQuery.setParameter("person", person);

        // we display all uncompleated order, if want to get today's order, should open a new method.
        // and o.delieverdate > :delieverdate
        // Calendar calendar = Calendar.getInstance();
        // calendar.set(Calendar.HOUR_OF_DAY, 1);
        // tQuery = tQuery.setParameter("delieverdate", calendar.getTime());

        List<MainOrder> mainOrders = null;
        try {
            mainOrders = tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }

        return mainOrders;
    }

    public List<MainOrder> findNewMainOrdersOfToday(String lastProcessedID, Set<Person> personSet) {
        TypedQuery<MainOrder> tQuery =
                entityManager.createQuery(
                        "SELECT o FROM MainOrder AS o WHERE o.id > :lastProcessedID and o.person in (:personSet) and (deliverdate > :today or deliverdate is null) ORDER BY o.id", MainOrder.class);

//SELECT * FROM MainOrder AS o WHERE o.id > 1 and o.person_id in (10064, 10072)  and (o.delieverdate > '1970-01-01 00:00:00' or o.delieverdate is null) ORDER BY o.delieverdate

        tQuery = tQuery.setParameter("lastProcessedID", Long.valueOf(lastProcessedID));
        tQuery = tQuery.setParameter("personSet", personSet);

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String today = df.format(new Date()) + " 00:00:00";
        tQuery = tQuery.setParameter("today", today);

        List<MainOrder> mainOrders = null;
//        List<MainOrder> mainOrders = tEntityManager.createNativeQuery("SELECT * FROM MainOrder AS o WHERE o.id > " + lastProcessedID + " and o.person_id in (" +  personSet + ") ORDER BY o.id").getResultList();
        try {
            mainOrders = tQuery.getResultList();
        }catch(Exception e) {
            try {
                mainOrders = tQuery.getResultList();	//TODO: don't know why the first time always fail, while the second time start to work.......
            }catch(Exception exp) {
                TaoDebug.error("MainOrder Controller: excepioon when running sql:" + exp);
            }
        }
        return mainOrders;
    }

    public List<MainOrder> findMainOrderEntries(int firstResult, int maxResults) {
        return entityManager.createQuery("SELECT o FROM MainOrder o", MainOrder.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    public List<MainOrder> findMainOrdersByClientAndPerson(
            UserAccount client,
            Person person,
            String order) {
        TypedQuery<MainOrder> tQuery =
                entityManager.createQuery(
                        "SELECT o FROM MainOrder AS o WHERE o.person = :person and o.client = :client ORDER BY o.id "
                                + order, MainOrder.class);
        tQuery = tQuery.setParameter("person", person);
        tQuery = tQuery.setParameter("client", client);

        // we display all uncompleated order, if want to get today's order, should open a new method.
        // and o.delieverdate > :delieverdate
        // Calendar calendar = Calendar.getInstance();
        // calendar.set(Calendar.HOUR_OF_DAY, 1);
        // tQuery = tQuery.setParameter("delieverdate", calendar.getTime());

        List<MainOrder> mainOrders = null;
        try {
            mainOrders = tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }

        return mainOrders;
    }

    public List<MainOrder> findUnCompletedMainOrdersByPerson(
            Person person,
            String order) {
        TypedQuery<MainOrder> tQuery =
                entityManager
                        .createQuery(
                                "SELECT o FROM MainOrder AS o WHERE o.person = :person and o.recordStatus > -1 and o.recordStatus < 10000 ORDER BY o.id "
                                        + order, MainOrder.class);
        tQuery = tQuery.setParameter("person", person);

        // we display all uncompleated order, if want to get today's order, should open a new method.
        // and o.delieverdate > :delieverdate
        // Calendar calendar = Calendar.getInstance();
        // calendar.set(Calendar.HOUR_OF_DAY, 1);
        // tQuery = tQuery.setParameter("delieverdate", calendar.getTime());

        List<MainOrder> mainOrders = null;
        try {
            mainOrders = tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }

        return mainOrders;
    }

    public List<MainOrder> findMainOrdersByStatusLimitationAndPerson(
            int recordStatus,
            Person person,
            String order) {
        TypedQuery<MainOrder> tQuery =
                entityManager.createQuery(
                        "SELECT o FROM MainOrder AS o WHERE o.person = :person and o.recordStatus >= 0 and o.recordStatus <= :recordStatus ORDER BY o.id "
                                + order, MainOrder.class);
        tQuery = tQuery.setParameter("person", person);
        tQuery = tQuery.setParameter("recordStatus", recordStatus);

        List<MainOrder> mainOrders = null;
        try {
            mainOrders = tQuery.getResultList();
        } catch (Exception e) {
            return null;
        }

        return mainOrders;
    }

    public List<MainOrder> findMainOrderEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        String jpaQuery = "SELECT o FROM MainOrder o";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        return entityManager.createQuery(jpaQuery, MainOrder.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    public void persist(MainOrder mainOrder) {
        this.entityManager.persist(mainOrder);
    }

    public void remove(MainOrder mainOrder) {
        if (this.entityManager.contains(mainOrder)) {
            this.entityManager.remove(mainOrder);
        } else {
            MainOrder attached = findMainOrder(mainOrder.getId());
            this.entityManager.remove(attached);
        }
    }

    public void flush() {
        this.entityManager.flush();
    }

    public void clear() {
        this.entityManager.clear();
    }

    public MainOrder merge(MainOrder mainOrder) {
        MainOrder merged = entityManager.merge(mainOrder);
        this.entityManager.flush();
        return merged;
    }
}
