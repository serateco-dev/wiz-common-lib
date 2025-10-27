package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES256 암호화/복호화 유틸리티
 * - 게이트웨이에서 userId를 암호화하여 마이크로서비스로 전달
 * - 마이크로서비스에서 암호화된 userId를 복호화하여 사용
 *
 * 보안 고려사항:
 * - AES-256-CBC 모드 사용
 * - 고정 IV 사용 (Gateway-MSA 간 제한된 통신에서 실용적)
 * - 더 높은 보안이 필요하면 랜덤 IV + 암호문 함께 전송 방식 고려
 */
@Slf4j
@Component
public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 32; // AES-256: 32 bytes
    private static final int IV_SIZE = 16;  // AES 블록 크기: 16 bytes

    private final SecretKeySpec secretKeySpec;
    private final IvParameterSpec ivParameterSpec;

    public CryptoUtil(
            @Value("${crypto.secret-key}") String secretKey,
            @Value("${crypto.iv}") String iv) {

        // 키 길이 검증 (AES-256은 32바이트 필요)
        if (secretKey.getBytes(StandardCharsets.UTF_8).length != KEY_SIZE) {
            throw new IllegalArgumentException(
                    "Secret key must be " + KEY_SIZE + " bytes for AES-256");
        }

        this.secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                ALGORITHM
        );

        // IV는 16바이트 (AES 블록 크기)
        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        if (ivBytes.length != IV_SIZE) {
            throw new IllegalArgumentException("IV must be " + IV_SIZE + " bytes");
        }
        this.ivParameterSpec = new IvParameterSpec(ivBytes);
    }

    /**
     * AES256 암호화
     * @param plainText 평문
     * @return Base64 인코딩된 암호화 문자열
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] encryptedBytes = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            // Base64 인코딩
            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Encryption error details", e);
            }
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * AES256 복호화
     * @param encrypted Base64 인코딩된 암호화 문자열
     * @return 복호화된 평문
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }

        try {
            // Base64 디코딩
            byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);

            // AES256 복호화
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Failed encrypted value length: {}, preview: {}...",
                        encrypted.length(),
                        encrypted.substring(0, Math.min(10, encrypted.length())));
            }
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * userId 암호화 (Gateway -> 헤더 전달 -> MSA에서 활용)
     * @param userId 평문 userId
     * @return 암호화된 userId
     * @throws IllegalArgumentException userId가 null이거나 비어있을 때
     * @throws IllegalStateException 암호화 실패 시
     */
    public String encryptUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }

        try {
            String encrypted = encrypt(userId);
            if (encrypted == null) {
                throw new IllegalStateException("Encryption returned null for userId");
            }
            return encrypted;
        } catch (RuntimeException e) {
            // encrypt()에서 이미 로그 출력됨
            throw new IllegalStateException("Failed to encrypt userId", e);
        }
    }

    /**
     * userId 복호화 (MSA에서 사용)
     * @param encryptedUserId 암호화된 userId
     * @return 평문 userId
     * @throws IllegalArgumentException encryptedUserId가 null이거나 비어있을 때
     * @throws IllegalStateException 복호화 실패 시
     */
    public String decryptUserId(String encryptedUserId) {
        if (encryptedUserId == null || encryptedUserId.isEmpty()) {
            throw new IllegalArgumentException("encryptedUserId cannot be null or empty");
        }

        try {
            String decrypted = decrypt(encryptedUserId);
            if (decrypted == null) {
                throw new IllegalStateException("Decryption returned null for userId");
            }
            return decrypted;
        } catch (RuntimeException e) {
            // decrypt()에서 이미 로그 출력됨
            throw new IllegalStateException("Failed to decrypt userId", e);
        }
    }
}