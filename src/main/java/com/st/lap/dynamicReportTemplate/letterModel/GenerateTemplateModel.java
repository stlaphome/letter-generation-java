/**
 * 
 */
package com.st.lap.dynamicReportTemplate.letterModel;


import java.util.Date;

import lombok.Data;

@Data
public class GenerateTemplateModel {

	private String productCode;
	private String templateName;
	private String sanctionDate;
	private String applicationNumber;
	private String templateType;
	
	
}