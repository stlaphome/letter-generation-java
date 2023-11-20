package com.st.lap.dynamicReportTemplate.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.sql.DataSource;
import javax.sql.rowset.serial.SerialBlob;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.st.lap.dynamicDataSource.service.DynamicDataSourceService;
import com.st.lap.dynamicReportTemplate.model.DynamicReportContainer;
import com.st.lap.dynamicReportTemplate.model.DynamicTemplate;
import com.st.lap.dynamicReportTemplate.model.DynamicTemplateModel;
import com.st.lap.dynamicReportTemplate.model.DynamicVariables;
import com.st.lap.dynamicReportTemplate.model.GenerateTemplateModel;
import com.st.lap.dynamicReportTemplate.model.LetterProduct;
import com.st.lap.dynamicReportTemplate.repo.DynamicReportContainerRepo;
import com.st.lap.dynamicReportTemplate.repo.DynamicTemplateRepo;
import com.st.lap.dynamicReportTemplate.repo.DynamicVariablesRepo;
import com.st.lap.dynamicReportTemplate.repo.LetterProductRepo;

import freemarker.template.Configuration;
import lombok.Data;
import lombok.NoArgsConstructor;

@Service
public class DynamicTemplateService {

	@Autowired
	DynamicTemplateRepo dynamicTemplateRepo;

	@Autowired
	DynamicVariablesRepo dynamicVariablesRepo;

	@Autowired
	DynamicReportContainerRepo dynamicReportContainerRepo;
	
	@Autowired
	LetterProductRepo letterProductRepo;

	@Autowired
	JavaMailSender javaMailSender;

	@Autowired
	Configuration fmConfiguration;

	@Autowired
	ResourceLoader loader;

	@Autowired
	private WebClient webClient;
	
	@Autowired
    private  DynamicDataSourceService dynamicDataSourceService;


	@Value("${stlap.server.url}")
	private String stlapServerUrl;

	@Value("${mail.server.url}")
	private String mailServerUrl;

	public ResponseEntity<String> saveTemplate(DynamicTemplateModel dynamicTemplateModel) {
		String errorContent = validateEditorContent(dynamicTemplateModel.getContent(), returnVariablesList());
		if (Objects.isNull(errorContent)) {
			DynamicTemplate dynamicTemplate = new DynamicTemplate();
			Blob blob;
			try {
				blob = (Blob) new SerialBlob(dynamicTemplateModel.getContent().getBytes());
				dynamicTemplate.setContent(blob);
			} catch (Exception e) {
				e.printStackTrace();
			}
			dynamicTemplate.setActive(dynamicTemplateModel.getActive());
			dynamicTemplate.setTemplateName(dynamicTemplateModel.getTemplateName());
			if (dynamicTemplateModel.getMode().equalsIgnoreCase("NEW")) {
				SecureRandom secureRandom;
				try {
					secureRandom = SecureRandom.getInstance("SHA1PRNG");
					int randomValue = secureRandom.nextInt();
					if (randomValue < 0) {
						dynamicTemplate.setTemplateHeaderKey(randomValue * -1);
					} else {
						dynamicTemplate.setTemplateHeaderKey(randomValue);
					}
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			} else {
				dynamicTemplate.setTemplateHeaderKey(dynamicTemplateModel.getTemplateHeaderKey());
			}
			dynamicTemplate.setTemplateKey(dynamicTemplateModel.getTemplateKey());
			dynamicTemplate.setCreatedBy(dynamicTemplateModel.getUserName());
			dynamicTemplate.setCreatedDate(new Date());
			dynamicTemplate.setLastModifiedBy(dynamicTemplateModel.getUserName());
			dynamicTemplate.setLastModifiedDate(new Date());

			if (dynamicTemplateModel.getActive()) {
				List<DynamicTemplate> dynamicTemplateList = dynamicTemplateRepo
						.findByTemplateNameAndActive(dynamicTemplateModel.getTemplateName(), true);
				if (CollectionUtils.isEmpty(dynamicTemplateList)) {
					dynamicTemplateRepo.save(dynamicTemplate);

				} else {
					dynamicTemplateList.stream().forEach(item -> {
						item.setActive(false);
					});
					dynamicTemplateList.add(dynamicTemplate);
					dynamicTemplateRepo.saveAll(dynamicTemplateList);

				}
			} else {
				dynamicTemplateRepo.save(dynamicTemplate);
			}
			return ResponseEntity
					.ok(dynamicTemplateModel.getMode().equalsIgnoreCase("NEW") ? "Template Saved Successfully"
							: "Template Updated Successfully");
		}
		return ResponseEntity.ok("Error Invalid Variable Name(s): " + errorContent);
	}

	private String validateEditorContent(String content, List<String> returnVariablesList) {
		StringBuilder errorContent = new StringBuilder("");
		String[] stringSplitup = content.split("//");
		List<String> wordsList = Arrays.asList(stringSplitup);
		Set<String> variablesPresentList = new HashSet<String>();
		AtomicBoolean errorExists = new AtomicBoolean(false);
		wordsList.stream().forEach(item -> {
			if (Pattern.matches("^~~.*", item)) {
				if (returnVariablesList.contains(String.valueOf("//" + item + "//"))) {
					variablesPresentList.add(item);
				} else {
					errorExists.set(true);
					errorContent.append(" , " + item);
				}
			}
		});
		return errorExists.get() ? errorContent.toString() : null;
	}

	public ResponseEntity<DynamicTemplateModel> getTemplate(DynamicTemplateModel dynamicTemplateModel) {
		Optional<DynamicTemplate> optionalDynamicTemplate = dynamicTemplateRepo.findByTemplateNameAndTemplateKey(
				dynamicTemplateModel.getTemplateName(), dynamicTemplateModel.getTemplateKey());

		if (!optionalDynamicTemplate.isPresent()) {
			return ResponseEntity.ok(null);
		} else {
			DynamicTemplate dynamicTemplate = optionalDynamicTemplate.get();

			byte[] bdata;
			Blob blob = dynamicTemplate.getContent();
			try {
				bdata = blob.getBytes(1, (int) blob.length());
				String s = new String(bdata);
				dynamicTemplateModel.setContent(s);
			} catch (Exception e) {
				e.printStackTrace();
			}
			dynamicTemplateModel.setActive(dynamicTemplate.getActive());
			dynamicTemplateModel.setTemplateHeaderKey(dynamicTemplate.getTemplateHeaderKey());
			dynamicTemplateModel.setTemplateKey(dynamicTemplate.getTemplateKey());
			dynamicTemplateModel.setTemplateName(dynamicTemplate.getTemplateName());
			return ResponseEntity.ok(dynamicTemplateModel);
		}

	}

	public ResponseEntity<List<Map>> getTemplateKey(Map<String, String> dataMap) {
		List<DynamicTemplate> dynamicTemplateList = dynamicTemplateRepo.findByTemplateName(dataMap.get("templateName"));
		List<Map> templateKeyList = new ArrayList<>();
		dynamicTemplateList.stream().forEach(item -> {
			Map<String, String> tempMap = new HashMap<>();
			tempMap.put("key", item.getTemplateKey());
			tempMap.put("value", item.getTemplateKey());
			tempMap.put("text", item.getTemplateKey());
			templateKeyList.add(tempMap);
		});
		return ResponseEntity.ok(templateKeyList);
	}

	public ResponseEntity<List<String>> getVariablesList(String templateName) {

		return ResponseEntity.ok(returnVariablesList());
	}

	public ResponseEntity<List<String>> getTemplateNameList() {
		List<DynamicTemplate> dynamicTemplateList = dynamicTemplateRepo.findAll();
		Set<String> templateNameSet = new HashSet<>();
		List<String> templateNameList = new ArrayList<>();
		dynamicTemplateList.stream().forEach(item -> {
			templateNameSet.add(item.getTemplateName());
		});
		templateNameList.addAll(templateNameSet);
		return ResponseEntity.ok(templateNameList);
	}

	@SuppressWarnings("unchecked")
	public Map<String, String> returnVariablesDataMapForMITC(String applicationNumber) {
		Date date = new Date();
		SimpleDateFormat formatter1 = new SimpleDateFormat("MM/dd/yyyy");

		Map<String, String> dataMap = new HashMap<>();
		dataMap.put("applicationNum", applicationNumber);
		dataMap.put("type", "accrual");

		// Get Los Customer Data
		Map<String, Object> returnResponse = webClient.post()
				.uri(stlapServerUrl + "/losCustomer/getCustomerDataByAppNum")
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).bodyValue(dataMap).retrieve()
				.bodyToMono(Map.class).block();

		// Calculate Documentation Charges
		ResponseEntity<Map> feeDataResponse = webClient.post().uri(stlapServerUrl + "/additionalfee/getFeeData")
				.bodyValue(dataMap).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).retrieve()
				.toEntity(Map.class).block();

		List<Map<String, String>> feeDataList = (List<Map<String, String>>) feeDataResponse.getBody().get("gridData");
		AtomicInteger documentationCharges = new AtomicInteger();
		feeDataList.stream().filter(item -> item.get("details").equalsIgnoreCase("DOCUMENTATION CHARGES"))
		.forEach(item -> {
			int tempValue = getInt(item.get("receiveable")) - getInt(item.get("received"));
			documentationCharges.set(tempValue);
		});

		// Amort Calculation for Balance Payable
		Calendar calendar = Calendar.getInstance();
		Date currentDate = getDate(calendar.getTime());
		calendar.set(Calendar.DATE, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
		Date dueStartDate = getDate(calendar.getTime());
		Double balancePayable = 0.0;

		ResponseEntity<List<Amort>> amortDataResponse = webClient.post()
				.uri(stlapServerUrl + "/repayment/getAmortListResponse").bodyValue(dataMap)
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).retrieve().toEntityList(Amort.class)
				.block();
		List<Amort> amortData = amortDataResponse.getBody();
		balancePayable = amortData.stream().filter(amort -> (amort.getDueStartDate().after(dueStartDate)
				|| amort.getDueStartDate().compareTo(dueStartDate) == 0)).mapToDouble(Amort::getEmiDue).sum();

		// Cash Handling Charges Calculation
		ResponseEntity<List<CashHandlingChargesModel>> cashHandlingResponse = webClient.get()
				.uri(stlapServerUrl + "/cashHandlingCharges/findByMaxEffectiveDate")
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).retrieve()
				.toEntityList(CashHandlingChargesModel.class).block();
		List<CashHandlingChargesModel> cashHandlingChargesList = cashHandlingResponse.getBody();

		StringBuilder cashHandlingChargesTables = new StringBuilder(
				"<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 55.25pt; border-collapse: collapse; mso-table-layout-alt: fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\"><tbody><tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\"><td style=\\\"width: 150pt; border: 1pt solid black; background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Amount of Remittance</td><td style=\\\"width: 150pt; border-top: 1pt solid black; border-right: 1pt solid black; border-bottom: 1pt solid black; border-image: initial; border-left: none; background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Applicable Charges</td></tr><tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 150.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> Upto Rs.2000/-</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> NIL</td></tr>");
		cashHandlingChargesList.stream().forEach(item -> {
			cashHandlingChargesTables.append(
					"<tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 150.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
			cashHandlingChargesTables
			.append("Rs." + item.getFromReceiptAmt() + "/- to Rs." + item.getToReceiptAmt() + "/-");
			cashHandlingChargesTables.append(
					"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
			cashHandlingChargesTables.append("Rs." + item.getCashHandlingCharges() + "/- + GST Per Receipt");
			cashHandlingChargesTables.append("</td></tr>");
		});
		cashHandlingChargesTables.append("</tbody></table>");
		List<Object> scheduledChangeTablesList = new ArrayList<>();
		//scheduledChangeTablesList.add("Test");
		StringBuilder scheduledChangeTables = new StringBuilder(
				"<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 55.25pt; border-collapse: collapse; mso-table-layout-alt: fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\">"
						+ "<tbody>"
						+ "<tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\">"
						+ "<td style=\\\"width: 150pt; border: 1pt solid black; background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document Name</td>"
						+ "<td style=\\\"width: 150pt; border-top: 1pt solid black; border-right: 1pt solid black; border-bottom: 1pt solid black;border-left: none;  background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document No</td>"
						+ "<td style=\\\"width: 150pt; border-top: 1pt solid black; border-right: 1pt solid black; border-bottom: 1pt solid black;border-left: none;  background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document Date</td>"
						+ "<td style=\\\"width: 150pt; border-top: 1pt solid black; border-right: 1pt solid black; border-bottom: 1pt solid black;border-left: none;  background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Title Holder</td></tr>");

		scheduledChangeTablesList.stream().forEach(item -> {
			scheduledChangeTables.append(
					"<tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 150.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
			scheduledChangeTables
			.append(item);
			scheduledChangeTables.append(
					"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
			scheduledChangeTables.append(item);
			scheduledChangeTables.append(
					"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
			scheduledChangeTables.append(item);
			scheduledChangeTables.append(
					"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
			scheduledChangeTables.append(item);
			scheduledChangeTables.append("</td></tr>");
		});
		scheduledChangeTables.append("</tbody></table>");
		//annxure table
		List<Map<String,Object>> annexureChrgeTableList = new ArrayList<>();
		
		Map<String,Object> annexureTableValuesMap = new HashMap<>();
		annexureTableValuesMap.put("Documentation Charges", "Kerala – Rs.800/-, Rajasthan – Rs.700/-, Maharashtra & Gujarat\r\n"
				+ "Rs.600/- and Other states Rs.450/-");
		annexureTableValuesMap.put("Switch Fee", "0.5% of the Principal Outstanding + GST");
		annexureTableValuesMap.put("Statement Charges", "Rs.500/- + GST. Not applicable if requested for the first time in a\r\n"
				+ "financial year.");
		annexureChrgeTableList.add(annexureTableValuesMap);
		StringBuilder annexureChrgeTables = new StringBuilder(
				"<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 04.00pt; border-collapse: collapse; mso-table-layout-alt: fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\">"
						+ "<tbody>"
						+ "<tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\">"
						+ "<td style=\\\"width: 15pt; border: 1pt solid black; background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">SL\r\nNo</td>"
						+ "<td style=\\\"width: 150pt; border: 1pt solid black; background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">DESCRIPTION</td>"
						+ "<td style=\\\"width: 250pt; border-top: 1pt solid black; border-right: 1pt solid black; border-bottom: 1pt solid black;border-left: none;  background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">CHARGES\r\n"
						+ "(Wherever applicable, charges are subject to GST on inclusive\r\n"
						+ "/exclusive basis)</td></tr>");
		annexureChrgeTableList.stream().forEach(item -> {
			int index = 1;
			for (Map.Entry<String, Object> entry : item.entrySet()) {
				
				annexureChrgeTables.append(
						"<tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 50.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
				annexureChrgeTables
				.append(index);
				annexureChrgeTables.append(
						"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
				annexureChrgeTables.append(entry.getKey());
				annexureChrgeTables.append(
						"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
				annexureChrgeTables.append(entry.getValue());
				annexureChrgeTables.append("</td></tr>");
				++index;
			}
			
		});
		annexureChrgeTables.append("</tbody></table>");

		// Prepayment Charges Calculation
		dataMap.put("prepayment_reason", "PRE - OWN FUNDS");
		ResponseEntity<PrepaymentChargesModel> prepaymentResponse = webClient.post()
				.uri(stlapServerUrl + "/prepaymentCharges/findByReasonAndMaxEffectiveDate").bodyValue(dataMap)
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).retrieve()
				.toEntity(PrepaymentChargesModel.class).block();

		PrepaymentChargesModel prepaymentModel = prepaymentResponse.getBody();
		String prepaymentCharge = "";
		if(prepaymentModel!=null) {
			prepaymentCharge = String.valueOf(prepaymentModel.getRate().intValue());
		}


		// ChequeReturnCharges Calculation

		dataMap.put("parameterName", "ChequeReturnCharges");
		Map<String, Object> parameterResponse = webClient.post().uri(stlapServerUrl + "/parameter/getParameterByName")
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).bodyValue(dataMap).retrieve()
				.bodyToMono(Map.class).block();
		String todayDate = formatter1.format(currentDate);
		String chequeReturnCharges = (todayDate.compareTo(parameterResponse.get("paramEffStartDate").toString()) >= 0
				&& todayDate.compareTo(parameterResponse.get("paramEffEndDate").toString()) <= 0)
						? parameterResponse.get("paramValue").toString()
						: "0";
		int loanAmount = (int)Math.round((Double) returnResponse.get("loanAmt"));
		int sanctionAmount = (int)Math.round((Double) returnResponse.get("sanctionAmt"));		
		DynamicVariables dynamicVariables = dynamicVariablesRepo.findByApplicationNumber("TEST");
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		String space5 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		String space10 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		String space20 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		String space25 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		String space30 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		YearMonth yearMonth = YearMonth.now();
        String formattedDate = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
		Map<String, String> variablesValueMap = new HashMap<String, String>();
		String toAddress = returnResponse.get("customerName") + ",<br>" +dynamicVariables.getToAddress();
		String[] branchAddress = dynamicVariables.getBranchAddress().split(",");
		String branchNewAddress = getExpandedAddress(branchAddress);
		String toNewAddress = returnResponse.get("customerName") +getExpandedAddress(dynamicVariables.getToAddress().split(","));
		variablesValueMap.put("~~Branch_Address~~", dynamicVariables.getBranchAddress());
		variablesValueMap.put("~~Date~~", formatter.format(date));
		variablesValueMap.put("~~To_Address~~", toNewAddress);
		variablesValueMap.put("~~Application_Number~~", returnResponse.get("applicationNum").toString());
		variablesValueMap.put("~~Loan_Amount~~", String.valueOf(loanAmount));
		variablesValueMap.put("~~Loan_Amount_In_Words~~", convertToIndianCurrency(String.valueOf(loanAmount)));
		variablesValueMap.put("~~Product~~", dynamicVariables.getProduct());
		variablesValueMap.put("~~Purpose_of_Loan~~", dynamicVariables.getPurposeofLoan());
		variablesValueMap.put("~~Term~~", String.valueOf(returnResponse.get("tenure")));
		variablesValueMap.put("~~ROI~~", String.valueOf(returnResponse.get("rateOfInterest")));
		variablesValueMap.put("~~EMI~~", dynamicVariables.getEmi());
		variablesValueMap.put("~~Upfront_Processing_Fee~~", dynamicVariables.getUpfrontProcessingFee());
		variablesValueMap.put("~~Balance_Payable~~", String.valueOf((int) Math.round(balancePayable)));
		variablesValueMap.put("~~Documentation_Charges~~", String.valueOf(documentationCharges.get()));
		variablesValueMap.put("~~CERSAI_Charges~~", dynamicVariables.getCersaiCharges());
		variablesValueMap.put("~~Appraisal_Charges~~", dynamicVariables.getAppraisalCharges());
		variablesValueMap.put("~~Switch_Fee~~", dynamicVariables.getSwitchFee());
		variablesValueMap.put("~~Retrieval_Charges~~", dynamicVariables.getRetrievalCharges());
		variablesValueMap.put("~~Conversion_Charges~~", dynamicVariables.getConversionCharges());
		variablesValueMap.put("~~Cheque_Return_Charges~~", chequeReturnCharges);
		variablesValueMap.put("~~GST_Tamilnadu~~", dynamicVariables.getGstTamilnadu());
		variablesValueMap.put("~~GST_Andra~~", dynamicVariables.getGstAndra());
		variablesValueMap.put("~~GST_Karnataka~~", dynamicVariables.getGstKarnataka());
		variablesValueMap.put("~~GST_Others~~", dynamicVariables.getGstOthers());
		variablesValueMap.put("~~Repricing_Fee~~", dynamicVariables.getRepricingFee());
		variablesValueMap.put("~~CA_Certification_Fee~~", dynamicVariables.getCaCertificationFee());
		variablesValueMap.put("~~Outstation_Cheque_Charges~~", dynamicVariables.getOutstationChequeCharges());
		variablesValueMap.put("~~Outstation_Cheque_Charges_Total~~",
				dynamicVariables.getOutstationChequeChargesTotal());
		variablesValueMap.put("~~PDC_Charges~~", dynamicVariables.getPdcCharges());
		variablesValueMap.put("~~Swapping_Charges~~", dynamicVariables.getSwappingCharges());
		variablesValueMap.put("~~Travelling_Expense~~", dynamicVariables.getTravellingExpense());
		variablesValueMap.put("~~Bureau_Charges_Individual_Customer~~",
				dynamicVariables.getBureauChargesIndividualCustomer());
		variablesValueMap.put("~~Bureau_Charges_Non_Individual_Customer~~",
				dynamicVariables.getBureauChargesNonIndividualCustomer());
		variablesValueMap.put("~~Prepayment_Charges~~", prepaymentCharge);
		variablesValueMap.put("~~Penal_Interest~~", dynamicVariables.getPenalInterest());
		variablesValueMap.put("~~Cheque_Dishonour_Charges~~", dynamicVariables.getChequeDishonourCharges());
		variablesValueMap.put("~~Cash_Handling_Charges_Table~~", cashHandlingChargesTables.toString());
		variablesValueMap.put("~~Annexures_Tables~~", annexureChrgeTables.toString());
		variablesValueMap.put("~~MOTD_Title_Execution~~", "TEST");
		variablesValueMap.put("~~MOTD_Run_Day~~", String.valueOf(date.getDay()));
		variablesValueMap.put("~~MOTD_Run_Month_Year~~", formattedDate);
		variablesValueMap.put("~~MOTD_Title_Holder~~", String.valueOf(returnResponse.get("customerName")));
		variablesValueMap.put("~~Schedule_Detail_Table~~", scheduledChangeTables.toString());
		variablesValueMap.put("~~SRO~~", "SRO"); //
		variablesValueMap.put("~~MOTD_Title_Holder_Aadhaar~~", "9326 4143 9726");
		variablesValueMap.put("~~MOTD_Title_Holder_Age~~", "40");
		variablesValueMap.put("~~MOTD_Title_Holder_Guardian~~", "Gokila");
		variablesValueMap.put("~~MOTD_Title_Holder_Address~~", "4/150 B3, Coimbatore");
		variablesValueMap.put("~~MOTD_Title_Holder_1~~", "TEST");
		variablesValueMap.put("~~MOTD_Title_Holder_Aadhaar_1~~", "TEST");
		variablesValueMap.put("~~MOTD_Title_Holder_Age_1~~", "TEST");
		variablesValueMap.put("~~MOTD_Title_Holder_Guardian_1~~", "TEST");
		variablesValueMap.put("~~MOTD_Title_Holder_Address_1~~", "TEST");
		variablesValueMap.put("~~MOTD_Registered_Date~~", "TEST");
		variablesValueMap.put("~~MOTD_Registered_Doc_no~~", "TEST");
		variablesValueMap.put("~~MOTD_Registered_Office~~", "TEST");
		variablesValueMap.put("~~MOTD_Registered_Sub_Office~~", "TEST");
		variablesValueMap.put("~~MOTD_Clearance_Date~~", "TEST");
		variablesValueMap.put("~~MOTD_Favour_Of~~", "Sundaram Home Finance Limited"); //
		variablesValueMap.put("~~MOTD_Sanction_Amount~~", String.valueOf(sanctionAmount));
		variablesValueMap.put("~~MOTD_Sanction_Amount_Words~~",
				convertToIndianCurrency(String.valueOf(sanctionAmount)));
		variablesValueMap.put("~~MOTD_Mortgage_Type~~", "TEST");
		variablesValueMap.put("~~MOTD_SRO_District~~", space30.concat(space20).concat("&nbsp;&nbsp;").concat("THIRUVALLUR")); //
		variablesValueMap.put("~~MOTD_SRO_Place~~", space30.concat(space30).concat(space5).concat("REDHILLS"));
		variablesValueMap.put("~~MOTD_District~~", space30.concat(space30).concat("&nbsp;").concat("THIRUVALLUR"));
		variablesValueMap.put("~~MOTD_Taluk~~", space30.concat(space30).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat("MADHAVARAM"));
		variablesValueMap.put("~~MOTD_Village~~",  space30.concat(space30).concat("&nbsp;").concat("NARAVARIKUPPAM"));
		variablesValueMap.put("~~MOTD_Survey_Additional_Survey~~",space20.concat("&nbsp;&nbsp;").concat("91/104B and NOW PONNERI TALUK"));
		variablesValueMap.put("~~MOTD_Plot_No~~", space30.concat(space25).concat(space5).concat("&nbsp;").concat("91/104B."));
		variablesValueMap.put("~~MOTD_Door_No~~", space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat("3/1 3/2 3/3"));
		variablesValueMap.put("~~MOTD_Project_Name~~", space25.concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(""));
		variablesValueMap.put("~~MOTD_Flat_No~~", space30.concat(space5).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(""));
		variablesValueMap.put("~~MOTD_Floor~~", space30.concat(space10).concat("&nbsp;&nbsp;").concat(""));
		variablesValueMap.put("~~MOTD_Block_No~~", space30.concat(space5).concat(""));
		variablesValueMap.put("~~MOTD_Address_1~~", space30.concat(space25).concat("&nbsp;&nbsp;").concat("AS PER SITE DOOR NO 3 3/1 3/2 3/3"));
		variablesValueMap.put("~~MOTD_Address_2~~", space30.concat(space25).concat("&nbsp;&nbsp;").concat("S NO 91/2A MUTHURAMALINGA DEVAR STREET"));
		variablesValueMap.put("~~MOTD_Address_3~~", space30.concat(space25).concat("&nbsp;&nbsp;").concat("NOW MUTHURAMALINGAM STREET REDHILLS"));
		variablesValueMap.put("~~MOTD_Pin_Code~~", space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat("600052"));
		variablesValueMap.put("~~MOTD_Land_Extent~~", space25.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat("550.00 Sq.Ft"));
		variablesValueMap.put("~~MOTD_North_Boundary~~", space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat("HOUSE OF MANIKA ASARI"));
		variablesValueMap.put("~~MOTD_South_Boundary~~", space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat("HOUSE OF BASKAR"));
		variablesValueMap.put("~~MOTD_East_Boundary~~", space30.concat(space30).concat("&nbsp;").concat("REMAINING LAND OF SELVAMANI"));
		variablesValueMap.put("~~MOTD_West_Boundary~~", space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").concat("MUTHURAMALINGAMDEVARSTREET"));
		variablesValueMap.put("~~MOTD_North_Measurement~~", space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat("40’00”"));
		variablesValueMap.put("~~MOTD_South_Measurement~~", space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat("40’00”"));
		variablesValueMap.put("~~MOTD_East_Measurement~~", space30.concat(space30).concat("&nbsp;").concat("60’00”"));
		variablesValueMap.put("~~MOTD_West_Measurement~~", space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").concat("60’00”"));
		variablesValueMap.put("~~Property_Under_Mortgaged~~", "TEST");
		variablesValueMap.put("~~Property_Boundary_Details~~", "TEST");
		return variablesValueMap;
	}

	private String getExpandedAddress(String[] addressContent) {
		String newAddress = "";
		 for (String singleAddress : addressContent) {
			 if(newAddress.isEmpty()) {
				 newAddress= newAddress+singleAddress;
			 }else {
				 newAddress = newAddress+",<br>" +singleAddress;
			 }
	        }
		 return newAddress;
		
	}

	public List<String> returnVariablesList() {
		return Arrays.asList("//~~Branch_Address~~//", "//~~Date~~//", "//~~To_Address~~//","//~~SRO~~//",
				"//~~Application_Number~~//", "//~~Loan_Amount~~//", "//~~Loan_Amount_In_Words~~//", "//~~Product~~//",
				"//~~Purpose_of_Loan~~//", "//~~Term~~//", "//~~ROI~~//", "//~~EMI~~//",
				"//~~Upfront_Processing_Fee~~//", "//~~Balance_Payable~~//", "//~~Documentation_Charges~~//",
				"//~~CERSAI_Charges~~//", "//~~Appraisal_Charges~~//", "//~~Switch_Fee~~//",
				"//~~Retrieval_Charges~~//", "//~~Conversion_Charges~~//", "//~~Cheque_Return_Charges~~//",
				"//~~GST_Tamilnadu~~//", "//~~GST_Andra~~//", "//~~GST_Karnataka~~//", "//~~GST_Others~~//",
				"//~~Repricing_Fee~~//", "//~~CA_Certification_Fee~~//", "//~~Outstation_Cheque_Charges~~//",
				"//~~Outstation_Cheque_Charges_Total~~//", "//~~PDC_Charges~~//", "//~~Swapping_Charges~~//",
				"//~~Travelling_Expense~~//", "//~~Bureau_Charges_Individual_Customer~~//",
				"//~~Bureau_Charges_Non_Individual_Customer~~//", "//~~Prepayment_Charges~~//",
				"//~~Penal_Interest~~//", "//~~Cheque_Dishonour_Charges~~//", "//~~Cash_Handling_Charges_Table~~//",
				"//~~MOTD_Title_Execution~~//", "//~~MOTD_Run_Day~~//", "//~~MOTD_Run_Month_Year~~//",
				"//~~MOTD_Title_Holder~~//", "//~~MOTD_Title_Holder_Aadhaar~~//", "//~~MOTD_Title_Holder_Age~~//",
				"//~~MOTD_Title_Holder_Guardian~~//", "//~~MOTD_Title_Holder_Address~~//",
				"//~~MOTD_Title_Holder_1~~//", "//~~MOTD_Title_Holder_Aadhaar_1~~//", "//~~MOTD_Title_Holder_Age_1~~//",
				"//~~MOTD_Title_Holder_Guardian_1~~//", "//~~MOTD_Title_Holder_Address_1~~//",
				"//~~MOTD_Registered_Date~~//", "//~~MOTD_Registered_Doc_no~~//", "//~~MOTD_Registered_Office~~//",
				"//~~MOTD_Registered_Sub_Office~~//", "//~~MOTD_Clearance_Date~~//", "//~~MOTD_Favour_Of~~//",
				"//~~MOTD_Sanction_Amount~~//", "//~~MOTD_Sanction_Amount_Words~~//", "//~~MOTD_Mortgage_Type~~//","//~~Schedule_Detail_Table~~//",
				"//~~MOTD_SRO_District~~//", "//~~MOTD_SRO_Place~~//", "//~~MOTD_District~~//", "//~~MOTD_Taluk~~//",
				"//~~MOTD_Village~~//", "//~~MOTD_Survey_Additional_Survey~~//", "//~~MOTD_Plot_No~~//",
				"//~~MOTD_Door_No~~//", "//~~MOTD_Project_Name~~//", "//~~MOTD_Flat_No~~//", "//~~MOTD_Floor~~//",
				"//~~MOTD_Block_No~~//", "//~~MOTD_Address_1~~//", "//~~MOTD_Address_2~~//", "//~~MOTD_Address_3~~//",
				"//~~MOTD_Pin_Code~~//", "//~~MOTD_Land_Extent~~//", "//~~MOTD_North_Boundary~~//",
				"//~~MOTD_South_Boundary~~//", "//~~MOTD_East_Boundary~~//", "//~~MOTD_West_Boundary~~//",
				"//~~MOTD_North_Measurement~~//", "//~~MOTD_South_Measurement~~//", "//~~MOTD_East_Measurement~~//",
				"//~~MOTD_West_Measurement~~//", "//~~Property_Under_Mortgaged~~//",
				"//~~Property_Boundary_Details~~//","//~~Type_Of_Loan~~//","//~~Repayment_Mode~~//","//~~Account_No~~//","//~~End_Use_of_Loan~~//",
				"//~~Cersai Fee~~//","//~~kerala_Document_Charges~~//","//~~Rajasthan_Document_Charges~~//","//~~Maha_Gujarat_Document_Charges~~//","//~~Other_Document_Charges~~//",
				"//~~MOTD Fee~~//","//~~Statement_Charges~~//","//~~Settlement_Figure_Charges~~//","//~~Document_Retrieval_Charges~~//","//~~Cheque_Return_Charges~~//",
				"//~~TamilNadu_Document_Handling_Charges~~//","//~~Andra_Document_Handling_Charges~~//","//~~Karnataka_Document_Handling_Charges~~//","//~~Madhya_Document_Handling_Charges~~//",
				"//~~Photocopy_Charges~~//","//~~Custodial_Charges~~//","//~~Annexures_Tables~~//",
				"//~~Sanction_Loan_Amount~~//","//~~Sanction_Processing_Fee~~//","//~~Sanction_Term~~//",
				"//~~Sanction_Net_Rate~~//","//~~Sanction_EMI~~//","//~~Sanction_Account_No~~//","//~~Sanction_End_Use_of_Loan~~//","//~~Sanction_Purpose_of_Loan~~//",
				"//~~Sanction_Header_Company_Name~~//","//~~Sanction_Current_Date~~//","//~~Sanction_Branch_Address~~//","//~~Sanction_To_Address~~//",
				"//~~Sanction_TelePhone_No~~//","//~~Sanction_Header_Mail~~//","//~~Sanction_Header_Branch_Address~~//");
	}
	public String replaceValues(String content, String applicationNumber) {
		Map<String, String> valuesMap = returnVariablesDataMapForMITC(applicationNumber);
		StringBuilder returnValue = new StringBuilder(content);
		valuesMap.entrySet().stream().forEach(value -> {
			String temp = returnValue.toString();
			returnValue.delete(0, returnValue.length());
			returnValue.append(temp.replaceAll(String.valueOf("//" + value.getKey() + "//"),
					Objects.isNull(value.getValue()) ? "" : value.getValue()));
		});
		//System.out.println(returnValue.toString());
		return returnValue.toString();
	}

	public ResponseEntity<List<String>> getAllApplicationNumbers() {
Map<String,Object> dataMap = new HashMap<>();
dataMap.put("status", "Sanctioned");
		List<String> returnResponse = webClient.post().uri(stlapServerUrl + "/losCustomer/getAppNumByStatus")
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).bodyValue(dataMap).retrieve()
				.bodyToMono(List.class).block();
		return ResponseEntity.ok(returnResponse);
	}

	public ResponseEntity<byte[]> getTemplateForView(Map<String, String> dataMap) {
		DynamicTemplate dynamicTemplate = dynamicTemplateRepo
				.findByTemplateNameAndActive(dataMap.get("templateName"), true).get(0);
		DynamicTemplateModel dynamicTemplateModel = new DynamicTemplateModel();
		byte[] bdata;
		Blob blob = dynamicTemplate.getContent();
		try {
			bdata = blob.getBytes(1, (int) blob.length());
			String s = new String(bdata);
			dynamicTemplateModel.setContent(s);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ResponseEntity
				.ok(convertHtmltoPdf(dynamicTemplateModel.getContent(), dynamicTemplate.getTemplateName()));
	}

	public byte[] convertHtmltoPdf(String htmlContent, String outFileName) {
		File output = new File(outFileName);
		FileOutputStream os;
		FileInputStream fl;
		byte[] arr = null;
		try {
			os = new FileOutputStream(output);
			 PdfDocument pdf = new PdfDocument((new PdfWriter(os)));
		        Document document = new Document(pdf, PageSize.A4);
		        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, event -> {
		            PdfCanvas canvas = new PdfCanvas(((PdfDocumentEvent) event).getPage());
		            canvas.beginText()
		                    .setFontAndSize(pdf.getDefaultFont(), 12)
		                    .moveText(36, 20) // Adjust the coordinates for the position of the page number
		                    .showText("Page " + ((PdfDocumentEvent) event).getDocument().getPageNumber(((PdfDocumentEvent) event).getPage()))
		                    .endText();
		        });
		        HtmlConverter.convertToPdf(htmlContent, pdf, new ConverterProperties());

			//HtmlConverter.convertToPdf(htmlContent, os);
			arr = new byte[(int) output.length()];
			fl = new FileInputStream(output);
			fl.read(arr);
			fl.close();
			return arr;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return arr;

	}

	public ResponseEntity<Map<String, Object>> generateLetter(GenerateTemplateModel model) {
		DynamicTemplate dynamicTemplate = dynamicTemplateRepo.findByTemplateNameAndActive(model.getTemplateName(), true)
				.get(0);
		if (model.getSanctionDate() != null) {
			return ResponseEntity.ok(generateReportForSanctionDate(model, dynamicTemplate));
		} else if (!model.getApplicationNumber().isEmpty()) {
			return ResponseEntity.ok(generateReportForApplicationNumber(model, dynamicTemplate));
		}
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("FilesList", new ArrayList());
		resultMap.put("Status", "Error Occured Letter Not Generated.");
		return ResponseEntity.ok(resultMap);
	}

	public Map<String, Object> generateReportForSanctionDate(GenerateTemplateModel model,
			DynamicTemplate dynamicTemplate) {
		Map<String, String> filesMap = new HashMap();
		filesMap.put("sanctiondate", model.getSanctionDate());
		Map<String, List<String>> contactDetails = new HashMap<>();
		List<Map<String, String>> returnResponse = webClient.post()
				.uri(stlapServerUrl + "/losCustomer/getBySanctionDate")
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).bodyValue(filesMap).retrieve()
				.bodyToMono(List.class).block();
		filesMap.clear();
		List<String> applicationList = new ArrayList<>();

		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
if(returnResponse.size()==0) {
	Map<String, Object> resultMap = new HashMap<>();
	resultMap.put("FilesList", filesMap);
	resultMap.put("Status", "No Application Found For this Sanctioned Date");
	resultMap.put("ApplicationList", applicationList);
	resultMap.put("ContactList", contactDetails);
	return resultMap;
}
		returnResponse.stream().forEach(application -> {
			try {
				String fileName = (dynamicTemplate.getTemplateName()).concat("_")
						.concat(application.get("applicationNum")).concat("_").concat(dateFormat.format(date))
						.concat(".pdf");

//				File file = new File("./downloads/letter_generation/" + fileName);

				filesMap.put(application.get("applicationNum"), fileName);
				applicationList.add(application.get("applicationNum"));
				List<String> contactDetailsList = new ArrayList<>();
				contactDetailsList.add(application.get("mobileNumber"));
				contactDetailsList.add(application.get("emailId"));
				contactDetails.put(application.get("applicationNum"), contactDetailsList);
//				FileOutputStream fos = new FileOutputStream(file);

				Blob blob = dynamicTemplate.getContent();

				byte[] bdata;

				bdata = blob.getBytes(1, (int) blob.length());

				String s = new String(bdata);

				String content = replaceValues(s, application.get("applicationNum"));

				blob = (Blob) new SerialBlob(content.getBytes());

				DynamicReportContainer reportContainer = new DynamicReportContainer();
				reportContainer.setReportFile(blob);
				reportContainer.setReportFileName(fileName);
				SecureRandom secureRandom;
				try {
					secureRandom = SecureRandom.getInstance("SHA1PRNG");
					int randomValue = secureRandom.nextInt();
					if (randomValue < 0) {
						reportContainer.setDynamicReportContainerHeaderKey(randomValue * -1);
					} else {
						reportContainer.setDynamicReportContainerHeaderKey(randomValue);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				dynamicReportContainerRepo.save(reportContainer);

//				HtmlConverter.convertToPdf(content, fos);
			} catch (Exception e) {
				e.printStackTrace();

			}
		});
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("FilesList", filesMap);
		resultMap.put("Status", "Letter Generated Successfully");
		resultMap.put("ApplicationList", applicationList);
		resultMap.put("ContactList", contactDetails);
		return resultMap;
	}

	public Map<String, Object> generateReportForApplicationNumber(GenerateTemplateModel model,
			DynamicTemplate dynamicTemplate) {
		List<String> applicationList = new ArrayList<>();
		Map<String, String> filesMap = new HashMap<>();
		Map<String, List<String>> contactDetails = new HashMap<>();
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
		try {
			String fileName = (dynamicTemplate.getTemplateName()).concat("_").concat(model.getApplicationNumber())
					.concat("_").concat(dateFormat.format(date)).concat(".pdf");

//			File file = new File("./downloads/letter_generation/" + fileName);
			filesMap.put("applicationNum", model.getApplicationNumber());

			Map<String, String> responseMap = webClient.post().uri(stlapServerUrl + "/losCustomer/getByAppNum")
					.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).bodyValue(filesMap).retrieve()
					.bodyToMono(Map.class).block();
			filesMap.clear();
			List<String> contactDetailsList = new ArrayList<>();
			contactDetailsList.add(responseMap.get("mobileNumber"));
			contactDetailsList.add(responseMap.get("emailId"));
			contactDetails.put(model.getApplicationNumber(), contactDetailsList);

			applicationList.add(model.getApplicationNumber());
			filesMap.put(model.getApplicationNumber(), fileName);

//			FileOutputStream fos = new FileOutputStream(file);

			Blob blob = dynamicTemplate.getContent();

			byte[] bdata;

			bdata = blob.getBytes(1, (int) blob.length());

			String s = new String(bdata);

			String content = replaceValues(s, model.getApplicationNumber());

			blob = (Blob) new SerialBlob(content.getBytes());

			DynamicReportContainer reportContainer = new DynamicReportContainer();
			reportContainer.setReportFile(blob);
			reportContainer.setReportFileName(fileName);
			SecureRandom secureRandom;
			try {
				secureRandom = SecureRandom.getInstance("SHA1PRNG");
				int randomValue = secureRandom.nextInt();
				if (randomValue < 0) {
					reportContainer.setDynamicReportContainerHeaderKey(randomValue * -1);
				} else {
					reportContainer.setDynamicReportContainerHeaderKey(randomValue);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			dynamicReportContainerRepo.save(reportContainer);

//			HtmlConverter.convertToPdf(content, fos);
		} catch (Exception e) {
			e.printStackTrace();

		}
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("FilesList", filesMap);
		resultMap.put("Status", "Letter Generated Successfully");
		resultMap.put("ApplicationList", applicationList);
		resultMap.put("ContactList", contactDetails);

		return resultMap;
	}

	public ResponseEntity<byte[]> getGeneratedFile(String filePath) {

		try {
			DynamicReportContainer report = dynamicReportContainerRepo.findByReportFileName(filePath);

			Blob blob = report.getReportFile();

			byte[] bdata;
			bdata = blob.getBytes(1, (int) blob.length());
			String s = new String(bdata);
			File file = new File(filePath);
			FileOutputStream fos = new FileOutputStream(file);
			 PdfDocument pdf = new PdfDocument((new PdfWriter(fos)));
		        Document document = new Document(pdf, PageSize.A4);
		        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, event -> {
		            PdfCanvas canvas = new PdfCanvas(((PdfDocumentEvent) event).getPage());
		            canvas.beginText()
		                    .setFontAndSize(pdf.getDefaultFont(), 12)
		                    .moveText(36, 20) // Adjust the coordinates for the position of the page number
		                    .showText("Page " + ((PdfDocumentEvent) event).getDocument().getPageNumber(((PdfDocumentEvent) event).getPage()))
		                    .endText();
		        });
		        HtmlConverter.convertToPdf(s, pdf, new ConverterProperties());
			//HtmlConverter.convertToPdf(s, fos);
			FileInputStream fis = new FileInputStream(file);
			byte[] bytes = new byte[(int) file.length()];
			fis.read(bytes);
			return ResponseEntity.ok(bytes);

		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return ResponseEntity.ok(new byte[0]);
 
		}

	}

	public ResponseEntity<String> sendNotification(Map<String, Object> dataMap) {
		List<String> applicationList = (List<String>) dataMap.get("applicationList");
		Map<String, String> filesMap = (Map<String, String>) dataMap.get("filesList");
		Map<String, List<String>> contactDetails = (Map<String, List<String>>) dataMap.get("contactList");
		StringBuilder errorExists = new StringBuilder("");
		try {
			if (dataMap.get("mode").equals("E-Mail")) {
				applicationList.stream().forEach(item -> {
					if (contactDetails.get(item).get(1).equals("")) {
						errorExists.append(item).append(",");
					} else {
						dataMap.put("toMail", contactDetails.get(item).get(1));

						String content = (String) dataMap.get("mailContent");
						content = content.replaceAll("/~~/", item);
						content = content + filesMap.get(item);
						String subject = (String) dataMap.get("subject");
						subject = subject.replaceAll("/~~/", item);
						String returnResponse = webClient.post().uri(mailServerUrl + "/mail/sendMailToCustomer")
								.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).bodyValue(dataMap)
								.retrieve().bodyToMono(String.class).block();
					}
				});
				if (errorExists.toString().equals("")) {
					return ResponseEntity.ok("Mail Sent Successfully");
				} else {
					return ResponseEntity
							.ok("Mail Not Send for : " + errorExists.toString() + "Since Mail Id Not Present");
				}

			} else {
				applicationList.stream().forEach(item -> {
					// Regular expression to accept valid phone number
					Pattern p = Pattern.compile("^\\d{10}$"); // Creating a pattern object
					// Creating a Matcher object
					Matcher matcher = p.matcher(contactDetails.get(item).get(0));
					// Verifying whether given phone number is valid
					if (!matcher.matches()) {
						errorExists.append(item).append(",");
					} else {
						dataMap.put("toNumber", contactDetails.get(item).get(0));
						String content = (String) dataMap.get("mailContent");
						content = content.replaceAll("/~~/", item);
						content = content + filesMap.get(item);
						String subject = (String) dataMap.get("subject");
						subject = subject.replaceAll("/~~/", item);

						String returnResponse = webClient.post()
								.uri(mailServerUrl + "/mobile/sendMobileNotificationToCustomer")
								.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).bodyValue(dataMap)
								.retrieve().bodyToMono(String.class).block();
					}
				});

				if (errorExists.toString().equals("")) {
					return ResponseEntity.ok("SMS Sent Successfully");
				} else {
					return ResponseEntity.ok("SMS Not Send for : " + errorExists.toString()
							+ "Since Mobile Number Not Present / Invalid");
				}

			}
		} catch (Exception exception) {
			return ResponseEntity.ok(exception.getMessage());
		}

	}

	private Integer getInt(Object object) {
		try {
			return (object == null || object.toString().equals("")) ? 0
					: Integer.parseInt(object.toString().replace(",", ""));
		} catch (Exception exception) {
			return 0;
		}
	}
	
	public ResponseEntity<List<Map<String, Object>>> getProductTypeList() {
		List<LetterProduct> letterproductAllData = letterProductRepo.findAll();
		 List<Map<String, Object>> productCodeList = letterproductAllData.stream().filter(distinctByKey(data->data.getProductCode())).map(datas->{
			 Map<String,Object> productMap = new HashMap<>();
			productMap.put("value",datas.getProductId());
			productMap.put("text",datas.getProductCode());
			return productMap;
		}).distinct().collect(Collectors.toList());
		return ResponseEntity.ok(productCodeList);
	}
	
	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		if (keyExtractor != null) {
			Map<Object, Boolean> seen = new ConcurrentHashMap<>();
			return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
		}
		return null;
	}

	public void performDatabaseSwitch() {
        // Your condition to switch to Oracle database
        if (shouldSwitchToOracle()) {
           // dynamicDataSourceService.switchToOracleDataSource();
        }

        // Use the current datasource to fetch data
        DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
        try (Connection connection = currentDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM your_table");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            // Process the result set
            while (resultSet.next()) {
                // Process each row
            }

        } catch (SQLException e) {
            // Handle SQL exception
            e.printStackTrace();
        }
    }

    private boolean shouldSwitchToOracle() {
        // Your logic to determine when to switch to Oracle
        // Example: Check data in MS SQL Server and decide to switch based on certain conditions
        return false; // Change this condition based on your requirements
    }


	public static String convertToIndianCurrency(String num) {
		BigDecimal bd = new BigDecimal(num);
		long number = bd.longValue();
		long no = bd.longValue();
		int decimal = (int) (bd.remainder(BigDecimal.ONE).doubleValue() * 100);
		int digits_length = String.valueOf(no).length();
		int i = 0;
		ArrayList<String> str = new ArrayList<>();
		HashMap<Integer, String> words = new HashMap<>();
		words.put(0, "");
		words.put(1, "One");
		words.put(2, "Two");
		words.put(3, "Three");
		words.put(4, "Four");
		words.put(5, "Five");
		words.put(6, "Six");
		words.put(7, "Seven");
		words.put(8, "Eight");
		words.put(9, "Nine");
		words.put(10, "Ten");
		words.put(11, "Eleven");
		words.put(12, "Twelve");
		words.put(13, "Thirteen");
		words.put(14, "Fourteen");
		words.put(15, "Fifteen");
		words.put(16, "Sixteen");
		words.put(17, "Seventeen");
		words.put(18, "Eighteen");
		words.put(19, "Nineteen");
		words.put(20, "Twenty");
		words.put(30, "Thirty");
		words.put(40, "Forty");
		words.put(50, "Fifty");
		words.put(60, "Sixty");
		words.put(70, "Seventy");
		words.put(80, "Eighty");
		words.put(90, "Ninety");
		String digits[] = { "", "Hundred", "Thousand", "Lakh", "Crore" };
		while (i < digits_length) {
			int divider = (i == 2) ? 10 : 100;
			number = no % divider;
			no = no / divider;
			i += divider == 10 ? 1 : 2;
			if (number > 0) {
				int counter = str.size();
				String plural = (counter > 0 && number > 9) ? "s" : "";
				String tmp = (number < 21) ? words.get(Integer.valueOf((int) number)) + " " + digits[counter] + plural
						: words.get(Integer.valueOf((int) Math.floor(number / 10) * 10)) + " "
								+ words.get(Integer.valueOf((int) (number % 10))) + " " + digits[counter] + plural;
				str.add(tmp);
			} else {
				str.add("");
			}
		}

		Collections.reverse(str);
		String Rupees = String.join(" ", str).trim();

		String paise = (decimal) > 0
				? " And Paise " + words.get(Integer.valueOf((int) (decimal - decimal % 10))) + " "
						+ words.get(Integer.valueOf((int) (decimal % 10)))
				: "";
		return "Rupees " + Rupees + paise + " Only";
	}

	@Data
	@NoArgsConstructor
	public static class CashHandlingChargesModel {

		private int id;

		private int fromReceiptAmt;

		private int toReceiptAmt;

		private int cashHandlingCharges;

		private String denomination;

		private String lastUpdatedBy;

	}

	@Data
	@NoArgsConstructor
	public static class PrepaymentChargesModel {

		private int id;

		private String product;

		private String rateType;

		private BigDecimal rate;

		private String customerType;

		private String prepaymentReason;

	}

	private Date getDate(Date date) {

		Calendar calendar = Calendar.getInstance();

		calendar.setTime(date);

		calendar.set(Calendar.HOUR_OF_DAY, 0);

		calendar.set(Calendar.SECOND, 0);

		calendar.set(Calendar.MINUTE, 0);

		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTime();

	}

	@Data
	@NoArgsConstructor
	public static class Amort {

		private String applicationNum;

		private Date dueStartDate;

		private Date dueEndDate;

		private Date dueDate;

		private int noOfDays;

		private double installmentToPay;

		private double emiReceivable;

		private double emiInterest;

		private double emiPrincipal;

		private double emiReceipt;

		private double EmiInterestReceipt;

		private double EmiPrincipalReceipt;

		private double emiDue;

		private double EmiInterestDue;

		private double EmiPrincipalDue;

		private double emiOpOs;

		private double emiCloseOs;

		private double openingPrincipal;

		private double disbAmt;

		private double prepayAmt;

		private double closingPrincipal;

		private double roi;

		private double emiPenalOpOs;

		private double emiPenal;

		private double emiPenalReceipt;

		private double emiPenalDue;

		private double emiPenalClOs;

		private double noOfMonthsOutstanding;

		private Date yearMonth;

		private double intrestAccNotDue;

		private int dpdDays;

		private int isBroken;

	}

	@Data
	@NoArgsConstructor
	public static class ParameterMaintananceResponse {

		private int paramId;

		private String paramName;

		private String paramValue;

		private String module;

		private String paramDataType;

		private String paramEffStartDate;

		private String paramEffEndDate;

		private String branch;

	}


}
