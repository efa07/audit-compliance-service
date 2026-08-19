package com.saas.budgetmanagement.security;

import com.saas.budgetmanagement.utility.PermissionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantAccessInterceptor implements HandlerInterceptor {

    private final PermissionUtil permissionUtil;

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        Map<String, String> pathVariables =
                (Map<String, String>) request.getAttribute(
                        org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVariables == null || !pathVariables.containsKey("tenantId")) {
            return true;
        }

        UUID tenantId = UUID.fromString(pathVariables.get("tenantId"));
        permissionUtil.isTenantUser(tenantId);
        return true;
    }
}