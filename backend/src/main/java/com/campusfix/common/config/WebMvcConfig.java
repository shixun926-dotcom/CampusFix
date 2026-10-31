package com.campusfix.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 静态资源映射：本地存储模式下把上传目录暴露为 /files/**。
 * OSS 模式下该映射不会被访问，保留不影响。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!"local".equalsIgnoreCase(storageProperties.getType())) {
            return;
        }
        String path = new File(storageProperties.getLocalPath()).getAbsolutePath();
        registry.addResourceHandler(storageProperties.getLocalUrlPrefix() + "/**")
                .addResourceLocations("file:" + path + File.separator);
    }
}
