package com.stgo.taostyle.service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits one mobile Product independently so a bad row does not undo good rows. */
@Service
public class MobileProductSyncService {
    private volatile boolean nullableLocalIdChecked;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long upsert(Long personId, Long localId, JSONObject d) {
        if (personId == null || personId <= 0) throw new IllegalArgumentException("Invalid person_id: " + personId);
        if (localId != null && localId <= 0) localId = null;
        Long existingId = findServerId(personId, localId, d);
        if (existingId != null) {
            if (localId != null) entityManager.createNativeQuery("UPDATE Product SET local_id = ? WHERE id = ? AND person_id = ?")
                    .setParameter(1, localId).setParameter(2, existingId).setParameter(3, personId).executeUpdate();
            updateProduct(existingId, personId, d);
            return localId;
        }
        String sql = "INSERT INTO Product (person_id,local_id,folderid,subject,content,code,unit,price,producarea,brand,category,mnemonic,cost,qst,`index`,stock,stock1,stock2,stock3,stock1_shortage,deleted,coverdays,weekly_sales_qty,monthly_sales_qty,last_modified) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, ?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE folderid=VALUES(folderid),subject=VALUES(subject),content=VALUES(content),code=VALUES(code),unit=VALUES(unit),price=VALUES(price),producarea=VALUES(producarea),brand=VALUES(brand),category=VALUES(category),mnemonic=VALUES(mnemonic),cost=VALUES(cost),qst=VALUES(qst),`index`=VALUES(`index`),stock=VALUES(stock),stock1=VALUES(stock1),stock2=VALUES(stock2),stock3=VALUES(stock3),stock1_shortage=VALUES(stock1_shortage),deleted=VALUES(deleted),coverdays=VALUES(coverdays),weekly_sales_qty=VALUES(weekly_sales_qty),monthly_sales_qty=VALUES(monthly_sales_qty),last_modified=CURRENT_TIMESTAMP";
        javax.persistence.Query q = entityManager.createNativeQuery(sql);
        Object[] values = {personId, localId, d.optInt("folderid", 0), d.optString("subject", null), d.optString("content", null), d.optString("code", null), d.optString("unit", null), d.optInt("price", 0), d.optString("producarea", null), d.optString("brand", null), d.optString("category", null), d.optString("mnemonic", null), d.optInt("cost", 0), d.optInt("qst", 0), d.optInt("index", 0), d.optInt("stock", 0), d.optInt("stock1", 0), d.optInt("stock2", 0), d.optInt("stock3", 0), d.optInt("monque", 0), d.optInt("deleted", 0), d.optDouble("coverdays", 0), d.optInt("weekly_sales_qty", 0), d.optInt("monthly_sales_qty", 0)};
        for (int i = 0; i < values.length; i++) q.setParameter(i + 1, values[i]);
        q.executeUpdate();
        return localId;
    }

    private Long findServerId(Long personId, Long localId, JSONObject d) {
        if (localId != null) {
            java.util.List<?> rows = entityManager.createNativeQuery("SELECT id FROM Product WHERE person_id = ? AND local_id = ? ORDER BY id LIMIT 1")
                    .setParameter(1, personId).setParameter(2, localId).getResultList();
            if (!rows.isEmpty()) return ((Number) rows.get(0)).longValue();
        }
        String code = d.optString("code", "").trim();
        if (!code.isEmpty()) {
            java.util.List<?> rows = entityManager.createNativeQuery("SELECT id FROM Product WHERE person_id = ? AND code = ? ORDER BY id LIMIT 1")
                    .setParameter(1, personId).setParameter(2, code).getResultList();
            if (!rows.isEmpty()) return ((Number) rows.get(0)).longValue();
        }
        return null;
    }

    /** Older deployments created local_id as NOT NULL; make the compatibility path usable. */
    public void ensureNullableLocalId() {
        if (nullableLocalIdChecked) return;
        synchronized (this) {
            if (nullableLocalIdChecked) return;
            try {
                entityManager.createNativeQuery("ALTER TABLE Product MODIFY local_id INT NULL").executeUpdate();
            } catch (RuntimeException ignored) {
                // Already nullable, or a deployment has a dialect-specific equivalent.
            }
            nullableLocalIdChecked = true;
        }
    }

    private void updateProduct(Long id, Long personId, JSONObject d) {
        String sql = "UPDATE Product SET folderid=?,subject=?,content=?,code=?,unit=?,price=?,producarea=?,brand=?,category=?,mnemonic=?,cost=?,qst=?,`index`=?,stock=?,stock1=?,stock2=?,stock3=?,stock1_shortage=?,deleted=?,coverdays=?,weekly_sales_qty=?,monthly_sales_qty=?,last_modified=CURRENT_TIMESTAMP WHERE id=? AND person_id=?";
        javax.persistence.Query q = entityManager.createNativeQuery(sql);
        Object[] values = {d.optInt("folderid", 0), d.optString("subject", null), d.optString("content", null), d.optString("code", null), d.optString("unit", null), d.optInt("price", 0), d.optString("producarea", null), d.optString("brand", null), d.optString("category", null), d.optString("mnemonic", null), d.optInt("cost", 0), d.optInt("qst", 0), d.optInt("index", 0), d.optInt("stock", 0), d.optInt("stock1", 0), d.optInt("stock2", 0), d.optInt("stock3", 0), d.optInt("monque", 0), d.optInt("deleted", 0), d.optDouble("coverdays", 0), d.optInt("weekly_sales_qty", 0), d.optInt("monthly_sales_qty", 0), id, personId};
        for (int i = 0; i < values.length; i++) q.setParameter(i + 1, values[i]);
        q.executeUpdate();
    }
}
