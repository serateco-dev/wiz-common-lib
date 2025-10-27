# WIZ-COMMON-LIB

[![](https://jitpack.io/v/yourusername/wiz-lib.svg)](https://jitpack.io/#yourusername/wiz-lib)

Common utilities and components for WIZ Platform microservices.

## ✨ 특징

- ✅ **자동 배포** - Tag만 푸시하면 JitPack이 자동 빌드
- ✅ **간편한 통합** - pom.xml에 3줄만 추가
- ✅ **검증된 보안** - Gateway 서명 검증, JWT, AES-256 암호화
- ✅ **마이크로서비스 최적화** - Spring Cloud Gateway 연동

---

## 📦 빠른 시작

### 1. pom.xml에 추가

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.yourusername</groupId>
        <artifactId>wiz-lib</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### 2. application.yml 설정

```yaml
# JWT 설정
jwt:
  secret: ${JWT_SECRET}
  expiration: 3600000
  refresh-expiration: 604800000
  issuer: WIZ-PLATFORM

# 암호화 설정
crypto:
  secret-key: ${CRYPTO_SECRET_KEY}  # 32 bytes
  iv: ${CRYPTO_IV}                  # 16 bytes

# Gateway 서명 검증
gateway:
  signature:
    enabled: true
    secret: ${GATEWAY_SECRET}
    timeout: 60

# 공개 경로
security:
  public-paths:
    - /health
    - /actuator/**
```

### 3. 코드에서 사용

```java
import org.softwiz.platform.iot.wizlib.context.GatewayContext;

@RestController
public class UserController {
    
    @GetMapping("/api/user/me")
    public UserInfo getCurrentUser() {
        // Gateway에서 전달된 사용자 정보
        String userId = GatewayContext.getCurrentUserId();
        String role = GatewayContext.getCurrentRole();
        
        return UserInfo.builder()
                .userId(userId)
                .role(role)
                .build();
    }
}
```

**끝!** 🎉

---

## 🔧 주요 기능

### 1. Gateway Context 관리
ThreadLocal 기반 사용자 컨텍스트로 어디서든 사용자 정보 접근
```java
String userId = GatewayContext.getCurrentUserId();
boolean hasAdmin = GatewayContext.currentHasAuth("ADMIN");
```

### 2. JWT 토큰 처리
토큰 생성, 검증, 파싱을 한 곳에서
```java
String token = jwtUtil.generateAccessToken(userNo, userId, serviceId, ...);
boolean valid = jwtUtil.isTokenValid(token);
```

### 3. AES-256 암호화
Gateway와 마이크로서비스 간 안전한 데이터 전송
```java
String encrypted = cryptoUtil.encryptUserId(userId);
String decrypted = cryptoUtil.decryptUserId(encrypted);
```

### 4. 자동 로깅 & 마스킹
민감정보 자동 마스킹으로 GDPR 준수
```java
log.info("User: {}", maskingUtil.maskEmail(email));
```

### 5. Gateway 서명 검증
HMAC-SHA256으로 직접 호출 방지
```java
// 자동으로 검증 (GatewayHeaderInterceptor)
```

---



## 🏗️ 아키텍처

```
API Gateway (Spring Cloud Gateway)
    │
    ├─ JWT 검증
    ├─ CORS 처리
    ├─ userId 암호화 (X-User-Id 헤더)
    └─ HMAC 서명 추가
    
    ↓ (Gateway 헤더 전달)
    
마이크로서비스 (WIZ-LIB 사용)
    │
    ├─ GatewayHeaderInterceptor
    │   ├─ HMAC 서명 검증
    │   ├─ userId 복호화
    │   └─ GatewayContext 설정
    │
    └─ 비즈니스 로직
        └─ GatewayContext.getCurrent() 사용
```

---

## 🎯 API 문서

### GatewayContext

| 메서드 | 설명 |
|--------|------|
| `getCurrentUserId()` | 현재 사용자 ID |
| `getCurrentRole()` | 현재 역할 |
| `getCurrentAuth()` | 현재 권한 리스트 |
| `currentHasAuth(String)` | 특정 권한 보유 여부 |
| `currentHasAnyAuth(String...)` | 여러 권한 중 하나 보유 |
| `currentHasAllAuth(String...)` | 모든 권한 보유 |

### JwtUtil

| 메서드 | 설명 |
|--------|------|
| `generateAccessToken(...)` | Access Token 생성 |
| `generateRefreshToken(...)` | Refresh Token 생성 |
| `validateToken(String)` | 토큰 검증 |
| `isTokenExpired(String)` | 만료 여부 확인 |
| `extractUserId(String)` | 사용자 ID 추출 |
| `extractAuth(String)` | 권한 추출 |

### CryptoUtil

| 메서드 | 설명 |
|--------|------|
| `encrypt(String)` | AES-256 암호화 |
| `decrypt(String)` | AES-256 복호화 |
| `encryptUserId(String)` | userId 암호화 (검증 포함) |
| `decryptUserId(String)` | userId 복호화 (검증 포함) |

### MaskingUtil

| 메서드 | 설명 |
|--------|------|
| `maskEmail(String)` | 이메일 마스킹 |
| `maskPhone(String)` | 전화번호 마스킹 |
| `maskIpAddress(String)` | IP 마스킹 |
| `maskLogMessage(String)` | 로그 전체 마스킹 |

---

## 🚀 버전 히스토리

### 1.0.0 (2025-01-XX)
- ✨ 초기 릴리스
- Gateway Context 관리
- JWT 토큰 처리
- AES-256 암호화
- 자동 로깅 & 마스킹
- Gateway 서명 검증

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing`)
5. Open a Pull Request

---




## 🙏 Acknowledgments

- Spring Boot Team
- Spring Cloud Team
- JWT.io
- JitPack.io
