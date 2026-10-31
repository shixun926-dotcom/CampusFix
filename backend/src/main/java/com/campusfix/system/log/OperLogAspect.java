package com.campusfix.system.log;

import com.campusfix.common.result.TraceId;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.common.security.LoginUser;
import com.campusfix.system.mapper.SysOperLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志切面：拦截标了 {@link OperLog} 的接口，把「谁、何时、对什么、
 * 传了什么、成功与否、耗时多久」落到 sys_oper_log。
 *
 * <p>纪律：日志失败绝不影响业务（全程 try/catch）；参数先脱敏再截断；
 * 未登录或非 HTTP 上下文（如定时任务内部调用）跳过记录。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final int MAX_PARAM_LENGTH = 2000;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String MASK = "***";

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            save(joinPoint, operLog, start, e);
            throw e;
        } finally {
            if (result != null) {
                save(joinPoint, operLog, start, null);
            }
        }
    }

    private void save(ProceedingJoinPoint joinPoint, OperLog operLog,
                      long start, Throwable error) {
        try {
            SysOperLog logEntry = new SysOperLog();
            logEntry.setModule(operLog.module());
            logEntry.setOperation(operLog.action());
            logEntry.setMethod(joinPoint.getSignature().toShortString());

            LoginUser me = currentUserOrNull();
            if (me != null) {
                logEntry.setUserId(me.getId());
                logEntry.setUsername(me.getUsername());
            }

            HttpServletRequest request = currentRequest();
            if (request != null) {
                logEntry.setRequestUri(truncate(request.getRequestURI(), 255));
                logEntry.setIp(resolveIp(request));
            }
            logEntry.setRequestParam(truncate(mask(joinPoint.getArgs(), operLog.masked()), MAX_PARAM_LENGTH));

            if (error == null) {
                logEntry.setStatus(1);
            } else {
                logEntry.setStatus(0);
                logEntry.setErrorMsg(truncate(
                        StringUtils.hasText(error.getMessage()) ? error.getMessage() : error.getClass().getSimpleName(),
                        MAX_ERROR_LENGTH));
            }
            logEntry.setCostMs((int) (System.currentTimeMillis() - start));
            operLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 日志失败不影响业务
            log.warn("操作日志记录失败 action={}", operLog.action(), e);
        }
    }

    // ------------------------------------------------------------------ 内部

    private LoginUser currentUserOrNull() {
        try {
            return CurrentUserHolder.get();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /** 参数序列化 + 脱敏 + 截断。无法序列化的参数（流等）退化为类名列表。 */
    private String mask(Object[] args, String[] maskedKeys) {
        try {
            Map<String, Object> named = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof HttpServletRequest || arg instanceof ContentCachingRequestWrapper) {
                    continue;
                }
                named.put("arg" + i, arg);
            }
            String json = objectMapper.writeValueAsString(named);
            for (String key : maskedKeys) {
                json = json.replaceAll(
                        "(\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\")[^\"]*(\")",
                        "$1" + MASK + "$2");
            }
            return json;
        } catch (Exception e) {
            try {
                return Arrays.toString(Arrays.stream(args)
                        .map(a -> a == null ? "null" : a.getClass().getSimpleName()).toArray());
            } catch (Exception ignored) {
                return "(不可序列化)";
            }
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
