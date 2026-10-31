package com.campusfix.common.web;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径入口：转发到前端 SPA 首页 index.html。
 *
 * <p>一键启动模式下，后端在 8080 端口同时托管前端构建产物（见
 * {@code StaticResourceConfig}），根路径直接打开前端登录页；
 * 接口文档仍可通过 {@code /swagger-ui.html} 访问。</p>
 */
@Hidden
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }
}
