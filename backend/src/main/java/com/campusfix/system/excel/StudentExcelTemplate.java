package com.campusfix.system.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 生成学生导入模板。
 *
 * <p>第一个工作表「学生信息」只放表头，导入时按 sheet(0) 解析；
 * 第二个工作表「填写说明」写清各列规则，避免用户凭猜填写。</p>
 */
@Component
public class StudentExcelTemplate {

    private static final List<String> NOTES = List.of(
            "1. 请勿修改表头文字、列顺序，也不要增删列，否则导入会被拒绝。",
            "2. 姓名：必填，最多 50 字。",
            "3. 学号：必填，4-30 位数字或字母，系统内唯一；同时作为登录账号。",
            "4. 班级：必填，例如「计算机2201」。",
            "5. 手机号：选填，填写则必须是 11 位手机号（1 开头）。",
            "6. 初始密码统一为 Cf@123456，学生首次登录后需修改。",
            "7. 单次最多导入 2000 行；空白行会被自动跳过。",
            "8. 建议先填写 1-2 行试导入，确认无误后再批量导入。"
    );

    public byte[] build() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("学生信息");
            CellStyle headStyle = headStyle(workbook);

            Row head = sheet.createRow(0);
            head.setHeightInPoints(26);
            for (int i = 0; i < StudentExcelParser.HEADERS.size(); i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(StudentExcelParser.HEADERS.get(i));
                cell.setCellStyle(headStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }
            sheet.createFreezePane(0, 1);

            Sheet note = workbook.createSheet("填写说明");
            note.setColumnWidth(0, 90 * 256);
            for (int i = 0; i < NOTES.size(); i++) {
                Row r = note.createRow(i);
                r.createCell(0).setCellValue(NOTES.get(i));
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle headStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
