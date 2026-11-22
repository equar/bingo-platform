package com.bakaroo.bingo.backend.config.performance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exists only in the guarded disposable performance-test profile. */
@RestController
@Profile("performance-test")
@RequestMapping("/api/v1/admin/performance-target")
public class PerformanceTargetController {

    private final PerformanceDatabaseGuard databaseGuard;
    private final String guardToken;

    public PerformanceTargetController(
            PerformanceDatabaseGuard databaseGuard,
            @Value("${app.performance.guard-token:}") String guardToken) {
        if (guardToken == null || guardToken.length() < 32) {
            throw new IllegalStateException("Performance target guard token must contain at least 32 characters");
        }
        this.databaseGuard = databaseGuard;
        this.guardToken = guardToken;
    }

    @GetMapping
    public PerformanceTargetResponse identity(@RequestHeader("X-Performance-Guard-Token") String suppliedToken) {
        if (!matchesGuardToken(guardToken, suppliedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Performance target guard token is invalid");
        }
        PerformanceDatabaseGuard.TargetIdentity identity = databaseGuard.requireVerifiedIdentity();
        return new PerformanceTargetResponse(
                true,
                "performance-test",
                identity.schemaName(),
                identity.databaseAccount().substring(0, identity.databaseAccount().indexOf('@')),
                identity.mysqlPort(),
                identity.mysqlServerUuid(),
                identity.hikariPoolMaximum(),
                identity.approvedMaximumConnections());
    }

    static boolean matchesGuardToken(String expected, String supplied) {
        return expected != null && expected.length() >= 32 && supplied != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8));
    }

    public record PerformanceTargetResponse(
            boolean disposable,
            String profile,
            String schemaName,
            String databaseAccount,
            int mysqlPort,
            String mysqlServerUuid,
            int hikariPoolMaximum,
            int approvedMaximumConnections) { }
}
