package com.saas.auditcompliance.utility;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class SecurityUtil {

    public Jwt getUserJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Jwt) authentication.getPrincipal();
    }

    public String getUserToken() {
        return String.format("Bearer %s", getUserJwt().getTokenValue());
    }

    public String getUserId() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getSubject();
    }

    public String getClaim(String claim) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return jwt.hasClaim(claim) ? jwt.getClaim(claim).toString() : null;
    }

    public String getTenantId() {
        String tenantId = getClaim("tenantId");
        return tenantId != null ? tenantId : "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    }

    public String getName() {
        return getClaim("name");
    }

    public String getCurrentUserId() {
        return getUserId();
    }
}