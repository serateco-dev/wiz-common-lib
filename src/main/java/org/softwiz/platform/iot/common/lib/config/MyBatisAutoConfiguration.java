package org.softwiz.platform.iot.common.lib.config;

import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.softwiz.platform.iot.common.lib.mybatis.DateFormatTypeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis 공통 설정
 * - TypeHandler 자동 등록
 */
@org.springframework.context.annotation.Configuration
@ConditionalOnClass(ConfigurationCustomizer.class)  // MyBatis 있을 때만 로드
public class MyBatisAutoConfiguration {

    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            // 공통 TypeHandler 자동 등록
            configuration.getTypeHandlerRegistry()
                .register(DateFormatTypeHandler.class);
        };
    }
}