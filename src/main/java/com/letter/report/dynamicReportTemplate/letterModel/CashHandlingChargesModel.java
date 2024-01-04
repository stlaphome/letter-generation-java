package com.letter.report.dynamicReportTemplate.letterModel;

import lombok.Data;

@Data
public class CashHandlingChargesModel {
	private int id;

	private int fromReceiptAmt;

	private int toReceiptAmt;

	private int cashHandlingCharges;

	private String denomination;

	private String lastUpdatedBy;
}
