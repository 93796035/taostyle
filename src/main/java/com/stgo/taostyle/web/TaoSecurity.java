package com.stgo.taostyle.web;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.stgo.taostyle.domain.orders.TaxonomyMaterial;
import com.stgo.taostyle.service.TaxonomyMaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaoSecurity {

    @Autowired
    private TaxonomyMaterialService taxonomyMaterialService;

    public boolean isHecker(
            HttpServletRequest request) {
        HttpSession session = request.getSession();
        String ip = request.getRemoteAddr();

        // out-region ip range can be added into dangerous_ip of for_demo user.
        Object dangerous_ips = session.getAttribute("dangerous_ip");
        if (dangerous_ips != null) {
            dangerous_ips = dangerous_ips.toString().split(",");
            for (String forbiddenIp : (String[]) dangerous_ips) {
                if (ip.startsWith(forbiddenIp)) {
                    return true;
                }
            }
        }

        // internal visit can be trusted. so developer will not be interrupted.
        if (ip.startsWith("192.168.") || ip.startsWith("0:0:0:0:0:0:0:1") || ip.startsWith("127.0.0.1")) {
            return false;
        }

        // same ip might be set limitation, if not set, each ip can order 3 times a day. if set to 0, then sorry~
        int limit = 20;
        Object limitation = session.getAttribute(ip);
        if (limitation != null) {
            try {
                limit = Integer.valueOf(limitation.toString());
                if (limit <= 0) {
                    return true; // some IP is set to 0????? ok, while the ip can be used by others.....
                }
            } catch (Exception e) {
            }
        }

        List<TaxonomyMaterial> taxonomyMaterials = taxonomyMaterialService.findTaxonomyMaterialByLocation(ip);
        if (taxonomyMaterials == null || taxonomyMaterials.size() < limit) {
            return false;
        } else {
            TaxonomyMaterial taxonomyMaterial = taxonomyMaterials.get(limit - 1);
            Date date = taxonomyMaterial.getLogtime();
            if (date == null) {
                return false;
            } else if (taxonomyMaterial.getLogtime().getDay() != new Date().getDate()) {
                return false;
            }
        }

        return true;
    }
}
