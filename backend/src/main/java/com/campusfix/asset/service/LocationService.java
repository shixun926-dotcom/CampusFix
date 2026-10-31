package com.campusfix.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.asset.entity.Location;
import com.campusfix.asset.mapper.LocationMapper;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.order.dto.OrderDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 位置树服务：报修选点、路径快照、楼栋归属。
 *
 * <p>位置必须是结构化的（校区→区域→楼栋→楼层→房间），
 * 「哪栋楼故障最多」和「重复故障分析」都依赖 {@link #buildingIdOf} 与
 * {@link #pathOf} 这两个推导结果。</p>
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    /** 楼栋层级：1 校区 2 区域 3 楼栋 4 楼层 5 房间 */
    private static final int LEVEL_BUILDING = 3;

    private final LocationMapper locationMapper;

    /** 全量位置树（一次查库，内存组树，节点数在千级以内）。 */
    public List<OrderDtos.LocationNode> tree() {
        List<Location> all = locationMapper.selectList(
                new LambdaQueryWrapper<Location>()
                        .eq(Location::getStatus, 1)
                        .orderByAsc(Location::getLevel)
                        .orderByAsc(Location::getSort));

        Map<Long, List<Location>> byParent = new HashMap<>();
        for (Location loc : all) {
            byParent.computeIfAbsent(loc.getParentId() == null ? 0L : loc.getParentId(), k -> new ArrayList<>())
                    .add(loc);
        }
        return buildChildren(0L, byParent);
    }

    private List<OrderDtos.LocationNode> buildChildren(Long parentId, Map<Long, List<Location>> byParent) {
        List<Location> children = byParent.getOrDefault(parentId, List.of());
        return children.stream()
                .sorted(Comparator.comparing(Location::getSort, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(loc -> new OrderDtos.LocationNode(loc.getId(), loc.getParentId(), loc.getLevel(),
                        loc.getName(), loc.getCode(), loc.getType(),
                        buildChildren(loc.getId(), byParent)))
                .toList();
    }

    public Location get(Long id) {
        Location loc = locationMapper.selectById(id);
        if (loc == null || loc.getStatus() == null || loc.getStatus() != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "报修位置不存在或已停用");
        }
        return loc;
    }

    /** 位置全路径快照，如「主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 201」。 */
    public String pathOf(Long locationId) {
        List<String> names = new ArrayList<>();
        Long cursor = locationId;
        int guard = 0;
        while (cursor != null && cursor > 0 && guard++ < 10) {
            Location loc = locationMapper.selectById(cursor);
            if (loc == null) {
                break;
            }
            names.add(0, loc.getName());
            cursor = loc.getParentId();
        }
        return String.join(" / ", names);
    }

    /** 向上找最近的楼栋节点，用于区域统计与重复故障分析。 */
    public Long buildingIdOf(Long locationId) {
        Long cursor = locationId;
        int guard = 0;
        while (cursor != null && cursor > 0 && guard++ < 10) {
            Location loc = locationMapper.selectById(cursor);
            if (loc == null) {
                return null;
            }
            if (loc.getLevel() != null && loc.getLevel() == LEVEL_BUILDING) {
                return loc.getId();
            }
            cursor = loc.getParentId();
        }
        return null;
    }

    public Map<Long, String> nameMap() {
        Map<Long, String> map = new HashMap<>();
        locationMapper.selectList(null).forEach(l -> map.put(l.getId(), l.getName()));
        return map;
    }
}
