package com.st.lap.dynamicReportTemplate.letterModel;

import java.util.List;

import lombok.Data;

@Data
public class PropertyDetailModel {
	private List<PropertyNumberModel> propertyNumberModelList;
	private List<TitleHolderDetail> titleHolderDetailList;

}
