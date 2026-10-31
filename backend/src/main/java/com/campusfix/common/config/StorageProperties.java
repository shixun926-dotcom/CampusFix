package com.campusfix.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储配置：campusfix.storage.*
 * 一期统一走后端中转上传，local / oss 通过配置切换，代码零改动。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campusfix.storage")
public class StorageProperties {

    /** local | oss */
    private String type = "local";

    /** 本地上传根目录 */
    private String localPath = "D:/campusfix-data/upload";

    /** 本地访问前缀，如 /files */
    private String localUrlPrefix = "/files";

    /**
     * 对外访问基地址，如 {@code https://api.example.com} 或 CDN 域名。
     *
     * <p>为空时 {@code getUrl} 返回 {@code /files/xxx} 相对路径（前后端同源部署）；
     * 配置后返回绝对 URL —— 前后端分域部署时必须配置，否则图片会打到前端域名上。</p>
     */
    private String publicBaseUrl = "";

    private Oss oss = new Oss();

    @Data
    public static class Oss {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket;
    }
}
