package com.support.server.supportrosterserver.repository;

import java.util.ArrayList;
import java.util.List;

import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.exception.ExcelAnalysisException;

import com.support.server.supportrosterserver.entity.ColorDefinitionRow;

import lombok.Getter;

public class ColorDefinitionDataListener extends AnalysisEventListener<ColorDefinitionRow> {

    @Getter
    private final List<ColorDefinitionRow> dataList = new ArrayList<>();

    @Override
    public void invoke(ColorDefinitionRow data, AnalysisContext context) {
        dataList.add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) {
        throw new ExcelAnalysisException("Excel解析异常", exception);
    }
}