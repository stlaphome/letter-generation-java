package com.st.lap.dynamicReportTemplate.model;

import lombok.Data;

@Data
public class DynamicTemplateModel {
	
	private int templateHeaderKey;
	private String templateName;
	private String templateKey;
	private String content;
	private Boolean active;
	private String userName;
	private String mode;

}
