package com.campusfix.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 前端 SPA 静态资源托管（一键启动）。
 *
 * <p>让后端在 8080 端口同时服务前端构建产物，从而在 IDEA 中点击一次运行按钮
 * 即可在浏览器直接访问完整应用，无需额外启动 Vite 前端服务。</p>
 *
 * <p>前端构建产物目录按以下候选顺序查找（<b>绝对路径</b>，不依赖进程工作目录）：</p>
 * <ol>
 *   <li>系统属性 {@code campusfix.frontend.dist}（可显式指定）</li>
 *   <li>项目根目录 {@code ../frontend/dist}（相对后端模块目录 backend）</li>
 *   <li>jar 内的 {@code classpath:/static/}（{@code mvn package} 打包产物）</li>
 * </ol>
 *
 * <p>前端使用 Vue Router 的 history 模式，因此对未命中任何静态文件的路径
 * （如 {@code /student}、{@code /admin}）做 SPA 回退，统一返回 {@code index.html}。</p>
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    /** 找到的第一个真实存在的 dist 目录；找不到返回 null。 */
    private Path resolveExistingDist() {
        List<Path> candidates = new ArrayList<>();
        // 1) 显式系统属性（-Dcampusfix.frontend.dist=...）
        String explicit = System.getProperty("campusfix.frontend.dist");
        if (explicit != null && !explicit.isBlank()) {
            candidates.add(Paths.get(explicit).toAbsolutePath());
        }
        // 2) 项目根目录：backend 模块的 ../frontend/dist（不依赖工作目录）
        candidates.add(Paths.get("../frontend/dist").toAbsolutePath());
        // 3) 兼容：backend 目录内（少见，保留）
        candidates.add(Paths.get("frontend/dist").toAbsolutePath());

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("index.html"))) {
                return candidate;
            }
        }
        return null;
    }

    /** 静态资源位置：真实 dist 目录（若存在）+ classpath 兜底。 */
    private String[] staticLocations() {
        Path dist = resolveExistingDist();
        if (dist != null) {
            return new String[] {
                    "file:" + dist.toAbsolutePath() + File.separator,
                    "classpath:/static/"
            };
        }
        return new String[] { "classpath:/static/" };
    }

    /** SPA 回退目标 index.html；dist 与 classpath 都不存在时返回 null（由框架返回 404）。 */
    private Resource resolveIndexResource() {
        Path dist = resolveExistingDist();
        if (dist != null) {
            try {
                return new UrlResource(dist.resolve("index.html").toUri());
            } catch (MalformedURLException ignored) {
                // 忽略，继续尝试 classpath
            }
        }
        ClassPathResource cp = new ClassPathResource("static/index.html");
        try {
            return cp.exists() ? cp : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String[] locations = staticLocations();
        Resource index = resolveIndexResource();

        // 统一用一个 "/**" 处理器：`**` 捕获完整相对路径（如 assets/index-xxx.js、student），
        // 从而能正确定位到 dist/assets/... 下的真实文件；
        // 命中真实文件则直接返回，未命中（前端 history 路由）则回退 index.html。
        registry.addResourceHandler("/**")
                .addResourceLocations(locations)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) {
                        Resource requested = null;
                        try {
                            requested = location.createRelative(resourcePath);
                            if (requested.exists() && requested.isReadable()) {
                                return requested;
                            }
                        } catch (IOException ignored) {
                            // 定位/读取失败按未命中处理
                        }
                        // 路径不指向真实文件（前端 history 路由）→ 回退 index.html；
                        // index 也为空（dist 与 classpath 都无产物）时返回 null → 404
                        return index;
                    }
                });
    }
}
