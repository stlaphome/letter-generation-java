package com.letter.report.dynamicReportTemplate.letterModel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Data;

@Data
public class PropertyDetailModel {
	private LinkedHashSet<PropertyNumberModel> propertyNumberModelList;
	private LinkedHashSet<TitleHolderDetail> titleHolderDetailList;
	private Map<String,LinkedHashSet<ScheduleA>> scheduleListMap;
	private Map<String,ScheduleB> scheduleBListMap;
	private Map<String,Boundries> boundriesListMap;
	private Map<String,Measurement> measurementListMap;
	private Map<String,LinkedSroDetails> linkedSroDetailMap;
	

}
