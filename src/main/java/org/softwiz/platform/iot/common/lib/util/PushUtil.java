package org.softwiz.platform.iot.common.lib.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 푸시 알림 요청 유틸리티
 *
 * <p>다른 서비스에서 WizMessage 푸시 서비스로 요청을 보낼 때 사용합니다.</p>
 *
 * <pre>
 * 사용 예시:
 * {@code
 * // 1. 간단한 푸시 요청 생성
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("새 알림")
 *     .content("새로운 알림이 있습니다.")
 *     .build();
 *
 * // 2. 상세한 푸시 요청 생성
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .title("경고")
 *     .content("긴급 상황이 발생했습니다.")
 *     .warnDiv(PushUtil.WarnDiv.WARNING)
 *     .pushValue("EMERGENCY_ALERT")
 *     .linkUrl("https://app.example.com/alert/123")
 *     .imageUrl("https://cdn.example.com/warning.png")
 *     .build();
 *
 * // 3. 예약 발송
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .content("예약된 알림입니다.")
 *     .scheduledAt(LocalDateTime.now().plusHours(1))
 *     .build();
 *
 * // 4. 추가 데이터 포함
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .content("주문이 완료되었습니다.")
 *     .dataField("orderId", 12345)
 *     .dataField("orderStatus", "COMPLETED")
 *     .build();
 *
 * // 5. 마케팅 푸시 (동의 확인 필요)
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .content("특별 할인 이벤트!")
 *     .marketingConsent()  // consentType = "MARKETING"
 *     .build();
 *
 * // 6. 시스템 알림 (동의 확인 스킵)
 * PushRequest request = PushUtil.builder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .content("시스템 점검 안내")
 *     .skipConsentCheck()
 *     .build();
 *
 * // 7. 템플릿 기반 발송 (개인)
 * TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("ORDER_COMPLETE")
 *     .userNo(1001L)
 *     .variable("orderNo", "12345")
 *     .variable("deliveryDate", "2025-12-20")
 *     .build();
 *
 * // 8. 템플릿 기반 발송 (다중)
 * TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("MARKETING_EVENT")
 *     .userNos(1001L, 1002L, 1003L)
 *     .variable("eventName", "연말 할인")
 *     .variable("eventContent", "최대 50% 할인!")
 *     .build();
 *
 * // 9. 템플릿 기반 발송 (전체)
 * TemplatePushRequest request = PushUtil.templateBuilder()
 *     .serviceId("NEST")
 *     .templateCode("SYSTEM_NOTICE")
 *     .sendAll()
 *     .variable("noticeTitle", "서버 점검")
 *     .skipConsentCheck()
 *     .build();
 *
 * // 10. 토큰 저장 요청
 * TokenRequest request = PushUtil.tokenBuilder()
 *     .serviceId("NEST")
 *     .userNo(1001L)
 *     .pushToken("fCm_token_xxx...")
 *     .deviceId("abc123def456")
 *     .android()
 *     .deviceModel("Galaxy S24")
 *     .osVersion("14")
 *     .appVersion("1.0.0")
 *     .build();
 *
 * // 11. RestTemplate으로 발송
 * String pushServiceUrl = "http://wizmessage:8095/api/v2/push/send";
 * ApiResponse response = restTemplate.postForObject(pushServiceUrl, request, ApiResponse.class);
 *
 * // 12. 템플릿 발송
 * String templatePushUrl = "http://wizmessage:8095/api/v2/push/template/send";
 * ApiResponse response = restTemplate.postForObject(templatePushUrl, request, ApiResponse.class);
 * }
 * </pre>
 */
@Slf4j
public class PushUtil {

    private PushUtil() {
        // Utility class
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ========================================
    // 알림 구분 상수
    // ========================================

    /**
     * 알림 구분 (warnDiv)
     */
    public enum WarnDiv {
        INFO("I"),      // 정보
        WARNING("W"),   // 경고
        DANGER("D");    // 위험

        private final String code;

        WarnDiv(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 동의 유형
     */
    public enum ConsentType {
        PUSH("PUSH"),
        LOCATION("LOCATION"),
        MARKETING("MARKETING"),
        NIGHT_PUSH("NIGHT_PUSH"),
        EVENT_PUSH("EVENT_PUSH");

        private final String code;

        ConsentType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * OS 타입
     */
    public enum OsType {
        ANDROID("ANDROID"),
        IOS("IOS"),
        WEB("WEB");

        private final String code;

        OsType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    // ========================================
    // 푸시 요청 DTO
    // ========================================

    /**
     * 푸시 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PushRequest {
        private String serviceId;
        private Long userNo;
        private String content;
        private String title;
        private String data;
        private String linkUrl;
        private String imageUrl;
        private String warnDiv;
        private String pushValue;
        private String eventTime;
        private String consentType;
        private String deviceId;
        private Boolean skipConsentCheck;
    }

    /**
     * 템플릿 푸시 발송 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TemplatePushRequest {
        private String serviceId;
        private String templateCode;
        private Long userNo;
        private List<Long> userNos;
        private Boolean sendAll;
        private Map<String, String> variables;
        private String data;
        private String linkUrl;
        private String imageUrl;
        private String eventTime;
        private Boolean skipConsentCheck;
        private String deviceId;
    }

    /**
     * 푸시 토큰 저장 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenRequest {
        private String serviceId;
        private Long userNo;
        private String pushToken;
        private String deviceId;
        private String uuid;
        private String isPush;
        private String os;
        private String deviceModel;
        private String osVersion;
        private String appVersion;
    }

    // ========================================
    // Builder 팩토리 메서드
    // ========================================

    public static PushRequestBuilder builder() {
        return new PushRequestBuilder();
    }

    public static TemplatePushRequestBuilder templateBuilder() {
        return new TemplatePushRequestBuilder();
    }

    public static TokenRequestBuilder tokenBuilder() {
        return new TokenRequestBuilder();
    }

    // ========================================
    // 푸시 요청 빌더
    // ========================================

    public static class PushRequestBuilder {
        private String serviceId;
        private Long userNo;
        private String content;
        private String title;
        private String linkUrl;
        private String imageUrl;
        private String warnDiv;
        private String pushValue;
        private LocalDateTime scheduledAt;
        private String consentType = "PUSH";
        private String deviceId;
        private Boolean skipConsentCheck = false;
        private final Map<String, Object> dataMap = new HashMap<>();

        public PushRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public PushRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public PushRequestBuilder content(String content) {
            this.content = content;
            return this;
        }

        public PushRequestBuilder title(String title) {
            this.title = title;
            return this;
        }

        public PushRequestBuilder linkUrl(String linkUrl) {
            this.linkUrl = linkUrl;
            return this;
        }

        public PushRequestBuilder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public PushRequestBuilder warnDiv(WarnDiv warnDiv) {
            this.warnDiv = warnDiv.getCode();
            return this;
        }

        public PushRequestBuilder warnDiv(String warnDiv) {
            this.warnDiv = warnDiv;
            return this;
        }

        public PushRequestBuilder pushValue(String pushValue) {
            this.pushValue = pushValue;
            return this;
        }

        public PushRequestBuilder scheduledAt(LocalDateTime scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }

        public PushRequestBuilder consentType(String consentType) {
            this.consentType = consentType;
            return this;
        }

        public PushRequestBuilder consentType(ConsentType consentType) {
            this.consentType = consentType.getCode();
            return this;
        }

        public PushRequestBuilder marketingConsent() {
            this.consentType = ConsentType.MARKETING.getCode();
            return this;
        }

        public PushRequestBuilder nightPushConsent() {
            this.consentType = ConsentType.NIGHT_PUSH.getCode();
            return this;
        }

        public PushRequestBuilder eventPushConsent() {
            this.consentType = ConsentType.EVENT_PUSH.getCode();
            return this;
        }

        public PushRequestBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public PushRequestBuilder skipConsentCheck() {
            this.skipConsentCheck = true;
            return this;
        }

        public PushRequestBuilder skipConsentCheck(boolean skip) {
            this.skipConsentCheck = skip;
            return this;
        }

        public PushRequestBuilder dataField(String key, Object value) {
            this.dataMap.put(key, value);
            return this;
        }

        public PushRequestBuilder dataFields(Map<String, Object> data) {
            this.dataMap.putAll(data);
            return this;
        }

        public PushRequest build() {
            String dataJson = null;
            if (!dataMap.isEmpty()) {
                dataJson = JsonUtil.toJson(dataMap);
            }

            String eventTime = null;
            if (scheduledAt != null) {
                eventTime = scheduledAt.format(DATE_TIME_FORMATTER);
            }

            return PushRequest.builder()
                    .serviceId(serviceId)
                    .userNo(userNo)
                    .content(content)
                    .title(title)
                    .data(dataJson)
                    .linkUrl(linkUrl)
                    .imageUrl(imageUrl)
                    .warnDiv(warnDiv)
                    .pushValue(pushValue)
                    .eventTime(eventTime)
                    .consentType(consentType)
                    .deviceId(deviceId)
                    .skipConsentCheck(skipConsentCheck)
                    .build();
        }
    }

    // ========================================
    // 템플릿 푸시 요청 빌더
    // ========================================

    public static class TemplatePushRequestBuilder {
        private String serviceId;
        private String templateCode;
        private Long userNo;
        private List<Long> userNos;
        private Boolean sendAll = false;
        private Map<String, String> variables = new LinkedHashMap<>();
        private String linkUrl;
        private String imageUrl;
        private LocalDateTime scheduledAt;
        private Boolean skipConsentCheck = false;
        private String deviceId;
        private final Map<String, Object> dataMap = new LinkedHashMap<>();

        public TemplatePushRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public TemplatePushRequestBuilder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        public TemplatePushRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public TemplatePushRequestBuilder userNos(List<Long> userNos) {
            this.userNos = userNos;
            return this;
        }

        public TemplatePushRequestBuilder userNos(Long... userNos) {
            this.userNos = Arrays.asList(userNos);
            return this;
        }

        public TemplatePushRequestBuilder sendAll() {
            this.sendAll = true;
            return this;
        }

        public TemplatePushRequestBuilder sendAll(boolean sendAll) {
            this.sendAll = sendAll;
            return this;
        }

        public TemplatePushRequestBuilder variable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        public TemplatePushRequestBuilder variable(String key, Number value) {
            this.variables.put(key, value != null ? value.toString() : "");
            return this;
        }

        public TemplatePushRequestBuilder variables(Map<String, String> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public TemplatePushRequestBuilder linkUrl(String linkUrl) {
            this.linkUrl = linkUrl;
            return this;
        }

        public TemplatePushRequestBuilder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public TemplatePushRequestBuilder scheduledAt(LocalDateTime scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }

        public TemplatePushRequestBuilder skipConsentCheck() {
            this.skipConsentCheck = true;
            return this;
        }

        public TemplatePushRequestBuilder skipConsentCheck(boolean skip) {
            this.skipConsentCheck = skip;
            return this;
        }

        public TemplatePushRequestBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public TemplatePushRequestBuilder dataField(String key, Object value) {
            this.dataMap.put(key, value);
            return this;
        }

        public TemplatePushRequestBuilder dataFields(Map<String, Object> data) {
            this.dataMap.putAll(data);
            return this;
        }

        public TemplatePushRequest build() {
            String dataJson = null;
            if (!dataMap.isEmpty()) {
                dataJson = JsonUtil.toJson(dataMap);
            }

            String eventTime = null;
            if (scheduledAt != null) {
                eventTime = scheduledAt.format(DATE_TIME_FORMATTER);
            }

            return TemplatePushRequest.builder()
                    .serviceId(serviceId)
                    .templateCode(templateCode)
                    .userNo(userNo)
                    .userNos(userNos)
                    .sendAll(sendAll)
                    .variables(variables.isEmpty() ? null : variables)
                    .data(dataJson)
                    .linkUrl(linkUrl)
                    .imageUrl(imageUrl)
                    .eventTime(eventTime)
                    .skipConsentCheck(skipConsentCheck)
                    .deviceId(deviceId)
                    .build();
        }
    }

    // ========================================
    // 토큰 요청 빌더
    // ========================================

    public static class TokenRequestBuilder {
        private String serviceId;
        private Long userNo;
        private String pushToken;
        private String deviceId;
        private String uuid;
        private String isPush = "Y";
        private String os;
        private String deviceModel;
        private String osVersion;
        private String appVersion;

        public TokenRequestBuilder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public TokenRequestBuilder userNo(Long userNo) {
            this.userNo = userNo;
            return this;
        }

        public TokenRequestBuilder pushToken(String pushToken) {
            this.pushToken = pushToken;
            return this;
        }

        public TokenRequestBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public TokenRequestBuilder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public TokenRequestBuilder isPush(boolean isPush) {
            this.isPush = isPush ? "Y" : "N";
            return this;
        }

        public TokenRequestBuilder isPush(String isPush) {
            this.isPush = isPush;
            return this;
        }

        public TokenRequestBuilder os(String os) {
            this.os = os;
            return this;
        }

        public TokenRequestBuilder os(OsType osType) {
            this.os = osType.getCode();
            return this;
        }

        public TokenRequestBuilder android() {
            this.os = OsType.ANDROID.getCode();
            return this;
        }

        public TokenRequestBuilder ios() {
            this.os = OsType.IOS.getCode();
            return this;
        }

        public TokenRequestBuilder web() {
            this.os = OsType.WEB.getCode();
            return this;
        }

        public TokenRequestBuilder deviceModel(String deviceModel) {
            this.deviceModel = deviceModel;
            return this;
        }

        public TokenRequestBuilder osVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }

        public TokenRequestBuilder appVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        public TokenRequest build() {
            return TokenRequest.builder()
                    .serviceId(serviceId)
                    .userNo(userNo)
                    .pushToken(pushToken)
                    .deviceId(deviceId)
                    .uuid(uuid)
                    .isPush(isPush)
                    .os(os)
                    .deviceModel(deviceModel)
                    .osVersion(osVersion)
                    .appVersion(appVersion)
                    .build();
        }
    }

    // ========================================
    // 편의 메서드 - 일반 푸시
    // ========================================

    public static PushRequest info(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .content(content)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    public static PushRequest info(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    public static PushRequest warning(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("경고")
                .content(content)
                .warnDiv(WarnDiv.WARNING)
                .build();
    }

    public static PushRequest danger(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("위험")
                .content(content)
                .warnDiv(WarnDiv.DANGER)
                .build();
    }

    public static PushRequest system(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .warnDiv(WarnDiv.INFO)
                .skipConsentCheck()
                .build();
    }

    public static PushRequest marketing(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .warnDiv(WarnDiv.INFO)
                .marketingConsent()
                .build();
    }

    // ========================================
    // 편의 메서드 - 템플릿 푸시
    // ========================================

    public static TemplatePushRequest template(String serviceId, String templateCode,
                                               Long userNo, Map<String, String> variables) {
        return templateBuilder()
                .serviceId(serviceId)
                .templateCode(templateCode)
                .userNo(userNo)
                .variables(variables)
                .build();
    }

    public static TemplatePushRequest templateMultiple(String serviceId, String templateCode,
                                                       List<Long> userNos, Map<String, String> variables) {
        return templateBuilder()
                .serviceId(serviceId)
                .templateCode(templateCode)
                .userNos(userNos)
                .variables(variables)
                .build();
    }

    public static TemplatePushRequest templateAll(String serviceId, String templateCode,
                                                  Map<String, String> variables) {
        return templateBuilder()
                .serviceId(serviceId)
                .templateCode(templateCode)
                .sendAll()
                .variables(variables)
                .build();
    }

    // ========================================
    // 편의 메서드 - 토큰
    // ========================================

    public static TokenRequest token(String serviceId, Long userNo, String pushToken,
                                     String deviceId, String os) {
        return tokenBuilder()
                .serviceId(serviceId)
                .userNo(userNo)
                .pushToken(pushToken)
                .deviceId(deviceId)
                .os(os)
                .build();
    }
}