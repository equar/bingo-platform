package com.equar.bingo.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/admin/login",
            "/api/v1/agent/login");

    @Bean
    public OpenAPI openAPI() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("Bingo Backend API").version("v1")
                        .description("REST API plus live draw WebSocket at ws://<host>/ws or wss://<host>/ws. "
                                + "Authenticate STOMP CONNECT frames with the HTTP JWT as a Bearer token. "
                                + "Subscribe to /topic/session/{sessionId}/draws for live draw events."))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer apiErrorResponses() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations()
                .forEach(operation -> {
                    addErrorResponse(operation, "400", "Invalid request");
                    addErrorResponse(operation, "401", "Authentication required or credentials invalid");
                    addErrorResponse(operation, "404", "Resource not found");
                    addErrorResponse(operation, "409", "Request conflicts with current state");
                    addErrorResponse(operation, "500", "Unexpected server error");
                    if (!PUBLIC_PATHS.contains(path)) {
                        addErrorResponse(operation, "403", "Access denied");
                    }
                }));
    }

    private void addErrorResponse(Operation operation, String status, String description) {
        operation.getResponses().put(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse")))));
    }
}