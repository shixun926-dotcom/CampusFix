package com.campusfix.system.excel;

import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学生导入 Excel 解析器。
 *
 * <p>只负责「读文件 + 校验表头 + 取列值」，不做业务校验、不碰数据库，
 * 便于脱离 Spring 上下文单独测试。</p>
 *
 * <p>表头必须与模板完全一致（顺序、文案都要一致），否则直接抛错，
 * 避免"列错位导致姓名写进学号"这类静默数据污染。</p>
 */
@Slf4j
@Component
public class StudentExcelParser {

    /** 模板表头，顺序即列序 */
    public static final List<String> HEADERS = List.of("姓名", "学号", "班级", "手机号");

    /** 单次导入上限，防止异常大文件打满内存 */
    public static final int MAX_ROWS = 2000;

    private static final DataFormatter FORMATTER = new DataFormatter();

    public List<StudentRow> parse(InputStream in) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "Excel 中没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            validateHeader(sheet);

            List<StudentRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isBlankRow(row)) {
                    continue;
                }
                // 行号按 Excel 习惯从 1 开始：下标 1 即第 2 行
                rows.add(new StudentRow(
                        i + 1,
                        cell(row, 0),
                        cell(row, 1),
                        cell(row, 2),
                        cell(row, 3)));

                if (rows.size() > MAX_ROWS) {
                    throw new BizException(ResultCode.PARAM_INVALID,
                            "单次最多导入 " + MAX_ROWS + " 名学生，请拆分文件后重试");
                }
            }

            if (rows.isEmpty()) {
                throw new BizException(ResultCode.PARAM_INVALID, "表格中没有数据行，请填写后再导入");
            }
            return rows;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析学生导入文件失败", e);
            throw new BizException(ResultCode.PARAM_INVALID,
                    "无法解析该文件，请确认是 .xlsx 格式且未加密，或重新下载模板填写");
        }
    }

    /** 表头必须与模板逐列一致 */
    private void validateHeader(Sheet sheet) {
        Row head = sheet.getRow(0);
        List<String> actual = new ArrayList<>();
        if (head != null) {
            for (int i = 0; i < head.getLastCellNum(); i++) {
                String v = cell(head, i);
                if (!v.isEmpty()) {
                    actual.add(v);
                }
            }
        }

        if (!actual.equals(HEADERS)) {
            String expected = HEADERS.stream().collect(Collectors.joining(" | "));
            String got = actual.isEmpty() ? "（空表头）" : actual.stream().collect(Collectors.joining(" | "));
            throw new BizException(ResultCode.PARAM_INVALID,
                    "模板格式不正确。期望表头：" + expected + "；实际表头：" + got
                            + "。请先下载模板，在模板中填写后再导入，不要增删或调整列顺序。");
        }
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < HEADERS.size(); i++) {
            if (!cell(row, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 取单元格文本：数字型学号也能正确转成 "2021001" 而不是 "2021001.0" */
    private String cell(Row row, int index) {
        if (row == null) {
            return "";
        }
        Cell c = row.getCell(index);
        if (c == null) {
            return "";
        }
        String v = FORMATTER.formatCellValue(c);
        if (v == null) {
            return "";
        }
        return v.replace('\u00A0', ' ').trim();
    }
}
