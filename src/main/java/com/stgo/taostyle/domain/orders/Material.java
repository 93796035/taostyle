package com.stgo.taostyle.domain.orders;

import com.stgo.taostyle.domain.Person;
import lombok.Data;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Data
public class Material {

    @ManyToOne
    private MainOrder mainOrder;

    @NotNull
    private String portionName;

    private Integer endSubTotal;

    private String menFu;

    private String color;

    private String location;

    private String remark;

    private Integer quantity;

    @ManyToOne
    private Person person;

    private int recordStatus;

	public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

	@Version
    private Integer version;

    public String getSubTotalInYuan() {
        if (endSubTotal == null) return "0.00";
        return String.format("%.2f", endSubTotal / 100.0);
    }

    public void setSubTotalInYuan(String subTotalInYuan) {
        this.endSubTotal = (int)(Double.parseDouble(subTotalInYuan) * 100);
    }
}
