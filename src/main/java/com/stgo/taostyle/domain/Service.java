package com.stgo.taostyle.domain;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import com.stgo.taostyle.web.CC;
import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Entity
@Data  // 自动生成 Getter、Setter、toString、equals、hashCode
@NoArgsConstructor  // 自动生成无参构造方法
@AllArgsConstructor // 自动生成全参构造方法
@ToString(exclude = {"person"}) // 避免 toString 时的循环依赖
@EqualsAndHashCode(of = "id")   // 以 id 作为 equals/hashCode 的依据
public class Service implements MultiLanguageabl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

    private String name;
    private String description;
    private String c1;
    private String c2;
    private String c3;
    private Integer price;
    private Integer cost;
    private Integer stock;
    private Integer weeklySalesQty;
    private Integer monthlySalesQty;

    @ManyToOne
    private Person person;

    private int recordStatus;

    @Transient
    private String localName;

    @Transient
    private String localDescription;

    // JSON 序列化方法
    public String toJson() {
        return new JSONSerializer().exclude("*.class").serialize(this);
    }

    public static Service fromJsonToService(String json) {
        return new JSONDeserializer<Service>().use(null, Service.class).deserialize(json);
    }

    public static String toJsonArray(Collection<Service> collection) {
        return new JSONSerializer().exclude("*.class").serialize(collection);
    }

    public static Collection<Service> fromJsonArrayToServices(String json) {
        return new JSONDeserializer<List<Service>>().use(null, ArrayList.class).use("values", Service.class).deserialize(json);
    }
}
