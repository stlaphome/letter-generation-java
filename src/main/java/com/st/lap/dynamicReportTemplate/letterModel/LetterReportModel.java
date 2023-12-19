package com.st.lap.dynamicReportTemplate.letterModel;

import java.util.List;
import java.util.Map;


import lombok.Data;

@Data
public class LetterReportModel {

	//sanction
	private String contractNumber;
	private String applicationDate;
	private String branchCode;
	private String currentDate;
	private String customerCode;
	private int amountFinanced;
	private String purposeOfLoan;
	private String netRate;
	private int term;
	private int emiAmount;
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
	private String applicant;
	private String coApplicant1;
	private String coApplicant2;
	private String coApplicant3;
	private String divisionCode;
	private String schemeCode;
	private String productCode;
	private String borrower;
	private String endUseOfLoanCode;
	private String reference;
	//mitc
	private String product;
	private String balancePayable;
	private String documentationCharges;
	private String prePaymentCharges;
	private List<CashHandlingChargesModel> cashHandlingCharges;
	private String chequeReturnCharges;
	private String flatFee;
	private String flatRate;
	private String denomination;
	private String lifeInsurance;
	private int moratoriumPeriod;
	//motd
	private List<String> aadharNo;
	private List<String> titleHolderName; 
	private List<String> titleHolderGuardianName; 
	private String dateOfBirth; 
	private List<Integer> age; 
	private int principalOutstanding;
	private String SRO; 
	private String rateType;
	private String rateTypeString;
	private Map<String,List<ScheduleA>> scheduleA;
	private List<ScheduleB> schedleB;
	private List<Measurement> measurement;
	private List<Boundries> boundries;
	private List<PropertyAddress> propertyAddressList;
	private List<String> customerAddressList;
	
	
	
	
}
