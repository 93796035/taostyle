package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.web.TaoDebug;
import com.stgo.taostyle.web.TaoEmail;
import com.stgo.taostyle.web.TaoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

@Service
public class UserAccountService {
    public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("loginname", "password", "cel", "tel", "fax", "email", "companyname", "address", "city", "postcode", "credit", "balance", "description", "securitylevel", "person", "recordStatus");

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private UserAccountService userAccountService;

    @Transactional
    public UserAccount findUserAccountByEmail(String email) {
        TypedQuery<UserAccount> query = entityManager.createQuery(
                "SELECT u FROM UserAccount u WHERE u.email = :email",
                UserAccount.class);
        query.setParameter("email", email);
        return query.getResultList().stream().findFirst().orElse(null);
    }

    @Transactional
    public List<UserAccount> findAllUserAccounts() {
        return entityManager.createQuery("SELECT u FROM UserAccount u", UserAccount.class).getResultList();
    }

    @Transactional
    public long countUserAccounts() {
        return entityManager.createQuery("SELECT COUNT(u) FROM UserAccount u", Long.class).getSingleResult();
    }

    @Transactional
    public UserAccount findUserAccount(Long id) {
        UserAccount userAccount = entityManager.find(UserAccount.class, id);
        return userAccount;
    }

    @Transactional
    public void saveUserAccount(UserAccount userAccount) {
        if (userAccount.getId() == null) {
            entityManager.persist(userAccount);
        } else {
            entityManager.merge(userAccount);
        }
    }

    @Transactional
    public void remove(UserAccount userAccount) {
        if (entityManager.contains(userAccount)) {
            entityManager.remove(userAccount);
        } else {
            UserAccount attached = entityManager.find(UserAccount.class, userAccount.getId());
            if (attached != null) {
                entityManager.remove(attached);
            }
        }
    }

    @Transactional
    public UserAccount merge(UserAccount userAccount) {
        UserAccount merged = entityManager.merge(userAccount);
        entityManager.flush();
        return merged;
    }
    public List<UserAccount> findUserAccountEntriesByPerson(
            int firstResult,
            int maxResults,
            Person person) {
        TypedQuery<UserAccount> tQuery =
                entityManager.createQuery("SELECT o FROM UserAccount o where o.person = :person ORDER BY o.id DESC",
                        UserAccount.class);
        tQuery.setParameter("person", person);
        return tQuery.setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    @Transactional
    public long countUserAccountsByPerson(
            Person person) {
        return findAllUserAccountsByPerson(person).size();
    }

    @Transactional
    public List<UserAccount> findUserAccountEntriesByPerson(
            int firstResult,
            int maxResults,
            String sortFieldName,
            String sortOrder,
            Person person) {
        String jpaQuery = "SELECT o FROM UserAccount o where o.person = :person";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }

        TypedQuery<UserAccount> tQuery = entityManager.createQuery(jpaQuery, UserAccount.class);
        tQuery.setParameter("person", person);
        return tQuery.setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }

    public List<UserAccount> findAllUserAccountsByPerson(
            Person person) {
        TypedQuery<UserAccount> tQuery =
                entityManager.createQuery("SELECT o FROM UserAccount o where o.person = :person ORDER BY o.id DESC",
                        UserAccount.class);
        tQuery.setParameter("person", person);
        return tQuery.getResultList();
    }
    @Transactional
    public List<UserAccount> findAllUserAccountsByPerson(
            String sortFieldName,
            String sortOrder,
            Person person) {
        String jpaQuery = "SELECT o FROM UserAccount o where o.person =:person";
        if (fieldNames4OrderClauseFilter.contains(sortFieldName)) {
            jpaQuery = jpaQuery + " ORDER BY " + sortFieldName;
            if ("ASC".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                jpaQuery = jpaQuery + " " + sortOrder;
            }
        }
        TypedQuery<UserAccount> tQuery = entityManager.createQuery(jpaQuery, UserAccount.class);
        tQuery.setParameter("person", person);
        return tQuery.getResultList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(UserAccount userAccount) {
        entityManager.persist(userAccount);
    }

    /**
     * to make it stronger, we also tack the possibility that the pUserName maybe actually email. note: the username is
     * unique, because there's *{personId} in the end.
     *
     * @param userName
     * @return
     */
    public com.stgo.taostyle.domain.UserAccount findUserAccountByName(
            String userName) {
        TypedQuery<UserAccount> tQuery =
                entityManager
                        .createQuery("SELECT o FROM UserAccount AS o WHERE UPPER(o.loginname) = UPPER(:username)",
                                UserAccount.class);
        tQuery = tQuery.setParameter("username", userName);
        List<UserAccount> tList = tQuery.getResultList();
        if (tList != null && tList.size() == 1)
            return tList.get(0);
        else if (TaoEmail.isValidEmail(userName)) {
            // if it's null or have more than 1 match.
            tQuery =
                    entityManager.createQuery("SELECT o FROM UserAccount AS o WHERE UPPER(o.email) = UPPER(:tname)",
                            UserAccount.class);
            tQuery = tQuery.setParameter("tname", userName);
            tList = tQuery.getResultList();
            if (tList != null && tList.size() >= 1)
                return tList.get(0);
        }
        return null;
    }

    public com.stgo.taostyle.domain.UserAccount findUserAccountByNameAndPassword(
            String userName,
            String password) {
        TypedQuery<UserAccount> tQuery =
                entityManager
                        .createQuery(
                                "SELECT o FROM UserAccount AS o WHERE UPPER(o.loginname) = UPPER(:username) and o.password = :password",
                                UserAccount.class);
        tQuery = tQuery.setParameter("username", userName);
        tQuery = tQuery.setParameter("password", password);
        List<UserAccount> tList = tQuery.getResultList();
        if (tList != null && tList.size() == 1)
            return tList.get(0);
        else if (TaoEmail.isValidEmail(userName)) {
            // if it's null or have more than 1 match.
            tQuery =
                    entityManager
                            .createQuery(
                                    "SELECT o FROM UserAccount AS o WHERE UPPER(o.email) = UPPER(:email) and o.password = :password",
                                    UserAccount.class);
            tQuery = tQuery.setParameter("email", userName);
            tQuery = tQuery.setParameter("password", password);
            tList = tQuery.getResultList();
            if (tList != null && tList.size() >= 1)
                return tList.get(0);
        }
        return null;
    }

    public com.stgo.taostyle.domain.UserAccount findUserAccountsByNameAndTell(
            String userName,
            String tell) {
        tell = TaoUtil.cleanUpTel(tell);

        TypedQuery<UserAccount> tQuery =
                entityManager
                        .createQuery(
                                "SELECT o FROM UserAccount AS o WHERE UPPER(o.loginname) = UPPER(:username) and o.tel = :tel or o.cel = :cel",
                                UserAccount.class);
        tQuery = tQuery.setParameter("username", userName);
        tQuery = tQuery.setParameter("tel", tell);
        tQuery = tQuery.setParameter("cel", tell);
        UserAccount userAccount = null;
        try {
            List<UserAccount> list = tQuery.getResultList();
            userAccount = list.get(0);
            if (list.size() > 1) {
                UserAccount oldUser = null;
                for (UserAccount user : list) {
                    if (oldUser == null) {
                        oldUser = user;
                    } else if (!oldUser.equals(user)) {
                        TaoDebug.error("found duplicated and not toltally same user in db", user.getLoginname());
                    }
                }
            }
        } catch (Exception e) {
            // do nothing.
        }

        return userAccount;
    }
}
