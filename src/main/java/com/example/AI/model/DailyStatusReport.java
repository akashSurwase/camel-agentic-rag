package com.example.AI.model;

import lombok.Getter;
import lombok.Setter;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@Getter
@Setter
@CsvRecord(separator = ",", skipFirstLine = true)
public class DailyStatusReport {

    @DataField(pos = 1)
    private String incidentDate;

    @DataField(pos = 2)
    private String serviceName;

    @DataField(pos = 3)
    private String operation;

    @DataField(pos = 4)
    private String status;

    @DataField(pos = 5)
    private String exceptionClass;

    @DataField(pos = 6)
    private String logSnippet;
}