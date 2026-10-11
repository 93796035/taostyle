package com.stgo.taostyle.web;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.stgo.taostyle.service.PersonService;
import com.stgo.taostyle.service.LuckyDrawRecordService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.WebUtils;
import com.stgo.taostyle.backend.security.TaoEncrypt;
import com.stgo.taostyle.backend.security.UserContextService;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import java.io.UnsupportedEncodingException;
import java.util.List;

@RequestMapping("/people")
@Controller


public class PersonController {

    @Inject
    private UserContextService userContextService;

    @Autowired
    private PersonService personService;
    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private TaoDbUtil taoDbUtil;
    @Autowired
    private LuckyDrawRecordService luckyDrawRecordService;

    @RequestMapping(params = "form", produces = "text/html")
    public String createForm(
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        populateEditForm(uiModel, person != null ? person : new Person());
        UserAccount currentUser = (UserAccount) request.getSession().getAttribute(CC.currentUser);
        if (person != null && currentUser != null) {
            uiModel.addAttribute("luckyPrizeRecords", luckyDrawRecordService.findRecentRecords(person, currentUser));
        }
        return "people/create";
    }

    @RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(
            @Valid Person person,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest httpServletRequest) {

        if (isItHacking(person, httpServletRequest)) {
            return "login";
        }

        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, person);
            return "people/create";
        }
        uiModel.asMap().clear();
        String password = person.getPassword();
        if (!StringUtils.isBlank(password)) {
            String encryptedPassword = TaoEncrypt.encryptPassword(password);
            Person personInDB = personService.findPersonByName(person.getName());
            if (!personInDB.getPassword().equals(encryptedPassword)) {
                personInDB.setPassword(encryptedPassword);
                personService.persist(personInDB);
            }
        }
        return "redirect:/people/" + encodeUrlPathSegment(person.getId().toString(), httpServletRequest);
    }

    private boolean isItHacking(
            Person person,
            HttpServletRequest request) {
        Person curPerson = (Person) request.getSession().getAttribute(CC.CLIENT);
        if (curPerson == null || person == null || !curPerson.getName().equals(person.getName())) {
            TaoDebug.warn(TaoDebug.getSB(request.getSession()),
                    "unexpected visit to PersonController, curPerson: {}, client: {}", curPerson, person);
            return true;
        }
        return false;
    }

    private boolean isItHacking(
            long id,
            HttpServletRequest request) {
        Person curPerson = (Person) request.getSession().getAttribute(CC.CLIENT);
        if (curPerson == null || curPerson.getId() != id) {
            TaoDebug.warn(TaoDebug.getSB(request.getSession()),
                    "unexpected visit to PersonController, curPerson: {}, person id: {}", curPerson, id);
            return true;
        }
        return false;
    }

    @RequestMapping(value = "/{id}", produces = "text/html")
    public String show(
            @PathVariable("id") Long id,
            Model uiModel,
            HttpServletRequest httpServletRequest) {

        if (isItHacking(id, httpServletRequest)) {
            return "login";
        }

        uiModel.addAttribute("person", personService.findPerson(id));
        uiModel.addAttribute("itemId", id);
        return "people/show";
    }

    @RequestMapping(produces = "text/html")
    public String list(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortFieldName", required = false) String sortFieldName,
            @RequestParam(value = "sortOrder", required = false) String sortOrder,
            Model uiModel,
            HttpServletRequest httpServletRequest) {
        String name = userContextService.getCurrentUserName();
        if (!CC.ADAM.equalsIgnoreCase(name) && !CC.EVE.equalsIgnoreCase(name)) {
            return "login";
        }

        if (StringUtils.isBlank(sortFieldName)) {
            sortFieldName = "name";
        }
        if (StringUtils.isBlank(sortOrder)) {
            sortOrder = "ASC";
        }

        if (page != null || size != null) {
            int sizeNo = size == null ? 10 : size.intValue();
            final int firstResult = page == null ? 0 : (page.intValue() - 1) * sizeNo;
            uiModel.addAttribute("people", personService.findPersonEntries(firstResult, sizeNo, sortFieldName, sortOrder));
            float nrOfPages = (float) personService.countPeople() / sizeNo;
            uiModel.addAttribute("maxPages",
                    (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1 : nrOfPages));
        } else {
            uiModel.addAttribute("people", personService.findAllPeople(sortFieldName, sortOrder));
        }
        return "people/list";
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(
            @PathVariable("id") Long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            Model uiModel,
            HttpServletRequest request) {

        Person curPerson = taoUtil.getCurPerson(request);
        if (!CC.EVE.equals(curPerson.getName()) && !CC.ADAM.equals(curPerson.getName())) {
            return "login";
        }


        Person person = personService.findPerson(id);
        taoDbUtil.cleanPersonRecords(person);

        //
        personService.remove(person);
        uiModel.asMap().clear();
        uiModel.addAttribute("page", (page == null) ? "1" : page.toString());
        uiModel.addAttribute("size", (size == null) ? "10" : size.toString());
        return "redirect:/people";
    }

    @RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(
            @Valid Person person,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, person);
            return "people/update";
        }
        uiModel.asMap().clear();
        personService.merge(person);
        return "redirect:/people/" + encodeUrlPathSegment(person.getId().toString(), httpServletRequest);
    }

	@RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(@PathVariable("id") Long id, Model uiModel) {
        populateEditForm(uiModel, personService.findPerson(id));
        return "people/update";
    }

	void populateEditForm(Model uiModel, Person person) {
        uiModel.addAttribute("person", person);
    }

	String encodeUrlPathSegment(String pathSegment, HttpServletRequest httpServletRequest) {
        String enc = httpServletRequest.getCharacterEncoding();
        if (enc == null) {
            enc = WebUtils.DEFAULT_CHARACTER_ENCODING;
        }
        try {
            pathSegment = UriUtils.encodePathSegment(pathSegment, enc);
        } catch (UnsupportedEncodingException uee) {}
        return pathSegment;
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.GET, headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> showJson(@PathVariable("id") Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        try {
            Person person = personService.findPerson(id);
            if (person == null) {
                return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<String>(person.toJson(), headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> listJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        try {
            List<Person> result = personService.findAllPeople();
            return new ResponseEntity<String>(Person.toJsonArray(result), headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(method = RequestMethod.POST, headers = "Accept=application/json")
    public ResponseEntity<String> createFromJson(@RequestBody String json, UriComponentsBuilder uriBuilder) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            Person person = Person.fromJsonToPerson(json);
            personService.persist(person);
            RequestMapping a = (RequestMapping) getClass().getAnnotation(RequestMapping.class);
            headers.add("Location",uriBuilder.path(a.value()[0]+"/"+person.getId().toString()).build().toUriString());
            return new ResponseEntity<String>(headers, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(value = "/jsonArray", method = RequestMethod.POST, headers = "Accept=application/json")
    public ResponseEntity<String> createFromJsonArray(@RequestBody String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            for (Person person: Person.fromJsonArrayToPeople(json)) {
                personService.persist(person);
            }
            return new ResponseEntity<String>(headers, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.PUT, headers = "Accept=application/json")
    public ResponseEntity<String> updateFromJson(@RequestBody String json, @PathVariable("id") Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            Person person = Person.fromJsonToPerson(json);
            person.setId(id);
            if (personService.merge(person) == null) {
                return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<String>(headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, headers = "Accept=application/json")
    public ResponseEntity<String> deleteFromJson(@PathVariable("id") Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            Person person = personService.findPerson(id);
            if (person == null) {
                return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
            }
            personService.remove(person);
            return new ResponseEntity<String>(headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
