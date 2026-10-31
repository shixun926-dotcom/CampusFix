package com.campusfix.system.excel;

/**
 * Excel 中解析出的一行学生数据（尚未校验、未落库）。
 *
 * @param rowNum     Excel 行号（从 1 开始，含表头），用于错误定位
 * @param realName   姓名
 * @param studentNo  学号
 * @param className  班级
 * @param phone      手机号（可空）
 */
public record StudentRow(int rowNum, String realName, String studentNo, String className, String phone) {
}
