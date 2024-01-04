package com.letter.report.dynamicReportTemplate.letterModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Data;

@Data
public class PropertyDetailModel {
	private Set<PropertyNumberModel> propertyNumberModelList;
	private Set<TitleHolderDetail> titleHolderDetailList;
	private Map<String,Set<ScheduleA>> scheduleListMap;
	private Map<String,ScheduleB> scheduleBListMap;
	private Map<String,Boundries> boundriesListMap;
	private Map<String,Measurement> measurementListMap;
	

}
