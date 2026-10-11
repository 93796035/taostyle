package com.stgo.taostyle.service;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.TextContent;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.Material;
import com.stgo.taostyle.repository.MainOrderRepository;
import com.stgo.taostyle.web.CC;
import com.stgo.taostyle.web.RefreshNoticeCenter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.annotation.SessionScope;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class MainOrderService {

    @Autowired
    private MainOrderRepository mainOrderRepository;

    public long countMainOrders() {
        return findAllMainOrders().size();
    }

    @Transactional
    public List<MainOrder> findAllMainOrders() {
        return mainOrderRepository.findAllMainOrders();
    }

    @Transactional
    public List<MainOrder> findAllMainOrders(String sortFieldName, String sortOrder) {
        return mainOrderRepository.findAllMainOrders(sortFieldName, sortOrder);
    }

    public List<MainOrder> findMainOrderByCON(String pClientSideOrderNumber) {
        return mainOrderRepository.findMainOrderByCON(pClientSideOrderNumber);
    }

    public List<MainOrder> finMainOrdersBySizeTableAndPerson(String sizeTable, Person person) {
        return mainOrderRepository.finMainOrdersBySizeTableAndPerson(sizeTable, person);
    }

    @Transactional
    public List<MainOrder> findMainOrdersByPerson(Person person, String order) {
        return mainOrderRepository.findMainOrdersByPerson(person, order);
    }

    @Transactional
    public List<MainOrder> findCompletedMainOrdersByPerson(Person person, String order) {
        return mainOrderRepository.findCompletedMainOrdersByPerson(person, order);
    }

    @Transactional
    public List<MainOrder> findMainOrdersByStatusLimitationAndPerson(int recordStatus, Person person, String order) {
        return mainOrderRepository.findMainOrdersByStatusLimitationAndPerson(recordStatus, person, order);
    }

    @Transactional
    public List<MainOrder> findMainOrdersByStatusAndPerson(int recordStatus, Person person, String order) {
        return mainOrderRepository.findMainOrdersByStatusAndPerson(recordStatus, person, order);
    }

    public List<MainOrder> findNewMainOrdersOfToday(String lastProcessedID, Set<Person> personSet) {
        return mainOrderRepository.findNewMainOrdersOfToday(lastProcessedID, personSet);
    }

    @Transactional
    public List<MainOrder> findMainOrdersByClientAndPerson(UserAccount client, Person person, String order) {
        return mainOrderRepository.findMainOrdersByClientAndPerson(client, person, order);
    }

    @Transactional
    public List<MainOrder> findUnCompletedMainOrdersByPerson( Person person, String order) {
        return mainOrderRepository.findUnCompletedMainOrdersByPerson(person, order);
    }
    @Transactional
    public MainOrder findMainOrder(Long id) {
        if (id == null) return null;
        return mainOrderRepository.findMainOrder(id);
    }

    @Transactional
    public List<MainOrder> findMainOrderEntries(int firstResult, int maxResults) {
        return mainOrderRepository.findMainOrderEntries(firstResult, maxResults);
    }

    @Transactional
    public List<MainOrder> findMainOrderEntries(int firstResult, int maxResults, String sortFieldName, String sortOrder) {
        return mainOrderRepository.findMainOrderEntries(firstResult, maxResults, sortFieldName, sortOrder);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(MainOrder mainOrder) {
        mainOrderRepository.persist(mainOrder);
    }

    @Transactional
    public void remove(MainOrder mainOrder) {
        mainOrderRepository.remove(mainOrder);
    }

    @Transactional
    public void flush() {
        this.mainOrderRepository.flush();
    }

    @Transactional
    public void clear() {
        this.mainOrderRepository.clear();
    }

    @Transactional
    public MainOrder merge(MainOrder mainOrder) {
        return mainOrderRepository.merge(mainOrder);
    }
}
