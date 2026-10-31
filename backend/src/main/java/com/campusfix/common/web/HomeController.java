package com.campusfix.common.web;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 开发期入口：后端本身不提供页面，根路径直接引导到接口文档。
 *
 * <p>部署时前端由 Nginx 托管在 "/"，请求不会落到这里；
 * 此控制器只解决"直接访问 http://localhost:8080 看到一段 401 JSON"的困惑。</p>
 */
@Hidden
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/swagger-ui.html";
    }
}
