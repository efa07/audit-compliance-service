package com.saas.auditcompliance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Value("${keycloak.openIdConnectUrl:http://localhost:8080/realms/master/.well-known/openid-configuration}")
        private String keycloakOpenIdConnectUrl;

        @Value("${api-gateway-base-url:http://localhost:9192}")
        private String apiGatewayBaseUrl;

        @Value("${server.port:9192}")
        private String serverPort;

        @Value("${spring.application.name:budget-management-service}")
        private String applicationName;

        @Bean
        public OpenAPI customOpenAPI() {

                String apiGatewayBaseUrl = this.apiGatewayBaseUrl;
                String localhostUrl  = "http://localhost:" + this.serverPort;
                String applicationName = this.capitalizeApplicationName(this.applicationName);

                return new OpenAPI()
                        .info(new Info()
                                .title(applicationName + " APIs")
                                .version("1.0")
                                .description("SaaS ERP " + applicationName +" APIs")
                                .termsOfService("Terms of service")
                                .contact(new Contact()
                                        .name("Owner name")
                                        .email("Owner email address")
                                        .url("Owner website url"))
                                .license(new License().name("License name").url("License url")))
                        .addServersItem(new Server().url(localhostUrl).description("Local ENV"))
                        .addServersItem(new Server().url(apiGatewayBaseUrl).description("Remote ENV"))
                        .addSecurityItem(new SecurityRequirement().addList("Keycloak"))
                        .schemaRequirement(
                                "Keycloak",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OPENIDCONNECT)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .openIdConnectUrl(keycloakOpenIdConnectUrl));
        }

        @Bean
        public OperationCustomizer customizeTenantId() {
                return (operation, handlerMethod) -> {
                        if (operation.getParameters() != null) {
                                for (Parameter parameter : operation.getParameters()) {
                                        if ("tenantId".equals(parameter.getName())) {
                                                parameter.setExample("3fa85f64-5717-4562-b3fc-2c963f66afa6");
                                                if (parameter.getSchema() != null) {
                                                        parameter.getSchema().setDefault("3fa85f64-5717-4562-b3fc-2c963f66afa6");
                                                }
                                        }
                                }
                        }
                        return operation;
                };
        }

        private String capitalizeApplicationName(String applicationName) {
                String[] words = applicationName.split("-");
                StringBuilder capitalized = new StringBuilder();
                for (String word : words) {
                        if (!word.isEmpty()) {
                                capitalized
                                        .append(Character.toUpperCase(word.charAt(0)))
                                        .append(word.substring(1).toLowerCase())
                                        .append(" ");
                        }
                }
                return capitalized.toString().trim();
        }
}
