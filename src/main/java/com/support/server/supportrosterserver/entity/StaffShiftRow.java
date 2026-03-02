package com.support.server.supportrosterserver.entity;

import org.apache.fesod.sheet.annotation.ExcelProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StaffShiftRow {
    @ExcelProperty(index = 0)
    private String name;

    @ExcelProperty(index = 1)
    private String staffId;

    @ExcelProperty(index = 2)
    private String roleGroup;

    @ExcelProperty(index = 3)
    private String region;

    @ExcelProperty(index = 4)
    private String contact;

    @ExcelProperty(index = 5)
    private String notes;

    @ExcelProperty(index = 6)
    private String day1;

    @ExcelProperty(index = 7)
    private String day2;

    @ExcelProperty(index = 8)
    private String day3;

    @ExcelProperty(index = 9)
    private String day4;

    @ExcelProperty(index = 10)
    private String day5;

    @ExcelProperty(index = 11)
    private String day6;

    @ExcelProperty(index = 12)
    private String day7;

    @ExcelProperty(index = 13)
    private String day8;

    @ExcelProperty(index = 14)
    private String day9;

    @ExcelProperty(index = 15)
    private String day10;

    @ExcelProperty(index = 16)
    private String day11;

    @ExcelProperty(index = 17)
    private String day12;

    @ExcelProperty(index = 18)
    private String day13;

    @ExcelProperty(index = 19)
    private String day14;

    @ExcelProperty(index = 20)
    private String day15;

    @ExcelProperty(index = 21)
    private String day16;

    @ExcelProperty(index = 22)
    private String day17;

    @ExcelProperty(index = 23)
    private String day18;

    @ExcelProperty(index = 24)
    private String day19;

    @ExcelProperty(index = 25)
    private String day20;

    @ExcelProperty(index = 26)
    private String day21;

    @ExcelProperty(index = 27)
    private String day22;

    @ExcelProperty(index = 28)
    private String day23;

    @ExcelProperty(index = 29)
    private String day24;

    @ExcelProperty(index = 30)
    private String day25;

    @ExcelProperty(index = 31)
    private String day26;

    @ExcelProperty(index = 32)
    private String day27;

    @ExcelProperty(index = 33)
    private String day28;

    @ExcelProperty(index = 34)
    private String day29;

    @ExcelProperty(index = 35)
    private String day30;

    @ExcelProperty(index = 36)
    private String day31;

    public String getShiftCodeByDay(int day) {
        if (day < 1 || day > 31) {
            return null;
        }
        return switch (day) {
            case 1 -> day1;
            case 2 -> day2;
            case 3 -> day3;
            case 4 -> day4;
            case 5 -> day5;
            case 6 -> day6;
            case 7 -> day7;
            case 8 -> day8;
            case 9 -> day9;
            case 10 -> day10;
            case 11 -> day11;
            case 12 -> day12;
            case 13 -> day13;
            case 14 -> day14;
            case 15 -> day15;
            case 16 -> day16;
            case 17 -> day17;
            case 18 -> day18;
            case 19 -> day19;
            case 20 -> day20;
            case 21 -> day21;
            case 22 -> day22;
            case 23 -> day23;
            case 24 -> day24;
            case 25 -> day25;
            case 26 -> day26;
            case 27 -> day27;
            case 28 -> day28;
            case 29 -> day29;
            case 30 -> day30;
            case 31 -> day31;
            default -> null;
        };
    }
}
