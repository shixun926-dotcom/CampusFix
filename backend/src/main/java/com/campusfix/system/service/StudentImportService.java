package com.campusfix.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.result.ResultCode;
import com.campusfix.system.dto.ImportDtos.ImportResult;
import com.campusfix.system.dto.ImportDtos.RowError;
import com.campusfix.system.entity.SysRole;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.excel.StudentExcelParser;
import com.campusfix.system.excel.StudentRow;
import com.campusfix.system.mapper.SysRoleMapper;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 学生批量导入。
 *
 * <p>策略：<b>逐行校验、部分成功</b>。格式错误的行跳过并记录原因，
 * 合法行正常入库，最后返回「总数 / 成功 / 失败 + 明细」，便于管理员修正后重传。</p>
 *
 * <p>不使用整体事务：若中途某行失败就回滚全部，会让管理员无法分批修复；
 * 逐行写入后用唯一索引兜底，重复学号会被捕获并计入失败明细。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentImportService {

    private static final String DEFAULT_PASSWORD = "Cf@123456";
    private static final Pattern STUDENT_NO = Pattern.compile("^[0-9A-Za-z]{4,30}$");
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private final StudentExcelParser parser;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public ImportResult importStudents(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "请选择要导入的 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase().endsWith(".xlsx")
                || filename.toLowerCase().endsWith(".xls"))) {
            throw new BizException(ResultCode.PARAM_INVALID, "仅支持 .xlsx / .xls 格式的文件");
        }

        List<StudentRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = parser.parse(in);
        } catch (IOException e) {
            log.warn("读取导入文件失败", e);
            throw new BizException(ResultCode.STORAGE_ERROR, "读取文件失败，请重新上传");
        }

        // 一次性查出已存在的学号，避免逐行查库
        List<String> nos = rows.stream().map(StudentRow::studentNo).filter(StringUtils::hasText).distinct().toList();
        Set<String> exists = nos.isEmpty() ? Set.of()
                : userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getStudentNo, nos))
                        .stream().map(SysUser::getStudentNo).filter(StringUtils::hasText)
                        .collect(Collectors.toSet());

        Long studentRoleId = roleIdOf(RoleCode.STUDENT);

        Set<String> seen = new HashSet<>();
        List<RowError> errors = new ArrayList<>();
        int success = 0;

        for (StudentRow row : rows) {
            String reason = validate(row, seen, exists);
            if (reason != null) {
                addError(errors, row, reason);
                continue;
            }
            seen.add(row.studentNo());

            try {
                SysUser user = toUser(row);
                userMapper.insert(user);
                if (studentRoleId != null) {
                    userRoleMapper.insert(user.getId(), studentRoleId);
                }
                success++;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发写入，或该学号属于已被逻辑删除的账号（唯一索引仍占位）
                addError(errors, row, "学号已被占用（可能存在于已删除账号），请先在系统中处理");
            } catch (Exception e) {
                log.warn("导入第 {} 行失败", row.rowNum(), e);
                addError(errors, row, "写入失败：" + e.getMessage());
            }
        }

        int failed = rows.size() - success;
        log.info("学生导入完成：总 {} 行，成功 {}，失败 {}", rows.size(), success, failed);
        return new ImportResult(rows.size(), success, failed, errors);
    }

    /** 返回 null 表示通过，否则返回失败原因 */
    private String validate(StudentRow row, Set<String> seen, Set<String> exists) {
        if (!StringUtils.hasText(row.realName())) {
            return "姓名不能为空";
        }
        if (row.realName().length() > 50) {
            return "姓名超过 50 字";
        }
        if (!StringUtils.hasText(row.studentNo())) {
            return "学号不能为空";
        }
        if (!STUDENT_NO.matcher(row.studentNo()).matches()) {
            return "学号格式不正确（需 4-30 位数字或字母）";
        }
        if (!StringUtils.hasText(row.className())) {
            return "班级不能为空";
        }
        if (row.className().length() > 50) {
            return "班级超过 50 字";
        }
        if (StringUtils.hasText(row.phone()) && !PHONE.matcher(row.phone()).matches()) {
            return "手机号格式不正确";
        }
        if (seen.contains(row.studentNo())) {
            return "文件内该学号重复";
        }
        if (exists.contains(row.studentNo())) {
            return "学号已存在于系统中";
        }
        return null;
    }

    private SysUser toUser(StudentRow row) {
        SysUser user = new SysUser();
        user.setUsername(row.studentNo());
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRealName(row.realName());
        user.setStudentNo(row.studentNo());
        user.setClassName(row.className());
        user.setPhone(StringUtils.hasText(row.phone()) ? row.phone() : null);
        user.setCreditScore(100);
        user.setStatus(1);
        user.setPwdResetFlag(1);
        return user;
    }

    private void addError(List<RowError> errors, StudentRow row, String reason) {
        if (errors.size() < ImportResult.MAX_ERRORS) {
            errors.add(new RowError(row.rowNum(), row.studentNo(), row.realName(), reason));
        }
    }

    private Long roleIdOf(String code) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
        return role == null ? null : role.getId();
    }
}
