package com.st.lap.dynamicReportTemplate.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "ST_TB_LMS_DYNAMIC_VARIABLES_HDR")
public class DynamicVariables {
	
	@Id
	@Column(name = "dynamic_variables_hdr_key")
	private int dynamicVariablesHeaderKey;

	@Column(name = "branch_address" )
	private String branchAddress;

	@Column(name = "to_address" )
	private String toAddress;

	@Column(name = "application_number" )
	private String applicationNumber;
	
	@Column(name = "product" )
	private String product;

	@Column(name = "purpose_of_loan" )
	private String purposeofLoan;
	@Column(name = "emi" )
	private String emi;

	@Column(name = "upfront_processing_fee" )
	private String upfrontProcessingFee;

	@Column(name = "cersai_charges" )
	private String cersaiCharges;

	@Column(name = "appraisal_charges" )
	private String appraisalCharges;

	@Column(name = "switch_fee" )
	private String switchFee;

	@Column(name = "retrieval_charges" )
	private String retrievalCharges;

	@Column(name = "conversion_charges" )
	private String conversionCharges;

	@Column(name = "gst_tamilnadu" )
	private String gstTamilnadu;

	@Column(name = "gst_andra" )
	private String gstAndra;

	@Column(name = "gst_karnataka" )
	private String gstKarnataka;

	@Column(name = "gst_others" )
	private String gstOthers;

	@Column(name = "repricing_fee" )
	private String repricingFee;

	@Column(name = "ca_certification_fee" )
	private String caCertificationFee;

	@Column(name = "outstation_cheque_charges" )
	private String outstationChequeCharges;

	@Column(name = "outstation_cheque_charges_total" )
	private String outstationChequeChargesTotal;

	@Column(name = "pdc_charges" )
	private String pdcCharges;

	@Column(name = "swapping_charges" )
	private String swappingCharges;

	@Column(name = "travelling_expense" )
	private String travellingExpense;

	@Column(name = "bureau_charges_individual_customer" )
	private String bureauChargesIndividualCustomer;

	@Column(name = "bureau_charges_non_individual_customer" )
	private String bureauChargesNonIndividualCustomer;

	@Column(name = "penal_interest" )
	private String penalInterest;

	@Column(name = "cheque_dishonour_charges")
	private String chequeDishonourCharges;
//	@Column(name = "motd_title_execution")
//	private String motdTitleExecution;
//	@Column(name = "motd_run_day")
//	private String motdRunDay;
//	@Column(name = "motd_run_month_year")
//	private String motdRunMonthYear;
//	@Column(name = "motd_title_holder")
//	private String motdTitleHolder;
//	@Column(name = "motd_title_holder_aadhaar")
//	private String motdTitleHolderaadhaar;
//	@Column(name = "motd_title_holder_age")
//	private String motdTitleHolderAge;

}
