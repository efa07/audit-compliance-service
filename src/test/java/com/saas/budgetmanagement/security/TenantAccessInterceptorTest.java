package com.saas.budgetmanagement.security;

import com.saas.budgetmanagement.service.BudgetPlanService;
import com.saas.budgetmanagement.utility.PermissionUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantAccessInterceptorTest {

    private static final UUID TENANT_A = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UUID TENANT_B = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID USER_A_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");

    @Autowired
    private TenantAccessInterceptor tenantAccessInterceptor;

    @Autowired
    private PermissionUtil permissionUtil;

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private BudgetPlanService budgetPlanService;

    @Test
    @DisplayName("Interceptor should throw AccessDeniedException when path tenantId does not match user's JWT tenant claim")
    void preHandle_shouldThrowAccessDeniedException_whenTenantIdMismatches() {
        // Arrange: User is authenticated for Tenant A
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("tenantId", TENANT_A.toString())
                .claim("realm_access", Map.of("roles", List.of("admin")))
                .subject(USER_A_ID.toString())
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("admin")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            HandlerMethod handler = mock(HandlerMethod.class);

            // Set path variable tenantId to Tenant B
            request.setAttribute(
                    HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                    Map.of("tenantId", TENANT_B.toString())
            );

            // Act & Assert: Should throw AccessDeniedException
            assertThatThrownBy(() -> tenantAccessInterceptor.preHandle(request, response, handler))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Access Denied");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Interceptor should block execution end-to-end and ensure underlying service method is never invoked")
    void httpCall_shouldReturn403AndNeverInvokeService_whenTenantIdMismatches() throws Exception {
        // Act: Make GET request for Tenant B path while authenticated as Tenant A
        mockMvc.perform(get("/api/budget-management/budget-plans/" + TENANT_B)
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isForbidden());

        // Assert: The controller/service method was never invoked
        verify(budgetPlanService, never()).list(any(), any(), any(Pageable.class));
    }
}
