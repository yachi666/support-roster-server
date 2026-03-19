package com.support.server.supportrosterserver.entity;

import org.apache.fesod.sheet.annotation.ExcelProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ShiftDefinitionRow {
    @ExcelProperty(index = 0)
    private String team;

    @ExcelProperty(index = 1)
    private String code;

    @ExcelProperty(index = 2)
    private String meaning;

    @ExcelProperty(index = 3)
    private String startTime;

    @ExcelProperty(index = 4)
    private String endTime;

    @ExcelProperty(index = 5)
    private String timezone;

    @ExcelProperty(index = 6)
    private String showOnRosterPage;

    @ExcelProperty(index = 7)
    private String remark;
}
