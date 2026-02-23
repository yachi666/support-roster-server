package com.support.server.supportrosterserver.repository;

import java.util.ArrayList;
import java.util.List;

import com.support.server.supportrosterserver.entity.ColorRow;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.exception.ExcelAnalysisException;
import lombok.Getter;

public class ColorDataListener extends AnalysisEventListener<ColorRow> {

    @Getter
    private List<ColorRow> dataList = new ArrayList<>();

    @Override
    public void invoke(ColorRow data, AnalysisContext context) {
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
