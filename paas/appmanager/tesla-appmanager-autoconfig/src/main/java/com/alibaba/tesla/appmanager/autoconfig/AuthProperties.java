package com.alibaba.tesla.appmanager.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证相关设置
 *
 * @author yaoxing.gyx@alibaba-inc.com
 */
@Data
@ConfigurationProperties(prefix = "appmanager.auth")
public class AuthProperties {

    /**
     * 是否开启鉴权逻辑
     */
    private Boolean enableAuth = true;

    /**
     * SUPER CLIENT ID
     */
    private String superClientId;

    /**
     * SUPER CLIENT SECRET
     */
    private String superClientSecret;

    /**
     * SUPER ACCESS ID
     */
    private String superAccessId;

    /**
     * SUPER ACCESS SECRET
     */
    private String superAccessSecret;

    /**
     * JWT Secret Key
     */
    private String jwtSecretKey;
}
