package com.st.lap.dynamicReportTemplate.model;

import java.math.BigDecimal;
import java.sql.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "cc_contract_master")
@Data
public class CcContractMaster {

	@Id
	@Column(name = "contract_number")
	private String contractNumber;
	
	@Column(name = "revision_number")
	private BigDecimal revisionNumber;
	
	@Column(name = "contract_status")
	private BigDecimal contractStatus;
	
	@Column(name = "customer_code")
	private String customerCode;
	
	@Column(name = "contract_branch")
	private String contractBranch;
	
	@Column(name = "spoke")
	private BigDecimal spoke;
	
	@Column(name = "outreach")
	private BigDecimal outreach;
	
	@Column(name = "agreement_date")
	private Date agreementDate;
	
	@Column(name = "accounting_date")
	private Date accountingDate;
	
	@Column(name = "application_number")
	private String applicationNumber;
	
	@Column(name = "application_date")
	private Date applicationDate;
	
	@Column(name = "product_code")
	private BigDecimal productCode;
	
	@Column(name = "division_code")
	private BigDecimal divisionCode;
	
	@Column(name = "contract_type")
	private BigDecimal contractType;
	
	@Column(name = "parent_contract_number")
	private String parentContractNumber;
	
	@Column(name = "scheme_code")
	private BigDecimal schemeCode;
	
	@Column(name = "amount_financed")
	private BigDecimal amountFinanced;
	
	@Column(name = "finance_charges")
	private BigDecimal financeCharges;
	
	@Column(name = "amount_repayable")
	private BigDecimal amountRepayable;
	
	@Column(name = "tenure")
	private BigDecimal tenure;
	
	@Column(name = "advance_arrears_ind")
	private BigDecimal advanceArrearsInd;
	
	@Column(name = "repayment_frequency")
	private BigDecimal repaymentFrequency;
	
	@Column(name = "no_of_instalments")
	private BigDecimal noofInstalments;
	
	@Column(name = "moratorium_period")
	private BigDecimal moratoriumPeriod;
	
	@Column(name = "first_instalment_due_date")
	private Date firstInstalmentDueDate;
	
	@Column(name = "contract_end_date")
	private Date contractEndDate;
	
	@Column(name = "no_of_units")
	private BigDecimal noofUnits;
	
	@Column(name = "total_asset_cost")
	private BigDecimal totalAssetCost;
	
	@Column(name = "total_down_payment_company")
	private BigDecimal totalDownPaymentCompany;
	
	@Column(name = "total_down_payment_dealer")
	private BigDecimal totalDownPaymentDealer;
	
	@Column(name = "repayment_mode")
	private BigDecimal repaymentMode;
	
	@Column(name = "repayment_employer_code")
	private String repaymentEmployerCode;
	
	@Column(name = "repayment_employer_branch")
	private String repaymentEmployerBranch;
	
	@Column(name = "amount_paid")
	private BigDecimal amountPaid;
	
	@Column(name = "dealer_credit_period")
	private BigDecimal dealerCreditPeriod;
	
	@Column(name = "payment_due_date")
	private BigDecimal paymentDueDate;
	
	@Column(name = "afc_rate")
	private BigDecimal afcRate;
	
	@Column(name = "afc_credit_rate")
	private BigDecimal afcCreditRate;
	
	@Column(name = "contract_irr")
	private BigDecimal contractIrr;
	
	@Column(name = "address_slno")
	private BigDecimal addressSlno;
	
	@Column(name = "txn_id")
	private long txnId;
	
	@Column(name = "emi")
	private BigDecimal emi;
	
	@Column(name = "monthly_instalment_possible")
	private BigDecimal monthlyInstalmentPossible;
	
	@Column(name = "proposal_type_code")
	private BigDecimal proposalTypeCode;
	
	@Column(name = "moratorium_applicable")
	private BigDecimal moratoriumApplicable;
	
	@Column(name = "source_of_application")
	private BigDecimal sourceofApplication;
	
	@Column(name = "authorised_rep_code")
	private String authorisedRepCode;
	
	@Column(name = "bdo_code")
	private String bdoCode;
	
	@Column(name = "sales_associate_code")
	private String salesAssociateCode;
	
	@Column(name = "owner_of_property")
	private BigDecimal ownerofProperty;
	
	@Column(name = "borrower_category")
	private BigDecimal borrowerCategory;
	
	@Column(name = "loan_category")
	private BigDecimal loanCategory;
	
	@Column(name = "payment_date")
	private Date paymentDate;
	
	@Column(name = "creation_date")
	private Date creationDate;
	
	@Column(name = "hire_period")
	private BigDecimal hirePeriod;
	
	@Column(name = "external_leg_appl")
	private String externalLegAppl;
	
	@Column(name = "external_tech_appl")
	private String externalTechAppl;
	
	@Column(name = "remarks")
	private String remarks;
	
	@Column(name = "deviation_appl")
	private String deviationAppl;
	
	@Column(name = "accounting_branch")
	private String accountingBranch;
	
	@Column(name = "lead_id")
	private BigDecimal leadId;
	
	@Column(name = "lead_branch")
	private String leadBranch;
	
	@Column(name = "ext_credit_appraiser")
	private BigDecimal extCreditAppraiser;
	
	@Column(name = "no_of_own_houses")
	private BigDecimal noofOwnHouses;
	
	@Column(name = "dscr_value")
	private BigDecimal dscrValue;
	
	@Column(name = "reference")
	private String reference;
	
	@Column(name = "corporate_loan_type")
	private BigDecimal corporateLoanType;
	
	@Column(name = "acct_no")
	private String acctNo;
	
	@Column(name = "approval_remarks")
	private String approvalRemarks;
	
	@Column(name = "borrower_profile")
	private BigDecimal borrowerProfile;
	
	@Column(name = "tranch_serial_no")
	private BigDecimal tranchSerialNo;
	
	@Column(name = "file_no_locator")
	private String fileNoLocator;
	
	@Column(name = "no_of_months")
	private BigDecimal noofMonths;
	
	@Column(name = "emi_due_date")
	private BigDecimal emiDueDate;
	
	@Column(name = "ecs_appr_ind")
	private String ecsApprInd;
	
	@Column(name = "cur_repay_mode")
	private BigDecimal curRepayMode;
	
	@Column(name = "income_assessed_code")
	private BigDecimal incomeAssessedCode;
	
	@Column(name = "employee_name")
	private String employeeName;
	
	@Column(name = "ar_remarks")
	private String arRemarks;
	
	@Column(name = "premium_amt")
	private BigDecimal premiumAmt;
	
	@Column(name = "applicability")
	private String applicability;
	
	@Column(name = "add_applicable")
	private String addApplicable;
	
	@Column(name = "take_over_institution_code")
	private BigDecimal takeOverInstitutionCode;
	
	@Column(name = "take_over_reason_code")
	private BigDecimal takeOverReasonCode;
	
	@Column(name = "first_emi_due_date")
	private Date firstEmiDueDate;
	
	@Column(name = "flexible_billing_date")
	private Date flexibleBillingDate;
	
	@Column(name = "ar_approved")
	private String arApproved;
	
	@Column(name = "loan_proposal_remarks")
	private String loanProposalRemarks;
	
	@Column(name = "disb_file_number")
	private String disbFileNumber;
	
	@Column(name = "ar_app_remarks")
	private String arAppRemarks;
	
	@Column(name = "amort_pattern")
	private BigDecimal amortPattern;
	
	@Column(name = "amort_emi")
	private BigDecimal amortEmi;
	
	@Column(name = "enhancement_ind")
	private String enhancementInd;
	
	@Column(name = "justification")
	private String justification;
	
	@Column(name = "connector_code")
	private BigDecimal connectorCode;
	
	@Column(name = "connector_remarks")
	private String connectorRemarks;
	
	@Column(name = "con_approved")
	private String conApproved;
	
	@Column(name = "con_appr_remarks")
	private String conApprRemarks;
	
	@Column(name = "slm_loc_code")
	private String slmLocCode;
	
	@Column(name = "sales_associate_code_hd")
	private String salesAssociateCodeHd;
	
	@Column(name = "cr_score_collateral")
	private BigDecimal crScoreCollateral;
	
	@Column(name = "cr_score_wo_collateral")
	private BigDecimal crScoreWoCollateral;
	
	@Column(name = "purpose_of_loan")
	private String purposeofLoan;
	
	@Column(name = "source_by_role")
	private BigDecimal sourceByRole;
	
	@Column(name = "source_by_name")
	private String sourceByName;
	
	@Column(name = "followed_by_role")
	private BigDecimal followedByRole;
	
	@Column(name = "followed_by_name")
	private String followedByName;
	
	@Column(name = "risk_code")
	private BigDecimal riskCode;
	
	@Column(name = "lcb_pai_applicability")
	private String lcbPaiApplicability;
	
	@Column(name = "property_type_variant")
	private BigDecimal propertyTypeVariant;
	
}
