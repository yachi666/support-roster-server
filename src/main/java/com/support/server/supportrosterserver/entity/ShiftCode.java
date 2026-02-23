package com.support.server.supportrosterserver.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftCode {
    private String code;
    private String meaning;
    private String colorName;
    private String colorHex;
}
