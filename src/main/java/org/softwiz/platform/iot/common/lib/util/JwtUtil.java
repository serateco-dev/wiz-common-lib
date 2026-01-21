package org.softwiz.platform.iot.common.lib.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT 토큰 생성 및 검증 유틸리티 (SSO)
 *
 * 설계 원칙:
 * - JWT Claims: 전부 평문 저장
 * - 암호화는 프론트 응답 시에만 (AuthService에서 처리)
 * - userNo: 평문 숫자
 * - userId: 평문 이메일
 * - auth: 복합 권한 (숫자/문자열 혼합 가능, String으로 통일)
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "jwt",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false  // 설정이 없으면 빈을 생성하지 않음
)
public class JwtUtil {


    private CryptoUtil cryptoUtil;

    public void setCryptoUtil(CryptoUtil cryptoUtil) {
        this.cryptoUtil = cryptoUtil;
    }

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}")  // 기본값: 1시간
    private long expiration;

    @Value("${jwt.refresh-expiration:86400000}")  // 기본값: 24시간
    private long refreshExpiration;

    @Value("${jwt.issuer:iot-platform}")  // 기본값: iot-platform
    private String issuer;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ========================================
    // 토큰 생성
    // ========================================

    /**
     * Access Token 생성
     *
     * @param userNo 사용자 시스템 번호 (예: 12345)
     * @param userId 이메일 주소 또는 "provider:id" (예: "user@example.com", "KAKAO:123456")
     * @param serviceId 서비스 ID
     * @param role 권한
     * @param auth 복합 권한 리스트 (숫자, 문자열 혼합 가능 - 예: [1, 2, "ADMIN", "USER"])
     * @param nickName 닉네임
     * @param provider 로그인 제공자 (EMAIL, KAKAO, GOOGLE 등)
     * @param accessibleApi 접근 가능 API 목록
     * @param additionalClaims 추가 클레임
     * @return JWT Access Token
     */
    public String generateAccessToken(
            Long userNo,
            String userId,
            String serviceId,
            String role,
            List<?> auth,
            String nickName,
            String provider,
            List<String> accessibleApi,
            Map<String, Object> additionalClaims
    ) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        // 지시사항: userId 암호화 처리 (방어적 체크)
        String subjectValue = (cryptoUtil != null && StringUtils.hasText(userId))
                ? cryptoUtil.encryptUserId(userId) : userId;

        JwtBuilder builder = Jwts.builder()
                .subject(subjectValue)                  // 암호화된 이메일 또는 "KAKAO:123456"
                .claim("userNo", userNo)                // 평문 숫자
                .claim("serviceId", serviceId)
                .claim("role", role)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey(), Jwts.SIG.HS256);

        // 복합 권한 (숫자/문자열 혼합 가능)
        if (auth != null && !auth.isEmpty()) {
            builder.claim("auth", auth);
        }

        if (StringUtils.hasText(nickName)) {
            builder.claim("nickName", nickName);
        }

        if (StringUtils.hasText(provider)) {
            builder.claim("provider", provider);
        }

        // accessibleApi 처리
        if (accessibleApi == null || accessibleApi.isEmpty()) {
            builder.claim("accessibleApi", List.of("all"));
        } else {
            builder.claim("accessibleApi", accessibleApi);
        }

        // 추가 클레임
        if (additionalClaims != null && !additionalClaims.isEmpty()) {
            additionalClaims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * Access Token 생성 (간소화 버전)
     */
    public String generateAccessToken(
            Long userNo,
            String userId,
            String serviceId,
            String role,
            List<String> accessibleApi,
            Map<String, Object> additionalClaims
    ) {
        return generateAccessToken(userNo, userId, serviceId, role, null, null, null, accessibleApi, additionalClaims);
    }

    /**
     * Refresh Token 생성 (기본 유효기간)
     *
     * @param userNo 사용자 시스템 번호
     * @param userId 이메일 주소 또는 "provider:id" (NULL 가능)
     * @return JWT Refresh Token
     */
    public String generateRefreshToken(Long userNo, String userId) {
        return generateRefreshToken(userNo, userId, refreshExpiration);
    }

    /**
     * Refresh Token 생성 (유효기간 지정)
     *
     * @param userNo 사용자 시스템 번호
     * @param userId 이메일 주소 또는 "provider:id" (NULL 가능)
     * @param expirationTime 유효기간 (밀리초)
     * @return JWT Refresh Token
     */
    public String generateRefreshToken(Long userNo, String userId, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userNo))        // Refresh Token은 userNo를 subject로
                .claim("userNo", userNo)
                .claim("tokenType", "refresh")
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey(), Jwts.SIG.HS256);

        if (StringUtils.hasText(userId)) {
            // 지시사항: userId 암호화 처리
            String encryptedId = (cryptoUtil != null) ? cryptoUtil.encryptUserId(userId) : userId;
            builder.claim("userId", encryptedId);            // 선택적으로 userId 추가
        }

        return builder.compact();
    }

    // ========================================
    // Claims 추출
    // ========================================

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // ========================================
    // 사용자 정보 추출
    // ========================================

    /**
     * userNo 추출 (시스템 번호)
     * @return userNo (예: 12345)
     */
    public Long extractUserNo(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Number userNo = claims.get("userNo", Number.class);
            return userNo != null ? userNo.longValue() : null;
        } catch (Exception e) {
            log.warn("Failed to extract userNo from token", e);
            return null;
        }
    }

    /**
     * userId 추출 (이메일 또는 provider:id)
     * @return userId (예: "user@example.com" 또는 "KAKAO:123456")
     */
    public String extractUserId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String subject = claims.getSubject();

            // 지시사항: 복호화 처리
            if (cryptoUtil != null && StringUtils.hasText(subject)) {
                return cryptoUtil.decryptUserId(subject);
            }
            return subject;
        } catch (Exception e) {
            log.warn("Failed to extract userId from token", e);
            return null;
        }
    }

    /**
     * 닉네임 추출
     */
    public String extractNickName(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("nickName", String.class);
        } catch (Exception e) {
            log.warn("Failed to extract nickName from token", e);
            return null;
        }
    }

    // ========================================
    // 권한/역할 정보 추출
    // ========================================

    /**
     * 복합 권한 추출 (숫자/문자열 혼합 가능)
     *
     *
     * @param token JWT 토큰
     * @return 권한 리스트 (모두 String으로 변환)
     */
    public List<String> extractAuth(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Object authObj = claims.get("auth");

            if (authObj instanceof List<?> list) {
                return list.stream()
                        .filter(obj -> obj != null)
                        .map(obj -> {
                            // 숫자는 String으로 변환
                            if (obj instanceof Number) {
                                return String.valueOf(((Number) obj).longValue());
                            }
                            // 문자열은 그대로
                            if (obj instanceof String) {
                                return (String) obj;
                            }
                            // 기타 타입은 toString()
                            return obj.toString();
                        })
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to extract auth from token", e);
            return List.of();
        }
    }

    public String extractRole(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.warn("Failed to extract role from token", e);
            return null;
        }
    }

    /**
     * 접근 가능 API 목록 추출
     *
     * @param token JWT 토큰
     * @return API 목록 (없으면 ["all"])
     */
    public List<String> extractAccessibleApi(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Object apiObj = claims.get("accessibleApi");

            if (apiObj instanceof List<?> list) {
                return list.stream()
                        .filter(obj -> obj instanceof String)
                        .map(obj -> (String) obj)
                        .toList();
            }
            return List.of("all");
        } catch (Exception e) {
            log.warn("Failed to extract accessibleApi from token", e);
            return List.of("all");
        }
    }

    // ========================================
    // 서비스/디바이스 정보 추출
    // ========================================

    public String extractServiceId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("serviceId", String.class);
        } catch (Exception e) {
            log.warn("Failed to extract serviceId from token", e);
            return null;
        }
    }

    public String extractProvider(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("provider", String.class);
        } catch (Exception e) {
            log.warn("Failed to extract provider from token", e);
            return null;
        }
    }


    public String extractDeviceCd(String token) {
        try {
            String deviceCd = extractClaim(token, claims -> claims.get("deviceCd", String.class));
            return StringUtils.hasText(deviceCd) ? deviceCd : null;
        } catch (Exception e) {
            log.warn("Failed to extract deviceCd from token", e);
            return null;
        }
    }

    public String extractDeviceStr(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("deviceStr", String.class);
        } catch (Exception e) {
            log.warn("Failed to extract deviceStr from token", e);
            return null;
        }
    }

    // ========================================
    // 토큰 메타 정보 추출
    // ========================================

    public Date extractExpiration(String token) {
        try {
            return extractAllClaims(token).getExpiration();
        } catch (Exception e) {
            log.warn("Failed to extract expiration from token", e);
            return null;
        }
    }

    public Date extractIssuedAt(String token) {
        try {
            return extractAllClaims(token).getIssuedAt();
        } catch (Exception e) {
            log.warn("Failed to extract issuedAt from token", e);
            return null;
        }
    }

    // ========================================
    // 토큰 검증
    // ========================================

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException |
                 io.jsonwebtoken.security.SecurityException | IllegalArgumentException e) {
            log.warn("JWT validation error: {}", e.getMessage());
            throw e;
        }
    }

    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            validateToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration != null && expiration.before(new Date());
        } catch (JwtException e) {
            log.warn("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String tokenType = claims.get("tokenType", String.class);
            return "refresh".equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }
}