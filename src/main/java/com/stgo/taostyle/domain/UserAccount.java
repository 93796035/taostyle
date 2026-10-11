package com.stgo.taostyle.domain;

import java.util.Collection;
import java.util.List;
import javax.persistence.*;

import com.stgo.taostyle.web.TaoDebug;
import com.stgo.taostyle.web.TaoEmail;
import com.stgo.taostyle.web.TaoUtil;
import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;

@Entity
public class UserAccount {
    public static final List<String> fieldNames4OrderClauseFilter = java.util.Arrays.asList("loginname", "password", "cel", "tel", "fax", "email", "companyname", "address", "city", "postcode", "credit", "balance", "description", "securitylevel", "person", "recordStatus");

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

    private String loginname;
    private String password;
    private String cel;
    private String tel;
    private String fax;
    private String email;
    private String companyname;
    private String address;
    private String city;
    private String postcode;
    private int credit;
    private int balance;
    private String description;
    private String securitylevel;

    @ManyToOne
    private Person person;

    private int recordStatus;

    // Getter and Setter methods
    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return this.version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getLoginname() {
        return this.loginname;
    }

    public void setLoginname(String loginname) {
        this.loginname = loginname;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCel() {
        return this.cel;
    }

    public void setCel(String cel) {
        this.cel = cel;
    }

    public String getTel() {
        return this.tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public String getFax() {
        return this.fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCompanyname() {
        return this.companyname;
    }

    public void setCompanyname(String companyname) {
        this.companyname = companyname;
    }

    public String getAddress() {
        return this.address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return this.city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostcode() {
        return this.postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public int getCredit() {
        return this.credit;
    }

    public void setCredit(int credit) {
        this.credit = credit;
    }

    public int getBalance() {
        return this.balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSecuritylevel() {
        return this.securitylevel;
    }

    public void setSecuritylevel(String securitylevel) {
        this.securitylevel = securitylevel;
    }

    public Person getPerson() {
        return this.person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public int getRecordStatus() {
        return this.recordStatus;
    }

    public void setRecordStatus(int recordStatus) {
        this.recordStatus = recordStatus;
    }

    // JSON serialization methods
    public String toJson() {
        return new JSONSerializer().exclude("*.class").serialize(this);
    }

    public String toJson(String[] fields) {
        return new JSONSerializer().include(fields).exclude("*.class").serialize(this);
    }

    public static UserAccount fromJsonToUserAccount(String json) {
        return new JSONDeserializer<UserAccount>().use(null, UserAccount.class).deserialize(json);
    }

    public static String toJsonArray(Collection<UserAccount> collection) {
        return new JSONSerializer().exclude("*.class").serialize(collection);
    }

    public static String toJsonArray(Collection<UserAccount> collection, String[] fields) {
        return new JSONSerializer().include(fields).exclude("*.class").serialize(collection);
    }

    public static Collection<UserAccount> fromJsonArrayToUserAccounts(String json) {
        return new JSONDeserializer<List<UserAccount>>().use("values", UserAccount.class).deserialize(json);
    }

    @Override
    public String toString() {
        return this.getLoginname();
    }
}
