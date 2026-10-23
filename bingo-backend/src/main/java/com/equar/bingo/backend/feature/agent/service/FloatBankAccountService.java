package com.equar.bingo.backend.feature.agent.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.agent.dto.FloatBankAccountRequest;
import com.equar.bingo.backend.feature.agent.dto.FloatBankAccountResponse;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FloatBankAccountService {
    private static final String SELECT_COLUMNS = "id, bank_name, masked_account_number, account_holder_name, currency, active, player_deposits_enabled, created_at, updated_at";
    private final JdbcTemplate jdbc;
    private final byte[] encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public FloatBankAccountService(JdbcTemplate jdbc,
            @Value("${app.agent.bank-account-encryption-key:}") String encodedKey) {
        this.jdbc = jdbc;
        byte[] parsed = new byte[0];
        if (encodedKey != null && !encodedKey.isBlank()) {
            try {
                parsed = Base64.getDecoder().decode(encodedKey.trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Bank account encryption key must be Base64", exception);
            }
            if (parsed.length != 32) {
                throw new IllegalStateException("Bank account encryption key must decode to 32 bytes");
            }
        }
        this.encryptionKey = parsed;
    }

    @Transactional
    public FloatBankAccountResponse create(BingoUserPrincipal principal, FloatBankAccountRequest request) {
        requireRole(principal.getUser(), UserRole.ADMIN);
        if (!"ETB".equals(request.currency())) {
            throw new BadRequestException("UNSUPPORTED_CURRENCY", "Float receiving accounts must use ETB");
        }
        String normalized = request.accountNumber().replaceAll("[ -]", "");
        String encrypted = encrypt(normalized);
        String masked = "••••" + normalized.substring(Math.max(0, normalized.length() - 4));
        Instant now = Instant.now();
        jdbc.update("""
                insert into float_receiving_bank_accounts
                (bank_name, account_number_ciphertext, masked_account_number, account_holder_name, currency,
                 active, created_by_user_id, updated_by_user_id, created_at, updated_at)
                values (?, ?, ?, ?, 'ETB', true, ?, ?, ?, ?)
                """, request.bankName().trim(), encrypted, masked, request.accountHolderName().trim(),
                principal.getUser().getId(), principal.getUser().getId(), Timestamp.from(now), Timestamp.from(now));
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        return find(id);
    }

    @Transactional
    public FloatBankAccountResponse updateActiveStatus(BingoUserPrincipal principal, Long id, boolean active) {
        requireRole(principal.getUser(), UserRole.ADMIN);
        int updated = jdbc.update("update float_receiving_bank_accounts set active=?, updated_by_user_id=?, updated_at=? where id=?",
                active, principal.getUser().getId(), Timestamp.from(Instant.now()), id);
        if (updated != 1) throw new NotFoundException("FLOAT_BANK_ACCOUNT_NOT_FOUND", "Receiving account not found");
        return find(id);
    }

    @Transactional
    public FloatBankAccountResponse updatePlayerDepositEligibility(BingoUserPrincipal principal, Long id,
            boolean enabled) {
        requireRole(principal.getUser(), UserRole.ADMIN);
        FloatBankAccountResponse account = find(id);
        if (enabled && !account.active()) {
            throw new ConflictException("FLOAT_BANK_ACCOUNT_INACTIVE",
                    "An inactive receiving account cannot accept player deposits");
        }
        jdbc.update("""
                update float_receiving_bank_accounts
                set player_deposits_enabled=?, updated_by_user_id=?, updated_at=?
                where id=?
                """, enabled, principal.getUser().getId(), Timestamp.from(Instant.now()), id);
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<FloatBankAccountResponse> activeAccounts() {
        return jdbc.query("select " + SELECT_COLUMNS + " from float_receiving_bank_accounts where active=true order by bank_name, id",
                this::map);
    }

    @Transactional(readOnly = true)
    public List<FloatBankAccountResponse> allAccounts() {
        return jdbc.query("select " + SELECT_COLUMNS + " from float_receiving_bank_accounts order by id", this::map);
    }

    @Transactional(readOnly = true)
    public FloatBankAccountResponse requireActive(Long id, String currency) {
        FloatBankAccountResponse account = find(id);
        if (!account.active()) throw new ConflictException("FLOAT_BANK_ACCOUNT_INACTIVE", "Receiving bank account is inactive");
        if (!account.currency().equalsIgnoreCase(currency)) {
            throw new BadRequestException("CURRENCY_MISMATCH", "Receiving bank account currency must match float currency");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public FloatBankAccountResponse find(Long id) {
        return jdbc.query("select " + SELECT_COLUMNS + " from float_receiving_bank_accounts where id=?",
                this::map, id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("FLOAT_BANK_ACCOUNT_NOT_FOUND", "Receiving account not found"));
    }

    private String encrypt(String value) {
        if (encryptionKey.length != 32) {
            throw new ConflictException("BANK_ACCOUNT_ENCRYPTION_NOT_CONFIGURED",
                    "Receiving bank account management is unavailable until encryption is configured");
        }
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect receiving bank account number", exception);
        }
    }

    private FloatBankAccountResponse map(ResultSet rs, int row) throws SQLException {
        return new FloatBankAccountResponse(rs.getLong("id"), rs.getString("bank_name"),
                rs.getString("masked_account_number"), rs.getString("account_holder_name"),
                rs.getString("currency"), rs.getBoolean("active"), rs.getBoolean("player_deposits_enabled"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private void requireRole(User user, UserRole role) {
        if (!user.hasRole(role) || !user.isActive()) {
            throw new ConflictException("FINANCE_AUTHORIZATION_REQUIRED", "An active authorized user is required");
        }
    }
}
