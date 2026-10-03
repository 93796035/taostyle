
package com.stgo.taostyle.domain.orders;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Version;

import com.stgo.taostyle.web.CC;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;

@Entity
@Data
public class MainOrder {

    @ManyToOne
    private UserAccount client;

    private String clientSideOrderNumber;

    private String clientSideModelNumber;

    private String model;

    @ManyToOne
    private MediaUpload picture;

    private String productName;

    @ManyToOne
    private UserAccount contactPerson;

    private Integer quantity;

    private String payCondition;

    private String total_after_tax;

    private Integer colorProportionReceived;

    @Temporal(TemporalType.TIMESTAMP)
    @DateTimeFormat(style = "M-")
    private Date delieverdate;

    private String sizeTable;

    private String colorCard;

    private String testRequirement;

    private String sampleRequirement;

    private String Remark;

    @ManyToOne
    private Person person;

    private Integer recordStatus;


	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

	@Version
    @Column(name = "version")
    private Integer version;

    public String getRecordStatusText() {
        switch (this.recordStatus) {
            case 0:
                return "Ordered";
            case CC.STATUS_CHANGED_PLACE:
                return "Modified";
            case CC.STATUS_TO_PRINT:
                return "Prepared";
            case CC.STATUS_PRINTED:
                return "Delivering";
            case CC.STATUS_PAID:
                return "Paid";
            case CC.LEVEL_FULL:
                return "Delivering";
            case -1:
                return "Completed";
            default:
                return "Unknown";
        }
    }
    public String getTotalInYuan() {
        try {
            return String.format("%.2f", Integer.parseInt(total_after_tax.replaceAll("[^\\d]", "")) / 100.0);
        } catch (Exception e) {
            return "0.00";
        }
    }
}
