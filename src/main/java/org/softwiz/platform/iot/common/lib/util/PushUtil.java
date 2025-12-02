package org.softwiz.platform.iot.common.lib.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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
 *     .title("⚠️ 경고")
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
 *     .marketingConsent()  // consentType = "MARKETING_PUSH"
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
 * // 7. RestTemplate으로 발송
 * String pushServiceUrl = "http://wizmessage:8095/api/v2/push/send";
 * ApiResponse response = restTemplate.postForObject(pushServiceUrl, request, ApiResponse.class);
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
        /**
         * 서비스 ID (필수)
         */
        private String serviceId;

        /**
         * 수신자 사용자 번호 (필수)
         */
        private Long userNo;

        /**
         * 푸시 내용 (필수)
         */
        private String content;

        /**
         * 푸시 제목
         */
        private String title;

        /**
         * 추가 데이터 (JSON 문자열)
         */
        private String data;

        /**
         * 연결 링크 URL
         */
        private String linkUrl;

        /**
         * 이미지 URL
         */
        private String imageUrl;

        /**
         * 알림 구분 (I:정보, W:경고, D:위험)
         */
        private String warnDiv;

        /**
         * 푸시 값 (앱에서 처리할 코드)
         */
        private String pushValue;

        /**
         * 예약 발송 시간
         */
        private String eventTime;

        /**
         * 동의 유형 (SSO tb_user_consent.consent_type)
         * PUSH, MARKETING_PUSH, NIGHT_PUSH, EVENT_PUSH, LOCATION_PUSH 등
         */
        private String consentType;

        /**
         * 대상 디바이스 ID
         */
        private String deviceId;

        /**
         * 동의 확인 스킵 여부 (시스템 알림용)
         */
        private Boolean skipConsentCheck;
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
        private String uuid;
        private String isPush;
        private String platform;
        private String deviceModel;
        private String osVersion;
        private String appVersion;
    }

    // ========================================
    // Builder 팩토리 메서드
    // ========================================

    /**
     * 푸시 요청 빌더 생성
     */
    public static PushRequestBuilder builder() {
        return new PushRequestBuilder();
    }

    /**
     * 토큰 저장 요청 빌더 생성
     */
    public static TokenRequestBuilder tokenBuilder() {
        return new TokenRequestBuilder();
    }

    // ========================================
    // 푸시 요청 빌더
    // ========================================

    /**
     * 푸시 요청 빌더
     */
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

        /**
         * 알림 구분 설정 (enum)
         */
        public PushRequestBuilder warnDiv(WarnDiv warnDiv) {
            this.warnDiv = warnDiv.getCode();
            return this;
        }

        /**
         * 알림 구분 설정 (문자열)
         */
        public PushRequestBuilder warnDiv(String warnDiv) {
            this.warnDiv = warnDiv;
            return this;
        }

        public PushRequestBuilder pushValue(String pushValue) {
            this.pushValue = pushValue;
            return this;
        }

        /**
         * 예약 발송 시간 설정
         */
        public PushRequestBuilder scheduledAt(LocalDateTime scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }

        /**
         * 동의 유형 설정 (기본값: PUSH)
         */
        public PushRequestBuilder consentType(String consentType) {
            this.consentType = consentType;
            return this;
        }

        /**
         * PUSH 동의로 설정 (기본값)
         */
        public PushRequestBuilder pushConsent() {
            this.consentType = "PUSH";
            return this;
        }

        /**
         * MARKETING_PUSH 동의로 설정
         */
        public PushRequestBuilder marketingConsent() {
            this.consentType = "MARKETING_PUSH";
            return this;
        }

        /**
         * NIGHT_PUSH 동의로 설정 (야간 푸시)
         */
        public PushRequestBuilder nightConsent() {
            this.consentType = "NIGHT_PUSH";
            return this;
        }

        /**
         * EVENT_PUSH 동의로 설정 (이벤트 푸시)
         */
        public PushRequestBuilder eventConsent() {
            this.consentType = "EVENT_PUSH";
            return this;
        }

        /**
         * LOCATION_PUSH 동의로 설정 (위치 기반 푸시)
         */
        public PushRequestBuilder locationConsent() {
            this.consentType = "LOCATION_PUSH";
            return this;
        }

        /**
         * 대상 디바이스 설정
         */
        public PushRequestBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        /**
         * 동의 확인 스킵 (시스템 알림용)
         */
        public PushRequestBuilder skipConsentCheck() {
            this.skipConsentCheck = true;
            return this;
        }

        /**
         * 추가 데이터 필드 추가
         */
        public PushRequestBuilder dataField(String key, Object value) {
            this.dataMap.put(key, value);
            return this;
        }

        /**
         * 추가 데이터 (Map)
         */
        public PushRequestBuilder data(Map<String, Object> data) {
            this.dataMap.putAll(data);
            return this;
        }

        /**
         * 추가 데이터 (JSON 문자열)
         */
        public PushRequestBuilder dataJson(String json) {
            Map<String, Object> parsed = JsonUtil.toMap(json);
            if (parsed != null) {
                this.dataMap.putAll(parsed);
            } else {
                log.warn("Failed to parse data JSON: {}", json);
            }
            return this;
        }

        public PushRequest build() {
            // 데이터 맵을 JSON 문자열로 변환 (JsonUtil 사용)
            String dataJson = null;
            if (!dataMap.isEmpty()) {
                dataJson = JsonUtil.toJson(dataMap);
                if (dataJson == null) {
                    log.warn("Failed to serialize data map");
                }
            }

            // 예약 시간 포맷팅
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
    // 토큰 요청 빌더
    // ========================================

    /**
     * 토큰 저장 요청 빌더
     */
    public static class TokenRequestBuilder {
        private String serviceId;
        private Long userNo;
        private String pushToken;
        private String uuid;
        private String isPush = "Y";
        private String platform;
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

        public TokenRequestBuilder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public TokenRequestBuilder isPush(boolean isPush) {
            this.isPush = isPush ? "Y" : "N";
            return this;
        }

        public TokenRequestBuilder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public TokenRequestBuilder android() {
            this.platform = "ANDROID";
            return this;
        }

        public TokenRequestBuilder ios() {
            this.platform = "IOS";
            return this;
        }

        public TokenRequestBuilder web() {
            this.platform = "WEB";
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
                    .uuid(uuid)
                    .isPush(isPush)
                    .platform(platform)
                    .deviceModel(deviceModel)
                    .osVersion(osVersion)
                    .appVersion(appVersion)
                    .build();
        }
    }

    // ========================================
    // 편의 메서드
    // ========================================

    /**
     * 간단한 정보 알림 생성
     */
    public static PushRequest info(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .content(content)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    /**
     * 간단한 정보 알림 생성 (제목 포함)
     */
    public static PushRequest info(String serviceId, Long userNo, String title, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title(title)
                .content(content)
                .warnDiv(WarnDiv.INFO)
                .build();
    }

    /**
     * 경고 알림 생성
     */
    public static PushRequest warning(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("⚠️ 경고")
                .content(content)
                .warnDiv(WarnDiv.WARNING)
                .build();
    }

    /**
     * 위험 알림 생성
     */
    public static PushRequest danger(String serviceId, Long userNo, String content) {
        return builder()
                .serviceId(serviceId)
                .userNo(userNo)
                .title("🚨 위험")
                .content(content)
                .warnDiv(WarnDiv.DANGER)
                .build();
    }

    /**
     * 시스템 알림 생성 (동의 확인 스킵)
     */
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

    /**
     * 마케팅 알림 생성 (동의 확인 필요)
     */
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
}