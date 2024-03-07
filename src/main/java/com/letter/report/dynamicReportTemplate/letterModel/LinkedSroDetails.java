package com.letter.report.dynamicReportTemplate.letterModel;

import lombok.Data;

@Data
public class LinkedSroDetails {
	public String linkedDocumentNumber;
	public String linkedDocumentDate;
	public String linkedSro;
	public String linkedSroDistrict;
	private String linkedCustomerCode;
	private int linkedPropertyNumber;
	private String documentId;
	

}
