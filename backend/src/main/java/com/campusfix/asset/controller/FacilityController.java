package com.campusfix.asset.controller;

import com.campusfix.asset.entity.Facility;
import com.campusfix.asset.service.FacilityService;
import com.campusfix.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 设施档案查询：报修时按位置关联设施（可选，如墙面、路灯无需关联）。
 */
@Tag(name = "设施档案")
@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
public class FacilityController {

    private final FacilityService facilityService;

    public record FacilityVO(Long id, String facilityNo, String name,
                             String typeName, String status) {
    }

    @Operation(summary = "某位置下的设施列表（已报废不返回）")
    @GetMapping
    public Result<List<FacilityVO>> listByLocation(@RequestParam Long locationId) {
        Map<Long, String> typeNames = facilityService.typeNameMap();
        List<FacilityVO> list = facilityService.listByLocation(locationId).stream()
                .map(f -> new FacilityVO(f.getId(), f.getFacilityNo(), f.getName(),
                        typeNames.get(f.getTypeId()), f.getStatus()))
                .toList();
        return Result.ok(list);
    }
}
