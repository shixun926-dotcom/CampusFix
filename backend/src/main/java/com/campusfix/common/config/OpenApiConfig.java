package com.campusfix.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * OpenAPI 文档配置，对应交付物中的《API 接口文档》。访问：{@code /swagger-ui.html}
 *
 * <p>前后端分离后后端可能位于独立域名（如 {@code https://api.example.com}），
 * 通过 {@code campusfix.api.server-url} 注入，Swagger 里的「Try it out」才会打到正确的源站。</p>
 */
@Configuration
public class OpenApiConfig {

    @Value("${campusfix.api.server-url:}")
    private String serverUrl;

    @Bean
    public OpenAPI campusFixOpenApi() {
        String schemeName = "bearerAuth";
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("CampusFix API")
                        .version("v1")
                        .description("校园设施报修与工单管理平台接口文档。"
                                + "所有业务接口以 /api/v1 为前缀，统一返回 { code, message, data, traceId }。"))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));

        if (StringUtils.hasText(serverUrl)) {
            openAPI.servers(List.of(new Server().url(serverUrl).description("后端服务")));
        }
        return openAPI;
    }
}
