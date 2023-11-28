package com.st.lap.dynamicReportTemplate.letterModel;

import java.util.List;

import com.st.lap.dynamicReportTemplate.service.DynamicTemplateService.CashHandlingChargesModel;

import lombok.Data;

@Data
public class LetterReportModel {

	//sanction
	private String contractNumber;
	private String applicationDate;
	private String branchCode;
	private String currentDate;
	private String customerCode;
	private String amountFinanced;
	private String purposeOfLoan;
	private String netRate;
	private String term;
	private String emiAmount;
	private String processingFee;
	private String baseFileNumber;
	private String endUse;
	private String endUseOfLoan;
	private String useOfLoan;
	private String companyName;
	private String branchMailId;
	private String telePhoneNumber;
	private String branchAddress;
	private String customerName;
	private String customerAddress;
	private String accountNo;
	private String applicationNumber;
	//mitc
	private String product;
	private String balancePayable;
	private String documentationCharges;
	private String prePaymentCharges;
	private List<CashHandlingChargesModel> cashHandlingCharges;
	private String chequeReturnCharges;
	
	
	
	
	
}
