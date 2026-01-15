package org.softwiz.platform.iot.common.lib.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 이메일 발송 요청 유틸리티
 *
 * <p>다른 서비스에서 WizMessage 이메일 서비스로 요청을 보낼 때 사용합니다.</p>
 *
 * <pre>
 * 사용 예시:
 * {@code
 * // 1. 간단한 이메일 발송
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("환영합니다")
 *     .content("<h1>회원가입을 환영합니다!</h1>")
 *     .html()
 *     .build();
 *
 * // 2. 텍스트 이메일 발송
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("알림")
 *     .content("시스템 점검 안내입니다.")
 *     .text()
 *     .build();
 *
 * // 3. 발신자 정보 지정
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("공지사항")
 *     .content("중요한 공지사항입니다.")
 *     .senderEmail("notice@example.com")
 *     .senderName("공지팀")
 *     .build();
 *
 * // 4. 참조/숨은참조 포함
 * EmailRequest request = EmailUtil.builder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .subject("회의 초대")
 *     .content("회의에 초대합니다.")
 *     .cc("manager@example.com")
 *     .bcc("admin@example.com")
 *     .build();
 *
 * // 5. 템플릿 기반 발송
 * TemplateEmailRequest request = EmailUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("WELCOME")
 *     .recipient("user@example.com")
 *     .variable("userName", "홍길동")
 *     .variable("serviceName", "WIZ Platform")
 *     .build();
 *
 * // 6. 템플릿 + 추가 정보
 * TemplateEmailRequest request = EmailUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("ORDER_COMPLETE")
 *     .recipient("user@example.com")
 *     .variable("orderNo", "12345")
 *     .variable("orderDate", "2025-01-15")
 *     .variable("amount", "50,000원")
 *     .senderName("주문팀")
 *     .build();
 *
 * // 7. 인증 이메일 발송 (회원가입)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose("SIGNUP")
 *     .templateCode("VERIFY_SIGNUP")
 *     .variable("userName", "홍길동")
 *     .build();
 *
 * // 8. 인증 이메일 발송 (비밀번호 재설정)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose("PASSWORD_RESET")
 *     .templateCode("VERIFY_PASSWORD_RESET")
 *     .build();
 *
 * // 9. 인증 이메일 발송 (커스텀 코드 지정)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose("EMAIL_VERIFY")
 *     .templateCode("EMAIL_VERIFICATION")
 *     .customCode("123456")       // 직접 인증 코드 지정
 *     .expireMinutes(5)           // 만료 시간 5분
 *     .variable("userName", "홍길동")
 *     .build();
 *
 * // 10. 인증 이메일 발송 (회원 정보 포함 - USER)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("user@example.com")
 *     .verifyPurpose("EMAIL_VERIFY")
 *     .templateCode("EMAIL_VERIFICATION")
 *     .memberUser(1001L)          // memberType=USER, memberNo=1001
 *     .variable("userName", "홍길동")
 *     .build();
 *
 * // 11. 인증 이메일 발송 (회원 정보 포함 - ADMIN)
 * VerifyEmailRequest request = EmailUtil.verifyBuilder()
 *     .serviceId("NEST")
 *     .recipient("admin@example.com")
 *     .verifyPurpose("ADMIN_TEMP_PASSWORD")
 *     .templateCode("ADMIN_TEMP_PASSWORD")
 *     .memberAdmin(100L)          // memberType=ADMIN, memberNo=100
 *     .createdBy(1L)              // 생성자 (관리자 번호)
 *     .build();
 *
 * // 12. RestTemplate으로 발송
 * String emailServiceUrl = "http://wizmessage:8098/api/v2/email/send";
 * ApiResponse response = restTemplate.postForObject(emailServiceUrl, request, ApiResponse.class);
 *
 * // 13. 템플릿 발송
 * String templateEmailUrl = "http://wizmessage:8098/api/v2/email/template/send";
 * ApiResponse response = restTemplate.postForObject(templateEmailUrl, request, ApiResponse.class);
 * }
 * </pre>
 */
@Slf4j
public class EmailUtil {

    private EmailUtil() {
        // Utility class
    }

    // ========================================
    // 인증 목적 상수
    // ========================================

    /**
     * 인증 목적 (verifyPurpose)
     */
    public enum VerifyPurpose {
        SIGNUP("SIGNUP"),
        EMAIL_VERIFY("EMAIL_VERIFY"),
        PASSWORD_RESET("PASSWORD_RESET"),
        PARENT_VERIFY("PARENT_VERIFY"),
        ADMIN_TEMP_PASSWORD("ADMIN_TEMP_PASSWORD");

        private final String code;

        VerifyPurpose(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 회원 타입
     */
    public enum MemberType {
        USER("USER"),
        ADMIN("ADMIN");

        private final String code;

        MemberType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    // ========================================
    // 이메일 요청 DTO
    // ========================================

    /**
     * 이메일 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmailRequest {
        private String serviceId;
        private String recipient;
        private String subject;
        private String content;
        private String contentType;
        private String emailType;
        private String senderName;
        private String cc;
        private String bcc;
        private String recipientName;
        private Long userNo;
        private Long createdBy;
    }

    /**
     * 템플릿 이메일 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TemplateEmailRequest {
        private String serviceId;
        private String templateCode;
        private String recipient;
        private Map<String, String> variables;
        private String senderName;
        private String cc;
        private String bcc;
        private String recipientName;
        private Long userNo;
        private Long createdBy;
    }

    /**
     * 인증 이메일 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VerifyEmailRequest {
        private String serviceId;
        private String recipient;
        private String verifyPurpose;
        private String templateCode;
        private Map<String, String> variables;
        private String senderName;
        private String recipientName;

        /**
         * 인증 코드 직접 지정 (null이면 서버에서 6자리 자동 생성)
         */
        private String customCode;

        /**
         * 만료 시간 (분, 기본 10분)
         */
        private Integer expireMinutes;

        /**
         * 회원 타입 (USER, ADMIN) - 비회원이면 null
         */
        private String memberType;

        /**
         * 회원 번호 (user_no 또는 admin_no) - 비회원이면 null
         */
        private Long memberNo;

        /**
         * 생성자 (관리자 번호)
         */
        private Long createdBy;
    }

    // ========================================
    // 일반 이메일 빌더
    // ========================================

    public static EmailRequestBuilder builder() {
        return new EmailRequestBuilder();
    }

    public static class EmailRequestBuilder {
        private String serviceId;
        private String recipient;
        private String subject;
        private String content;
        private String contentType = "HTML";
        private String emailType = "SYSTEM";
        private String senderName;
        private String cc;
        private String bcc;
        private String recipientName;
        private Long userNo;
        private Long createdBy;

        public EmailRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public EmailRequestBuilder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public EmailRequestBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public EmailRequestBuilder content(String content) {
            this.content = content;
            return this;
        }

        public EmailRequestBuilder html() {
            this.contentType = "HTML";
            return this;
        }

        public EmailRequestBuilder text() {
            this.contentType = "TEXT";
            return this;
        }

        public EmailRequestBuilder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public EmailRequestBuilder emailType(String emailType) {
            this.emailType = emailType;
            return this;
        }

        public EmailRequestBuilder system() {
            this.emailType = "SYSTEM";
            return this;
        }

        public EmailRequestBuilder marketing() {
            this.emailType = "MARKETING";
            return this;
        }

        public EmailRequestBuilder transaction() {
            this.emailType = "TRANSACTION";
            return this;
        }

        public EmailRequestBuilder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }

        public EmailRequestBuilder cc(String cc) {
            this.cc = cc;
            return this;
        }

        public EmailRequestBuilder bcc(String bcc) {
            this.bcc = bcc;
            return this;
        }

        public EmailRequestBuilder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        public EmailRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public EmailRequestBuilder createdBy(Long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public EmailRequest build() {
            return EmailRequest.builder()
                    .serviceId(serviceId)
                    .recipient(recipient)
                    .subject(subject)
                    .content(content)
                    .contentType(contentType)
                    .emailType(emailType)
                    .senderName(senderName)
                    .cc(cc)
                    .bcc(bcc)
                    .recipientName(recipientName)
                    .userNo(userNo)
                    .createdBy(createdBy)
                    .build();
        }
    }

    // ========================================
    // 템플릿 이메일 빌더
    // ========================================

    public static TemplateEmailRequestBuilder templateBuilder() {
        return new TemplateEmailRequestBuilder();
    }

    public static class TemplateEmailRequestBuilder {
        private String serviceId;
        private String templateCode;
        private String recipient;
        private Map<String, String> variables = new LinkedHashMap<>();
        private String senderName;
        private String cc;
        private String bcc;
        private String recipientName;
        private Long userNo;
        private Long createdBy;

        public TemplateEmailRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public TemplateEmailRequestBuilder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        public TemplateEmailRequestBuilder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public TemplateEmailRequestBuilder variable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        public TemplateEmailRequestBuilder variable(String key, Number value) {
            this.variables.put(key, value != null ? value.toString() : "");
            return this;
        }

        public TemplateEmailRequestBuilder variables(Map<String, String> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public TemplateEmailRequestBuilder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }

        public TemplateEmailRequestBuilder cc(String cc) {
            this.cc = cc;
            return this;
        }

        public TemplateEmailRequestBuilder bcc(String bcc) {
            this.bcc = bcc;
            return this;
        }

        public TemplateEmailRequestBuilder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        public TemplateEmailRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public TemplateEmailRequestBuilder createdBy(Long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public TemplateEmailRequest build() {
            return TemplateEmailRequest.builder()
                    .serviceId(serviceId)
                    .templateCode(templateCode)
                    .recipient(recipient)
                    .variables(variables.isEmpty() ? null : variables)
                    .senderName(senderName)
                    .cc(cc)
                    .bcc(bcc)
                    .recipientName(recipientName)
                    .userNo(userNo)
                    .createdBy(createdBy)
                    .build();
        }
    }

    // ========================================
    // 인증 이메일 빌더
    // ========================================

    public static VerifyEmailRequestBuilder verifyBuilder() {
        return new VerifyEmailRequestBuilder();
    }

    public static class VerifyEmailRequestBuilder {
        private String serviceId;
        private String recipient;
        private String verifyPurpose;
        private String templateCode;
        private Map<String, String> variables = new LinkedHashMap<>();
        private String senderName;
        private String recipientName;
        private String customCode;
        private Integer expireMinutes;
        private String memberType;
        private Long memberNo;
        private Long createdBy;

        public VerifyEmailRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public VerifyEmailRequestBuilder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public VerifyEmailRequestBuilder verifyPurpose(String verifyPurpose) {
            this.verifyPurpose = verifyPurpose;
            return this;
        }

        public VerifyEmailRequestBuilder verifyPurpose(VerifyPurpose verifyPurpose) {
            this.verifyPurpose = verifyPurpose.getCode();
            return this;
        }

        public VerifyEmailRequestBuilder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        public VerifyEmailRequestBuilder variable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        public VerifyEmailRequestBuilder variable(String key, Number value) {
            this.variables.put(key, value != null ? value.toString() : "");
            return this;
        }

        public VerifyEmailRequestBuilder variables(Map<String, String> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public VerifyEmailRequestBuilder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }

        public VerifyEmailRequestBuilder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        /**
         * 인증 코드 직접 지정
         * @param customCode 사용할 인증 코드 (null이면 서버에서 6자리 자동 생성)
         */
        public VerifyEmailRequestBuilder customCode(String customCode) {
            this.customCode = customCode;
            return this;
        }

        /**
         * 만료 시간 설정
         * @param expireMinutes 만료 시간 (분, 기본 10분)
         */
        public VerifyEmailRequestBuilder expireMinutes(Integer expireMinutes) {
            this.expireMinutes = expireMinutes;
            return this;
        }

        /**
         * 회원 타입 설정 (USER 또는 ADMIN)
         */
        public VerifyEmailRequestBuilder memberType(String memberType) {
            this.memberType = memberType;
            return this;
        }

        /**
         * 회원 타입 설정 (Enum)
         */
        public VerifyEmailRequestBuilder memberType(MemberType memberType) {
            this.memberType = memberType.getCode();
            return this;
        }

        /**
         * 회원 번호 설정 (user_no 또는 admin_no)
         */
        public VerifyEmailRequestBuilder memberNo(Long memberNo) {
            this.memberNo = memberNo;
            return this;
        }

        /**
         * USER 회원 정보 설정 (memberType=USER, memberNo 설정)
         */
        public VerifyEmailRequestBuilder memberUser(Long userNo) {
            this.memberType = MemberType.USER.getCode();
            this.memberNo = userNo;
            return this;
        }

        /**
         * ADMIN 회원 정보 설정 (memberType=ADMIN, memberNo 설정)
         */
        public VerifyEmailRequestBuilder memberAdmin(Long adminNo) {
            this.memberType = MemberType.ADMIN.getCode();
            this.memberNo = adminNo;
            return this;
        }

        /**
         * 생성자 (관리자 번호)
         */
        public VerifyEmailRequestBuilder createdBy(Long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public VerifyEmailRequest build() {
            return VerifyEmailRequest.builder()
                    .serviceId(serviceId)
                    .recipient(recipient)
                    .verifyPurpose(verifyPurpose)
                    .templateCode(templateCode)
                    .variables(variables.isEmpty() ? null : variables)
                    .senderName(senderName)
                    .recipientName(recipientName)
                    .customCode(customCode)
                    .expireMinutes(expireMinutes)
                    .memberType(memberType)
                    .memberNo(memberNo)
                    .createdBy(createdBy)
                    .build();
        }
    }

    // ========================================
    // 편의 메서드 - 일반 이메일
    // ========================================

    public static EmailRequest text(String serviceId, String recipient, String subject, String content) {
        return builder()
                .serviceId(serviceId)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .text()
                .build();
    }

    public static EmailRequest html(String serviceId, String recipient, String subject, String content) {
        return builder()
                .serviceId(serviceId)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .html()
                .build();
    }

    public static EmailRequest system(String serviceId, String recipient, String subject, String content) {
        return builder()
                .serviceId(serviceId)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .senderName("시스템")
                .html()
                .build();
    }

    // ========================================
    // 편의 메서드 - 템플릿 이메일
    // ========================================

    public static TemplateEmailRequest template(String serviceId, String templateCode,
                                                String recipient, Map<String, String> variables) {
        return templateBuilder()
                .serviceId(serviceId)
                .templateCode(templateCode)
                .recipient(recipient)
                .variables(variables)
                .build();
    }

    // ========================================
    // 편의 메서드 - 인증 이메일
    // ========================================

    public static VerifyEmailRequest signup(String serviceId, String recipient,
                                            String templateCode, String userName) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.SIGNUP)
                .templateCode(templateCode)
                .variable("userName", userName)
                .build();
    }

    public static VerifyEmailRequest passwordReset(String serviceId, String recipient,
                                                   String templateCode) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.PASSWORD_RESET)
                .templateCode(templateCode)
                .build();
    }

    public static VerifyEmailRequest emailVerify(String serviceId, String recipient,
                                                 String templateCode) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.EMAIL_VERIFY)
                .templateCode(templateCode)
                .build();
    }

    /**
     * 커스텀 코드로 인증 이메일 생성
     */
    public static VerifyEmailRequest emailVerifyWithCode(String serviceId, String recipient,
                                                         String templateCode, String customCode) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.EMAIL_VERIFY)
                .templateCode(templateCode)
                .customCode(customCode)
                .build();
    }

    /**
     * USER 회원의 인증 이메일 생성
     */
    public static VerifyEmailRequest emailVerifyForUser(String serviceId, String recipient,
                                                        String templateCode, Long userNo) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.EMAIL_VERIFY)
                .templateCode(templateCode)
                .memberUser(userNo)
                .build();
    }

    /**
     * ADMIN 회원의 인증 이메일 생성
     */
    public static VerifyEmailRequest emailVerifyForAdmin(String serviceId, String recipient,
                                                         String templateCode, Long adminNo) {
        return verifyBuilder()
                .serviceId(serviceId)
                .recipient(recipient)
                .verifyPurpose(VerifyPurpose.EMAIL_VERIFY)
                .templateCode(templateCode)
                .memberAdmin(adminNo)
                .build();
    }
}