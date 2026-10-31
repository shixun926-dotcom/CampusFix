package com.campusfix.system.dto;

import java.util.List;

/**
 * 批量导入结果。
 */
public final class ImportDtos {

    private ImportDtos() {
    }

    /**
     * @param total   表格中的数据行数（不含空行）
     * @param success 成功入库数量
     * @param failed  失败数量
     * @param errors  失败明细，最多返回 {@link #MAX_ERRORS} 条
     */
    public record ImportResult(int total, int success, int failed, List<RowError> errors) {

        public static final int MAX_ERRORS = 200;

        public boolean hasError() {
            return failed > 0;
        }
    }

    /**
     * @param rowNum    Excel 行号（从 1 开始，含表头）
     * @param studentNo 学号
     * @param realName  姓名
     * @param reason    失败原因
     */
    public record RowError(int rowNum, String studentNo, String realName, String reason) {
    }
}
