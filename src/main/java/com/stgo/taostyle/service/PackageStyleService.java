package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.PackageStyle;
import com.stgo.taostyle.repository.PackageStyleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

@Service
public class PackageStyleService {
    @PersistenceContext
    transient EntityManager entityManager;
    
    @Autowired
    private MainOrderService mainOrderService;
    @Autowired
    private PackageStyleRepository packageStyleRepository;

    public List<PackageStyle> findAllPackageStylesByMainOrder(Long mainOrderId) {
        MainOrder tMainOrder = mainOrderService.findMainOrder(mainOrderId);
        if (tMainOrder == null) {
            return packageStyleRepository.findAllPackageStyles();
        } else {
            return packageStyleRepository.findAllPackageStylesByMainOrder(tMainOrder);
        }
    }

    public List<PackageStyle> findPackageStyleEntriesByMainOrder(
            Long mainOrderId,
            int firstResult,
            int maxResults) {
        MainOrder tMainOrder = mainOrderService.findMainOrder(mainOrderId);
        if (tMainOrder == null) {
            return packageStyleRepository.findPackageStyleEntries(firstResult, maxResults);
        } else {
            return packageStyleRepository.findPackageStyleEntriesByMainOrder(tMainOrder, firstResult, maxResults);
        }
    }

    @Transactional
    public long countPackageStyles() {
        return packageStyleRepository.countPackageStyles();
    }

    @Transactional
    public List<PackageStyle> findAllPackageStyles() {
        return packageStyleRepository.findAllPackageStyles();
    }

    @Transactional
    public List<PackageStyle> findAllPackageStyles(String sortFieldName, String sortOrder) {
        return packageStyleRepository.findAllPackageStyles(sortFieldName, sortOrder);
    }

    @Transactional
    public PackageStyle findPackageStyle(Long id) {
        if (id == null) return null;
        return packageStyleRepository.findPackageStyle(id);
    }

    @Transactional
    public List<PackageStyle> findPackageStyleEntries(int firstResult, int maxResults) {
        return packageStyleRepository.findPackageStyleEntries(firstResult, maxResults);
    }

    @Transactional
    public List<PackageStyle> findPackageStyleEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        return packageStyleRepository.findPackageStyleEntries(firstResult, maxResults, sortFieldName, sortOrder);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(PackageStyle packageStyle) {
        packageStyleRepository.persist(packageStyle);
    }

    @Transactional
    public void remove(PackageStyle packageStyle) {
        packageStyleRepository.remove(packageStyle);
    }

    @Transactional
    public void flush() {
        packageStyleRepository.flush();
    }

    @Transactional
    public void clear() {
        packageStyleRepository.clear();
    }

    @Transactional
    public PackageStyle merge(PackageStyle packageStyle) {
        return packageStyleRepository.merge(packageStyle);
    }

}
