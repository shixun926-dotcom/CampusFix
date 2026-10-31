package com.campusfix.order.controller;

import com.campusfix.common.result.Result;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "故障类型")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "启用中的故障类型")
    @GetMapping
    public Result<List<OrderDtos.CategoryVO>> list() {
        return Result.ok(categoryService.listEnabled());
    }
}
