package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.Material;
import com.stgo.taostyle.repository.MaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MaterialService {
    
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private MainOrderService mainOrderService;
    
    public List<Material> findAllMaterialsByMainOrderId( Long mainOrderId) {
        MainOrder mainOrder = mainOrderService.findMainOrder(mainOrderId);
        return findAllMaterialsByMainOrder(mainOrder);
    }

    // @!!when adding printer to material, make sure it starts and ends with ",", no space.
    public List<Material> findAllMaterialsByMainOrderIdAndPrinter(
            MainOrder mainOrder,
            UserAccount printer) {
        return materialRepository.findAllMaterialsByMainOrderIdAndPrinter(mainOrder, printer);
    }

    public List<Material> findMaterialEntriesByMainOrderId(
            Long mainOrderId,
            int firstResult,
            int maxResults) {
        MainOrder tMainOrder = mainOrderService.findMainOrder(mainOrderId);
        if (tMainOrder == null) {
            return materialRepository.findMaterialEntries(firstResult, maxResults);
        } else {
            return materialRepository.findMaterialEntriesByMainOrderId(tMainOrder, firstResult, maxResults);
        }
    }

    public List<Material> findAllMaterialsByMainOrder( MainOrder mainOrder) {
        return materialRepository.findAllMaterialsByMainOrder(mainOrder);
    }

    @Transactional
    public long countMaterials() {
        return findAllMaterials().size();
    }

    @Transactional
    public List<Material> findAllMaterials() {
        return materialRepository.findAllMaterials();
    }

    @Transactional
    public List<Material> findAllMaterialsByPersonAndStatus(Person person, int status) {
        return materialRepository.findAllMaterialsByPersonAndStatus(person, status);
    }

    @Transactional
    public Material findMaterial(Long id) {
        if (id == null) return null;
        return materialRepository.findMaterial(id);
    }

    @Transactional
    public List<Material> findMaterialEntries(int firstResult, int maxResults) {
        return materialRepository.findMaterialEntries(firstResult, maxResults);
    }

    @Transactional
    public List<Material> findMaterialEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        return materialRepository.findMaterialEntries(firstResult, maxResults, sortFieldName, sortOrder);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(Material material) {
        materialRepository.persist(material);
    }

    @Transactional
    public void remove(Material material) {
        materialRepository.remove(material);
    }

    @Transactional
    public void flush() {
        materialRepository.flush();
    }

    @Transactional
    public void clear() {
        materialRepository.clear();
    }

    @Transactional
    public Material merge(Material material) {
        return materialRepository.merge(material);
    }

}
