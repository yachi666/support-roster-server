package com.support.server.supportrosterserver.entity;

import org.apache.fesod.sheet.annotation.ExcelProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ColorRow {
    @ExcelProperty(index = 0)
    private String code;

    @ExcelProperty(index = 1)
    private String meaning;

    @ExcelProperty(index = 2)
    private String colorName;

    @ExcelProperty(index = 3)
    private String rgb;

    @ExcelProperty(index = 4)
    private String colorHex;
}
