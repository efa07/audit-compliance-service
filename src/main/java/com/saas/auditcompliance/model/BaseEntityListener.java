package com.saas.auditcompliance.model;

import com.saas.auditcompliance.utility.SecurityUtil;
import com.saas.auditcompliance.utility.SpringContextHolder;
import jakarta.persistence.PrePersist;

import java.util.UUID;

public class BaseEntityListener {

    @PrePersist
    public void setTenantId(Base entity) {
        if (entity.getTenantId() == null) {
            SecurityUtil securityUtil = SpringContextHolder.getBean(SecurityUtil.class);
            entity.setTenantId(UUID.fromString(securityUtil.getTenantId()));
        }
    }
}