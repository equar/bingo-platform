package com.bakaroo.bingo.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    void generatedOpenApiExposesRequiredPlayerAndAuthenticationContracts() throws Exception {
        JsonNode generatedSpec = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        JsonNode adminLogin = generatedSpec.path("paths").path("/api/v1/admin/login").path("post");
        assertTrue(adminLogin.has("requestBody"));
        assertTrue(adminLogin.path("security").isArray());
        assertTrue(adminLogin.path("security").isEmpty(),
                "Admin login must be documented as unauthenticated");

        JsonNode activeSessions = generatedSpec.path("paths").path("/api/v1/sessions/active").path("get");
        assertTrue(activeSessions.isObject(), "Player active-session endpoint must remain documented");
        JsonNode sessionSchema = generatedSpec.path("components").path("schemas")
                .path("PlayerGameSessionResponse").path("properties");
        assertTrue(sessionSchema.path("maxTicketsPerPlayer").isObject(),
                "Player session contract must expose the configured ticket limit");
    }
}
