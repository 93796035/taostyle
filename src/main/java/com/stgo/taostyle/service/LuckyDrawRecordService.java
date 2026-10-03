package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.LuckyDrawRecord;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.web.CC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class LuckyDrawRecordService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void persist(LuckyDrawRecord luckyDrawRecord) {
        entityManager.persist(luckyDrawRecord);
    }

    @Transactional
    public LuckyDrawRecord merge(LuckyDrawRecord luckyDrawRecord) {
        return entityManager.merge(luckyDrawRecord);
    }

    @Transactional
    public LuckyDrawRecord findPendingPrize(Person person, UserAccount userAccount) {
        if (person == null || userAccount == null) {
            return null;
        }
        expireOldPendingPrizes(person, userAccount);
        TypedQuery<LuckyDrawRecord> query = entityManager.createQuery(
                "SELECT o FROM LuckyDrawRecord o WHERE o.person = :person AND o.userAccount = :userAccount "
                        + "AND o.status = :status AND o.recordStatus = 0 ORDER BY o.createdAt DESC",
                LuckyDrawRecord.class);
        query.setParameter("person", person);
        query.setParameter("userAccount", userAccount);
        query.setParameter("status", CC.lucky_status_won);
        List<LuckyDrawRecord> list = query.setMaxResults(1).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @Transactional
    public boolean hasSpinToday(Person person, UserAccount userAccount) {
        if (person == null || userAccount == null) {
            return false;
        }
        expireOldPendingPrizes(person, userAccount);
        Long count = entityManager.createQuery(
                        "SELECT COUNT(o) FROM LuckyDrawRecord o WHERE o.person = :person AND o.userAccount = :userAccount "
                                + "AND o.recordStatus = 0 AND o.createdAt >= :startOfToday",
                        Long.class)
                .setParameter("person", person)
                .setParameter("userAccount", userAccount)
                .setParameter("startOfToday", startOfToday())
                .getSingleResult();
        return count != null && count.longValue() > 0L;
    }

    @Transactional
    public List<LuckyDrawRecord> findRecentRecords(Person person, UserAccount userAccount) {
        if (person == null || userAccount == null) {
            return java.util.Collections.emptyList();
        }
        expireOldPendingPrizes(person, userAccount);
        return entityManager.createQuery(
                        "SELECT o FROM LuckyDrawRecord o WHERE o.person = :person AND o.userAccount = :userAccount "
                                + "AND o.recordStatus = 0 ORDER BY o.createdAt DESC",
                        LuckyDrawRecord.class)
                .setParameter("person", person)
                .setParameter("userAccount", userAccount)
                .setMaxResults(20)
                .getResultList();
    }

    @Transactional
    public int expireOldPendingPrizes(Person person, UserAccount userAccount) {
        if (person == null || userAccount == null) {
            return 0;
        }
        return entityManager.createQuery(
                        "UPDATE LuckyDrawRecord o SET o.status = :expiredStatus WHERE o.person = :person "
                                + "AND o.userAccount = :userAccount AND o.recordStatus = 0 "
                                + "AND o.status = :wonStatus AND o.createdAt < :startOfToday")
                .setParameter("expiredStatus", CC.lucky_status_expired)
                .setParameter("wonStatus", CC.lucky_status_won)
                .setParameter("person", person)
                .setParameter("userAccount", userAccount)
                .setParameter("startOfToday", startOfToday())
                .executeUpdate();
    }

    @Transactional
    public LuckyDrawRecord createWinningRecord(Person person, UserAccount userAccount, String prizeCode, String prizeName) {
        LuckyDrawRecord luckyDrawRecord = new LuckyDrawRecord();
        luckyDrawRecord.setPerson(person);
        luckyDrawRecord.setUserAccount(userAccount);
        luckyDrawRecord.setPrizeCode(prizeCode);
        luckyDrawRecord.setPrizeName(prizeName);
        luckyDrawRecord.setStatus(CC.lucky_status_won);
        luckyDrawRecord.setCreatedAt(new Date());
        luckyDrawRecord.setRecordStatus(0);
        entityManager.persist(luckyDrawRecord);
        return luckyDrawRecord;
    }

    private Date startOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
