package com.campusfix.asset.controller;

import com.campusfix.asset.service.LocationService;
import com.campusfix.common.result.Result;
import com.campusfix.order.dto.OrderDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "位置树")
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @Operation(summary = "位置树（校区→区域→楼栋→楼层→房间）")
    @GetMapping("/tree")
    public Result<List<OrderDtos.LocationNode>> tree() {
        return Result.ok(locationService.tree());
    }
}
