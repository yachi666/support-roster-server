package com.support.server.supportrosterserver.repository;

import java.util.ArrayList;
import java.util.List;

import com.support.server.supportrosterserver.entity.StaffShiftRow;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.exception.ExcelAnalysisException;
import lombok.Getter;

public class StaffShiftDataListener extends AnalysisEventListener<StaffShiftRow> {

    @Getter
    private List<StaffShiftRow> dataList = new ArrayList<>();

    @Override
    public void invoke(StaffShiftRow data, AnalysisContext context) {
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
