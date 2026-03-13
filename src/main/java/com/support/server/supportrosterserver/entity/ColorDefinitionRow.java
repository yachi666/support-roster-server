package com.support.server.supportrosterserver.entity;

import org.apache.fesod.sheet.annotation.ExcelProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ColorDefinitionRow {
    @ExcelProperty(index = 0)
    private String code;

    @ExcelProperty(index = 1)
    private String colorName;

    @ExcelProperty(index = 2)
    private String rgb;

    @ExcelProperty(index = 3)
    private String hex;
}