package com.campusfix.system.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusfix.common.result.PageResult;
import com.campusfix.system.mapper.SysOperLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 操作日志查询（管理端）。数据由 {@link OperLogAspect} 写入，这里只读。
 */
@Service
@RequiredArgsConstructor
public class OperLogService {

    private final SysOperLogMapper operLogMapper;

    public PageResult<OperLogVO> page(Query query) {
        LambdaQueryWrapper<SysOperLog> w = new LambdaQueryWrapper<SysOperLog>()
                .eq(query.getUserId() != null, SysOperLog::getUserId, query.getUserId())
                .like(StringUtils.hasText(query.getUsername()), SysOperLog::getUsername,
                        StringUtils.trimWhitespace(query.getUsername()))
                .eq(StringUtils.hasText(query.getModule()), SysOperLog::getModule, query.getModule())
                .eq(query.getStatus() != null, SysOperLog::getStatus, query.getStatus())
                .ge(StringUtils.hasText(query.getBegin()), SysOperLog::getCreatedAt,
                        parseStart(query.getBegin()))
                .le(StringUtils.hasText(query.getEnd()), SysOperLog::getCreatedAt, parseEnd(query.getEnd()))
                .orderByDesc(SysOperLog::getId);

        IPage<SysOperLog> page = operLogMapper.selectPage(
                new Page<>(query.getPage(), Math.min(query.getSize(), 100)), w);
        return PageResult.of(page, this::toVO);
    }

    /** 模块下拉去重列表（表很小，直接全量 distinct）。 */
    public java.util.List<String> modules() {
        return operLogMapper.selectList(new LambdaQueryWrapper<SysOperLog>()
                        .select(SysOperLog::getModule))
                .stream().map(SysOperLog::getModule)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    // ------------------------------------------------------------------ 内部

    private LocalDateTime parseStart(String date) {
        return StringUtils.hasText(date) ? LocalDateTime.parse(date + "T00:00:00") : null;
    }

    private LocalDateTime parseEnd(String date) {
        return StringUtils.hasText(date) ? LocalDateTime.parse(date + "T23:59:59") : null;
    }

    private OperLogVO toVO(SysOperLog l) {
        return new OperLogVO(l.getId(), l.getUserId(), l.getUsername(), l.getModule(),
                l.getOperation(), l.getMethod(), l.getRequestUri(), l.getRequestParam(),
                l.getIp(), l.getStatus(), l.getErrorMsg(), l.getCostMs(), l.getCreatedAt());
    }

    public record OperLogVO(Long id, Long userId, String username, String module,
                            String operation, String method, String requestUri,
                            String requestParam, String ip, Integer status,
                            String errorMsg, Integer costMs, LocalDateTime createdAt) {
    }

    @Data
    public static class Query {
        private int page = 1;
        private int size = 20;
        private Long userId;
        private String username;
        private String module;
        /** 1 成功 0 失败 */
        private Integer status;
        /** yyyy-MM-dd */
        private String begin;
        /** yyyy-MM-dd */
        private String end;
    }
}
