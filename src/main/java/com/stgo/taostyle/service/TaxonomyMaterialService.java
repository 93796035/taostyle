package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.TaxonomyMaterial;
import com.stgo.taostyle.repository.TaxonomyMaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaxonomyMaterialService {

    @Autowired
    private TaxonomyMaterialRepository taxonomyMaterialRepository;
    @Autowired
    private  MainOrderService mainOrderService;

    public List<TaxonomyMaterial> findTaxonomyMaterialByLocation(String location) {
        return taxonomyMaterialRepository.findTaxonomyMaterialByLocation(location);
    }

    public List<TaxonomyMaterial> findAllTaxonomyMaterialsByMainOrder(
            MainOrder mainOrder) {
        if (mainOrder == null) {
            return taxonomyMaterialRepository.findAllTaxonomyMaterials();
        } else {
            return taxonomyMaterialRepository.findAllTaxonomyMaterialsByMainOrder(mainOrder);
        }
    }

    public List<TaxonomyMaterial> findAllTaxonomyMaterialsByMainOrderID(
            Long mainOrderId) {
        MainOrder mainOrder = mainOrderService.findMainOrder(mainOrderId);
        return findAllTaxonomyMaterialsByMainOrder(mainOrder);
    }

    public List<TaxonomyMaterial> findTaxonomyMaterialEntriesByMainOrder(
            Long mainOrderId,
            int firstResult,
            int maxResults) {
        MainOrder tMainOrder = mainOrderService.findMainOrder(mainOrderId);
        if (tMainOrder == null) {
            return taxonomyMaterialRepository.findTaxonomyMaterialEntries(firstResult, maxResults);
        } else {
            return taxonomyMaterialRepository.findTaxonomyMaterialEntriesByMainOrder(tMainOrder, firstResult,maxResults);
        }
    }

    @Transactional
    public long countTaxonomyMaterials() {
        return findAllTaxonomyMaterials().size();
    }

    @Transactional
    public List<TaxonomyMaterial> findAllTaxonomyMaterials() {
        return taxonomyMaterialRepository.findAllTaxonomyMaterials();
    }

    @Transactional
    public List<TaxonomyMaterial> findAllTaxonomyMaterials(String sortFieldName, String sortOrder) {
        return taxonomyMaterialRepository.findAllTaxonomyMaterials();
    }

    @Transactional
    public TaxonomyMaterial findTaxonomyMaterial(Long id) {
        if (id == null) return null;
        return taxonomyMaterialRepository.findTaxonomyMaterial(id);
    }

    @Transactional
    public List<TaxonomyMaterial> findTaxonomyMaterialEntries(int firstResult, int maxResults) {
        return taxonomyMaterialRepository.findTaxonomyMaterialEntries(firstResult, maxResults);
    }

    @Transactional
    public List<TaxonomyMaterial> findTaxonomyMaterialEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        return taxonomyMaterialRepository.findTaxonomyMaterialEntries(firstResult, maxResults, sortFieldName, sortOrder);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(TaxonomyMaterial taxonomyMaterial) {
        taxonomyMaterialRepository.persist(taxonomyMaterial);
    }

    @Transactional
    public void remove(TaxonomyMaterial taxonomyMaterial) {
        taxonomyMaterialRepository.remove(taxonomyMaterial);
    }

    @Transactional
    public void flush() {
        taxonomyMaterialRepository.flush();
    }

    @Transactional
    public void clear() {
        taxonomyMaterialRepository.clear();
    }

    @Transactional
    public TaxonomyMaterial merge(TaxonomyMaterial taxonomyMaterial) {
        return taxonomyMaterialRepository.merge(taxonomyMaterial);
    }

}
