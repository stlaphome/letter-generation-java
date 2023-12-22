package com.letter.report.dynamicReportTemplate.letterModel;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class PropertyDetailModel {
	private List<PropertyNumberModel> propertyNumberModelList;
	private List<TitleHolderDetail> titleHolderDetailList;
	private Map<String,List<ScheduleA>> scheduleListMap;
	private Map<String,ScheduleB> scheduleBListMap;
	private Map<String,Boundries> boundriesListMap;
	private Map<String,Measurement> measurementListMap;
	

}
