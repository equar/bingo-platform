package com.bakaroo.bingo.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void checkedInOpenApiMatchesGeneratedOperationsAndSchemas() throws Exception {
        JsonNode generatedSpec = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        Path testClasses = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        Path checkedInSpecPath = testClasses.getParent().getParent().resolve("openapi.json");
        JsonNode checkedInSpec = objectMapper.readTree(checkedInSpecPath.toFile());

        assertEquals(generatedSpec.path("paths"), checkedInSpec.path("paths"),
                "Checked-in OpenAPI paths, methods, parameters, schemas, security, and responses must match the app");
        assertEquals(generatedSpec.path("components"), checkedInSpec.path("components"),
                "Checked-in OpenAPI components must match the generated contract");
        assertEquals(generatedSpec.path("security"), checkedInSpec.path("security"),
                "Checked-in OpenAPI global authentication requirements must match the application");

        JsonNode adminLogin = checkedInSpec.path("paths").path("/api/v1/admin/login").path("post");
        assertTrue(adminLogin.has("requestBody"));
        assertEquals("#/components/schemas/AdminLoginRequest",
                adminLogin.path("requestBody").path("content").path("application/json")
                        .path("schema").path("$ref").asText());
        assertEquals("#/components/schemas/AuthResponse",
                adminLogin.path("responses").path("200").path("content").path("*/*")
                        .path("schema").path("$ref").asText());
        assertTrue(adminLogin.path("security").isArray());
        assertEquals(0, adminLogin.path("security").size(),
                "Admin login must be documented as unauthenticated");
    }
}
