package com.saas.auditcompliance.utility;

import com.saas.auditcompliance.config.RoleConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PermissionUtil {

    private final RoleConverter roleConverter;
    private final SecurityUtil securityUtil;

    public boolean hasPermission(UUID tenantId, String resourceName) {

        Jwt jwt = securityUtil.getUserJwt();
        Collection<GrantedAuthority> userRoles = roleConverter.extractAuthorities(jwt);
        this.isTenantUser(tenantId);
        return userRoles.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("admin"));
    }

    public boolean isAdmin() {

        Jwt jwt = securityUtil.getUserJwt();
        Collection<GrantedAuthority> userRoles = roleConverter.extractAuthorities(jwt);
        for (GrantedAuthority authority : userRoles) {
            if (authority.getAuthority().equals("admin")) {
                return true;
            }
        }
        return false;
    }

    public void isTenantUser(UUID tenantId) {

        String userTenantId = securityUtil.getTenantId();
        if (userTenantId != null && userTenantId.equals(tenantId.toString())) {
            return;
        }
        throw new AccessDeniedException(
                "Access Denied - You are not associated with the specified tenant");
    }
}