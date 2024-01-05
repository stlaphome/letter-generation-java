package com.letter.report.dynamicReportTemplate.service;

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
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.letter.report.dynamicDataSource.service.DynamicDataSourceService;
import com.letter.report.dynamicReportTemplate.letterModel.Boundries;
import com.letter.report.dynamicReportTemplate.letterModel.BranchAddress;
import com.letter.report.dynamicReportTemplate.letterModel.CashHandlingChargesModel;
import com.letter.report.dynamicReportTemplate.letterModel.CustomerAddress;
import com.letter.report.dynamicReportTemplate.letterModel.DynamicTemplateModel;
import com.letter.report.dynamicReportTemplate.letterModel.GenerateTemplateModel;
import com.letter.report.dynamicReportTemplate.letterModel.LetterReportModel;
import com.letter.report.dynamicReportTemplate.letterModel.Measurement;
import com.letter.report.dynamicReportTemplate.letterModel.MemorandumHeader;
import com.letter.report.dynamicReportTemplate.letterModel.PropertyAddress;
import com.letter.report.dynamicReportTemplate.letterModel.PropertyDetailModel;
import com.letter.report.dynamicReportTemplate.letterModel.PropertyNumberModel;
import com.letter.report.dynamicReportTemplate.letterModel.ScheduleA;
import com.letter.report.dynamicReportTemplate.letterModel.ScheduleB;
import com.letter.report.dynamicReportTemplate.letterModel.TitleHolderDetail;
import com.letter.report.dynamicReportTemplate.model.DynamicReportContainer;
import com.letter.report.dynamicReportTemplate.model.DynamicTemplate;
import com.letter.report.dynamicReportTemplate.model.LetterProduct;
import com.letter.report.dynamicReportTemplate.repo.DynamicReportContainerRepo;
import com.letter.report.dynamicReportTemplate.repo.DynamicTemplateRepo;
import com.letter.report.dynamicReportTemplate.repo.LetterProductRepo;

import freemarker.template.Configuration;
import lombok.Data;
import lombok.NoArgsConstructor;

@Service
public class DynamicTemplateService {

	@Autowired
	DynamicTemplateRepo dynamicTemplateRepo;


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
	DataSource dataSource;

	@Autowired
	private WebClient webClient;

	@Autowired
	private  DynamicDataSourceService dynamicDataSourceService;


	private final ObjectMapper objectMapper = new ObjectMapper();

	private static Logger logger = LoggerFactory.getLogger(DynamicTemplateService.class);

	private int serialNo = 1;


	@Value("${stlap.server.url}")
	private String stlapServerUrl;

	@Value("${mail.server.url}")
	private String mailServerUrl;

	public ResponseEntity<String> saveTemplate(DynamicTemplateModel dynamicTemplateModel) {
		String errorContent = validateEditorContent(dynamicTemplateModel.getContent(), returnVariablesList());
		List<LetterProduct> productList = letterProductRepo.findByProductCode(dynamicTemplateModel.getProductCode());
		Optional<LetterProduct> productData = productList.stream().filter(pr->(Objects.isNull(pr.getLetterName()) || pr.getLetterName().equals(dynamicTemplateModel.getTemplateName()))).findFirst();
		LetterProduct product = new LetterProduct();

		if (Objects.isNull(errorContent)) {
			DynamicTemplate dynamicTemplate = new DynamicTemplate();
			Blob blob;
			try {
				blob = (Blob) new SerialBlob(dynamicTemplateModel.getContent().getBytes());
				dynamicTemplate.setContent(blob);
			} catch (Exception e) {
				e.printStackTrace();
			}
			dynamicTemplate.setProductCode(dynamicTemplateModel.getProductCode());
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
			if(productData.isPresent()) {
				product = productData.get();
			}
			product.setProductCode(dynamicTemplateModel.getProductCode());
			product.setLetterName(dynamicTemplateModel.getTemplateName());
			switch(product.getProductCode()) {
			case "STLAP":
				product.setDataBase("MSSQL");
				break;
			case "HOMEFIN":
				product.setDataBase("ORACLE");
				break;
			default:
				break;
			}
			letterProductRepo.save(product);

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
		Optional<DynamicTemplate> optionalDynamicTemplate = dynamicTemplateRepo.findByProductCodeAndTemplateNameAndTemplateKey(dynamicTemplateModel.getProductCode(),
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
		List<Map> templateKeyList = new ArrayList<>();
			List<DynamicTemplate> dynamicTemplateList = dynamicTemplateRepo.findByProductCodeAndTemplateName(dataMap.get("productCode"), dataMap.get("templateName"));
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

	public ResponseEntity<List<String>> getTemplateNameList(String productType) {
		List<LetterProduct> letterproductAllData = letterProductRepo.findByProductCode(productType);
		Set<String> templateNameSet = new HashSet<>();
		List<String> templateNameList = new ArrayList<>();
		letterproductAllData.stream().forEach(item -> {
			if(Objects.nonNull(item.getLetterName())) {
				templateNameSet.add(item.getLetterName());
			}
		});
		templateNameList.addAll(templateNameSet);
		return ResponseEntity.ok(templateNameList);
	}

	

	public void getFeeDataForLetterGeneration(Map<String, Object> dataMap, LetterReportModel letterModel) {
		logger.info("getFeeDataForLetterGeneration method started");
		String applicationNumber = getString(String.valueOf(dataMap.get("applicationNum")));
		List<MemorandumHeader> memorandumSavedData = new ArrayList<>();
		try (Connection connection = dataSource.getConnection()) {
			String query = "SELECT memo_code_desc,txn_indicator,txn_amt FROM ST_TB_LMS_MEMO_HDR where application_num=?";
			try (PreparedStatement statement = connection.prepareStatement(query)) {
				statement.setString(1, applicationNumber);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						logger.info("getFeeDataForLetterGeneration query started"+memorandumSavedData);
						MemorandumHeader memoData = new MemorandumHeader();
						memoData.setMemoCode(resultSet.getString(1));
						memoData.setTxnIndicator(resultSet.getString(2));
						memoData.setTxnAmt(resultSet.getInt(3));
						memorandumSavedData.add(memoData);
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		AtomicInteger processingFee = new AtomicInteger(0);
		AtomicInteger documentationCharges = new AtomicInteger(0);
		AtomicInteger lifeInsuranceChrages = new AtomicInteger(0);
		memorandumSavedData.stream().forEach(action -> {
			logger.info("memorandumSavedData loop "+action);
			if (action.getMemoCode().equalsIgnoreCase("PROCESSING FEE")) {
				if (action.getTxnIndicator().equals("accrual")) {
					logger.info("processingFee loop "+action);
					processingFee.set(processingFee.get() + action.getTxnAmt());
				} else {
					logger.info("processingFee loop "+action);
					processingFee.set(processingFee.get() - action.getTxnAmt());
				}
			} 
		});
		logger.info("processingFee loop completed"+String.valueOf(processingFee.get()));
		memorandumSavedData.stream().forEach(action -> {
			if (action.getMemoCode().equalsIgnoreCase("DOCUMENTATION CHARGES")
					|| action.getMemoCode().equalsIgnoreCase("DOCUMENTATION FEE")) {
				if (action.getTxnIndicator().equals("accrual")) {
					documentationCharges.set(documentationCharges.get() + action.getTxnAmt());
				} else {
					documentationCharges.set(documentationCharges.get() - action.getTxnAmt());
				}
			}
		});
		logger.info("documentationCharges loop completed"+String.valueOf(documentationCharges.get()));
		memorandumSavedData.stream().forEach(action -> {
			if (action.getMemoCode().equalsIgnoreCase("LIFE INSURANCE")
					|| action.getMemoCode().equalsIgnoreCase("PROPERTY INSURANCE")) {
				if (action.getTxnIndicator().equals("accrual")) {
					lifeInsuranceChrages.set(lifeInsuranceChrages.get() + action.getTxnAmt());
				} else {
					lifeInsuranceChrages.set(lifeInsuranceChrages.get() - action.getTxnAmt());
				}
			}
		});
		logger.info("lifeInsuranceChrages loop completed"+String.valueOf(lifeInsuranceChrages.get()));
		letterModel.setDocumentationCharges(String.valueOf(documentationCharges.get()));
		letterModel.setProcessingFee(String.valueOf(processingFee.get()));
		letterModel.setLifeInsurance(String.valueOf(lifeInsuranceChrages.get()));

	}

	private PrepaymentChargesModel getDataFromPrepaymentCharges(Map<String, Object> dataMap) {
		PrepaymentChargesModel chargesResponse = new PrepaymentChargesModel();
		AtomicInteger rowId = new AtomicInteger(0);
		try (Connection connection = dataSource.getConnection()) {
			String query = "SELECT product,rate_type,rate,customer_type,prepayment_reason FROM ST_TB_LMS_PREPAYMENT_CHARGE_MSTR where prepayment_reason=? AND effective_date=(SELECT MAX(effective_date) FROM ST_TB_LMS_PREPAYMENT_CHARGE_MSTR where prepayment_reason=?)";

			try (PreparedStatement statement = connection.prepareStatement(query)) {
				statement.setString(1, String.valueOf(dataMap.get("prepayment_reason")));
				statement.setString(2, String.valueOf(dataMap.get("prepayment_reason")));
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						chargesResponse.setId(rowId.get());
						chargesResponse.setProduct(resultSet.getString(1));
						chargesResponse.setRateType(resultSet.getString(2));
						chargesResponse.setRate(resultSet.getBigDecimal(3));
						chargesResponse.setCustomerType(resultSet.getString(4));
						chargesResponse.setPrepaymentReason(resultSet.getString(5));
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return chargesResponse;
	}

	private String getString(String name) {
		return Objects.nonNull(name) ? name : "";
	}
	private String getStringFromObject(Object name) {
		return Objects.nonNull(name) ? String.valueOf(name) : "";
	}

	public String getCustomerAddress(int customerId, String applicantName) {
		logger.info("getCustomerAddress method completed"+applicantName);
		StringBuilder addressBuilder = new StringBuilder();

		try (Connection connection = dataSource.getConnection()) {
			String query = "SELECT * FROM st_tb_los_customer_information WHERE customer_id = " + customerId;

			try (PreparedStatement statement = connection.prepareStatement(query)) {
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						logger.info(" getCustomerAddress query method executed");
						String customerName = getStringFromObject(resultSet.getString("sur_name")) + " "
								+ getStringFromObject(resultSet.getString("first_name")) + " "
								+ getStringFromObject(resultSet.getString("middle_name"));
						logger.info("getCustomerAddress name fetched"+customerName);	
						addressBuilder.append(resultSet.getString("flat_door_building_block")).append(".");
						addressBuilder.append(resultSet.getString("road_street")).append(", ");
						addressBuilder.append(resultSet.getString("area_locality")).append(", ");
						addressBuilder.append(resultSet.getString("landmark")).append(", ");
						addressBuilder.append(resultSet.getString("city_town")).append(", ");
						addressBuilder.append(resultSet.getString("state")).append(" - ");
						addressBuilder.append(resultSet.getString("pincode"));
						logger.info("addressBuilder name fetched"+addressBuilder.toString());	
						break;
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		logger.info("getCustomerAddress name completed"+addressBuilder.toString());
		return addressBuilder.toString();
	}

	private String getExpandedAddress(String[] addressContent, Object customerName) {
		String newAddress = String.valueOf(customerName);
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
		return Arrays.asList("//~~Branch_Address~~//", "//~~Date~~//", "//~~To_Address~~//",
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
				"//~~MOTD_Title_Execution~~//", "//~~MOTD_Run_Day~~//", "//~~MOTD_Run_Month_Year~~//","//~~MOTD_Title~~//",

				"//~~MOTD_Title~~//","//~~MOTD_Title_Holder_Name~~//", "//~~MOTD_Title_Holder_Aadhaar~~//", "//~~MOTD_Title_Holder_Age~~//",
				"//~~MOTD_Title_Holder_Guardian~~//", "//~~MOTD_Title_Holder_Address~~//","//~~MOTD_OTD_Number~~//",
				"//~~MOTD_Title_1~~//","//~~MOTD_Title_Holder_Name_1~~//", "//~~MOTD_Title_Holder_Aadhaar_1~~//", "//~~MOTD_Title_Holder_Age_1~~//",
				"//~~MOTD_Title_Holder_Guardian_1~~//", "//~~MOTD_Title_Holder_Address_1~~//","//~~MOTD_OTD_Number_1~~//","//~~MOTD_Loan_details_table~~//",
				"//~~Schedule_A_Table~~//","//~~MOTD_Registered_Date~~//", "//~~MOTD_Registered_Doc_no~~//", "//~~MOTD_Registered_Office~~//",
				"//~~MOTD_Registered_Sub_Office~~//", "//~~MOTD_Clearance_Date~~//", "//~~MOTD_Favour_Of~~//",
				"//~~MOTD_Sanction_Amount~~//", "//~~MOTD_Sanction_Amount_Words~~//", "//~~MOTD_Mortgage_Type~~//","//~~Schedule_Detail_Table~~//",
				"//~~MOTD_SRO_District~~//","//~~MOTD_SRO~~//", "//~~MOTD_SRO_Place~~//", "//~~MOTD_District~~//", "//~~MOTD_Taluk~~//",
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
				"//~~Loan_Amount~~//","//~~Processing_Fee~~//","//~~Term~~//",
				"//~~Net_Rate~~//","//~~EMI~~//","//~~Account_No~~//","//~~End_Use_of_Loan~~//","//~~Purpose_of_Loan~~//",
				"//~~Header_Company_Name~~//","//~~Current_Date~~//","//~~Branch_Address~~//","//~~To_Address~~//",
				"//~~TelePhone_No~~//","//~~Header_Mail~~//",
				"//~~Header_Branch_Address~~//","//~~Life_Insurance~~//","//~~Admin_Fee~~//","//~~Applicant~~//","//~~Co-Applicant 1~~//","//~~Co-Applicant 2~~//","//~~Moratorium_Period~~//",
				"//~~MOTD_First_Mortage_Title_Holder_Detail~~//","//~~MOTD_First_Mortage_Title_Name_Detail~~//","//~~MOTD_Title_Holder_Detail~~//","//~~MOTD_Date~~//","//~~MOTD_Month_Year~~//"
				,"//~~Schedule_B_Detail~~//","//~~Boundries_Detail~~//","//~~Measurement_Detail~~//",
				"//~~Supplement_MOTD_Title_Holder_Detail~~//"
				);
	}
	
	public ResponseEntity<List<String>> getAllApplicationNumbers(String productCode) {
		String dataBase = "MSSQL";
		List<LetterProduct> letterproductDataList = letterProductRepo.findByProductCode(productCode);
		return fetchApplicationNumber(letterproductDataList);

	}

	private ResponseEntity<List<String>> fetchApplicationNumber(List<LetterProduct> letterproductData) {
		List<String> returnResponseList = new ArrayList<>();
		String dataBase = letterproductData.stream().findFirst().get().getDataBase();
		if(dataBase.equals("ORACLE")) {
			returnResponseList = getOracleApplicationNumber(letterproductData);
		}else {
			returnResponseList = getLosApplicationNumber("Sanctioned");
		}
		return ResponseEntity.ok(returnResponseList);
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
				.setFontAndSize(pdf.getDefaultFont(), 6)
				.moveText(300, 20) // Adjust the coordinates for the position of the page number
				.showText("" + ((PdfDocumentEvent) event).getDocument().getPageNumber(((PdfDocumentEvent) event).getPage()))
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

	public ResponseEntity<Map<String, Object>> generateLetter(GenerateTemplateModel model) throws SQLException {
		String dataBase = "MSSQL";
		LetterProduct letterProduct = letterProductRepo.findByProductCodeAndLetterName(model.getProductCode(),model.getTemplateName());
		List<DynamicTemplate> dynamicTemplateList = dynamicTemplateRepo.findByProductCodeAndTemplateNameAndActive(model.getProductCode(),model.getTemplateName(), true);
		DynamicTemplate dynamicTemplate;
		if(!dynamicTemplateList.isEmpty()) {
			dynamicTemplate = dynamicTemplateList.get(0);
			if(Objects.nonNull(letterProduct)) {
				dataBase = letterProduct.getDataBase();
				return ResponseEntity.ok(generateLetterForApplicationNumber(model,letterProduct,dynamicTemplate));
			}



		}

		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("FilesList", new ArrayList());
		resultMap.put("ApplicationList", new ArrayList());
		resultMap.put("Status", "Error Occured Letter Not Generated.");
		return ResponseEntity.ok(resultMap);
	}

	private Map<String, Object> generateLetterForApplicationNumber(GenerateTemplateModel model, LetterProduct letterProduct, DynamicTemplate dynamicTemplate) throws SQLException {
		String productData = letterProduct.getProductData();
		Date date = new Date();
		Map<String, String> filesMap = new HashMap<>();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
		Blob blob = dynamicTemplate.getContent();
		byte[] bdata;
		bdata = blob.getBytes(1, (int) blob.length());
		String htmlContent = new String(bdata);
		Map<String, Object> resultMap = new HashMap<>();
		List<String> applicationList = new ArrayList<>();
		try {
			List<LetterReportModel> sanctionModelList = objectMapper.readValue(productData, new TypeReference<List<LetterReportModel>>() {}); 
			if(sanctionModelList.isEmpty()) {
				resultMap.put("FilesList", filesMap);
				resultMap.put("ApplicationList", applicationList);
				resultMap.put("Status", "Error Occured. Letter Not Generated.");
				return resultMap;
			}
			sanctionModelList.stream().forEach(sanctionModel->{
				Map<String, Object> variableMap = new HashMap<>();
				String fileName = (dynamicTemplate.getTemplateName()).concat("_").concat(sanctionModel.getApplicationNumber())
						.concat("_").concat(dateFormat.format(date)).concat(".pdf");
				//			File file = new File("./downloads/letter_generation/" + fileName);
				//filesMap.put("applicationNum", model.getApplicationNumber());
				filesMap.put(sanctionModel.getApplicationNumber(), fileName);
				applicationList.add(sanctionModel.getApplicationNumber());
				switch (letterProduct.getDataBase()) {
				case "ORACLE":
					variableMap = getDataForOracleSanctionLetter(model,sanctionModel);
					break;
				case "MSSQL":
					variableMap = getDataForMITC(model,sanctionModel);
					break;
				default:
					break;
				}
				String outputVariable = replaceVariable(htmlContent,variableMap);
				saveDataToContainer(outputVariable,fileName);
			});
		} catch (Exception e) {
			resultMap.put("FilesList", filesMap);
			resultMap.put("ApplicationList", applicationList);
			resultMap.put("Status", "Error Occured. Letter Not Generated.");
			e.printStackTrace();
		}



		resultMap.put("FilesList", filesMap);
		resultMap.put("ApplicationList", applicationList);
		resultMap.put("Status", "Letter Generated Successfully");
		return resultMap;
	}

	private Map<String, Object> getDataForMITC(GenerateTemplateModel model, LetterReportModel sanctionModel) {
		Map<String, Object> variablesValueMap = new HashMap<String, Object>();
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		YearMonth yearMonth = YearMonth.now();
		String formattedDate = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
		String toNewAddress =   getExpandedAddress(sanctionModel.getCustomerAddress().split(","),sanctionModel.getCustomerName());
		variablesValueMap.put("~~Branch_Address~~", sanctionModel.getBranchAddress());
		variablesValueMap.put("~~Date~~", formatter.format(date));
		variablesValueMap.put("~~To_Address~~", toNewAddress);
		variablesValueMap.put("~~Application_Number~~", sanctionModel.getApplicationNumber());
		variablesValueMap.put("~~Loan_Amount~~", sanctionModel.getAmountFinanced());
		variablesValueMap.put("~~Loan_Amount_In_Words~~", convertToIndianCurrency(String.valueOf(sanctionModel.getAmountFinanced())));
		variablesValueMap.put("~~Product~~", "Non-Housing Loans");
		variablesValueMap.put("~~Purpose_of_Loan~~", nullCheckStringField(sanctionModel.getPurposeOfLoan()));
		variablesValueMap.put("~~Term~~", String.valueOf(sanctionModel.getTerm()));
		variablesValueMap.put("~~ROI~~", String.valueOf(sanctionModel.getNetRate()));
		variablesValueMap.put("~~EMI~~", sanctionModel.getEmiAmount());
		variablesValueMap.put("~~Upfront_Processing_Fee~~", sanctionModel.getProcessingFee()); 
		variablesValueMap.put("~~Balance_Payable~~", sanctionModel.getBalancePayable());
		variablesValueMap.put("~~Documentation_Charges~~", String.valueOf(sanctionModel.getDocumentationCharges()));
		variablesValueMap.put("~~CERSAI_Charges~~", "100");
		variablesValueMap.put("~~Appraisal_Charges~~", "Not Applicable"); //
		variablesValueMap.put("~~Switch_Fee~~", "0");  //Not Applicable
		variablesValueMap.put("~~Retrieval_Charges~~", "Nil");
		variablesValueMap.put("~~Conversion_Charges~~", "Not Applicable");
		variablesValueMap.put("~~Cheque_Return_Charges~~", sanctionModel.getChequeReturnCharges());
		variablesValueMap.put("~~GST_Tamilnadu~~", "1200");  
		variablesValueMap.put("~~GST_Andra~~", "1500"); 
		variablesValueMap.put("~~GST_Karnataka~~","1550"); 
		variablesValueMap.put("~~GST_Others~~", "2500"); 
		variablesValueMap.put("~~Repricing_Fee~~",  "0"); //Not Applicable
		variablesValueMap.put("~~CA_Certification_Fee~~", "10000"); //
		variablesValueMap.put("~~Outstation_Cheque_Charges~~", "4"); 
		variablesValueMap.put("~~Outstation_Cheque_Charges_Total~~","1000"); 
		variablesValueMap.put("~~PDC_Charges~~", "300"); 
		variablesValueMap.put("~~Swapping_Charges~~","500"); 
		variablesValueMap.put("~~Travelling_Expense~~", "200"); 
		variablesValueMap.put("~~Bureau_Charges_Individual_Customer~~","49"); 
		variablesValueMap.put("~~Bureau_Charges_Non_Individual_Customer~~","335"); 
		variablesValueMap.put("~~Prepayment_Charges~~", sanctionModel.getPrePaymentCharges());
		variablesValueMap.put("~~Penal_Interest~~","0"); //Not Applicable
		variablesValueMap.put("~~Cheque_Dishonour_Charges~~", "0"); //Not Applicable
		variablesValueMap.put("~~Life_Insurance~~", nullCheckStringField(sanctionModel.getLifeInsurance())); 
		variablesValueMap.put("~~Moratorium_Period~~", sanctionModel.getMoratoriumPeriod()); 

		List<CashHandlingChargesModel> cashHandlingChargesList = sanctionModel.getCashHandlingCharges();
		StringBuilder cashHandlingChargesTables = new StringBuilder(
				"<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 55.25pt; border-collapse: collapse; mso-table-layout-alt: fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\"><tbody><tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\"><td style=\\\"width: 150pt; border: 1pt solid black; background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Amount of Remittance</td><td style=\\\"width: 150pt; border-top: 1pt solid black; border-right: 1pt solid black; border-bottom: 1pt solid black; border-image: initial; border-left: none; background: rgb(191, 204, 218); padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Applicable Charges</td></tr><tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 150.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> Upto Rs.2000/-</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> NIL</td></tr>");
		if(Objects.nonNull(cashHandlingChargesList)) {
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
			variablesValueMap.put("~~Cash_Handling_Charges_Table~~", cashHandlingChargesTables.toString());
		}

		return variablesValueMap;
	}

	private void saveDataToContainer(String outputValue, String fileName) {
		DynamicReportContainer reportContainer = new DynamicReportContainer();
		try {
			Blob blob = (Blob) new SerialBlob(outputValue.getBytes());
			reportContainer.setReportFile(blob);
			reportContainer.setReportFileName(fileName);
			SecureRandom secureRandom;
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
				.setFontAndSize(pdf.getDefaultFont(), 6)
				.moveText(300, 20) // Adjust the coordinates for the position of the page number
				.showText("" + ((PdfDocumentEvent) event).getDocument().getPageNumber(((PdfDocumentEvent) event).getPage()))
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

	public Map<String, Object> getDataForOracleSanctionLetter(GenerateTemplateModel model, LetterReportModel sanctionModel) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		formatter.format(date);
		Map<String, Object> variablesValueMap = new HashMap<String, Object>();
		variablesValueMap.put("~~Application_Number~~", nullCheckStringField(sanctionModel.getApplicationNumber()));
		variablesValueMap.put("~~Header_Company_Name~~", nullCheckStringField(sanctionModel.getCompanyName()));
		variablesValueMap.put("~~Header_Branch_Address~~", nullCheckStringField(sanctionModel.getBranchAddress().toString()));
		variablesValueMap.put("~~TelePhone_No~~", nullCheckStringField(sanctionModel.getTelePhoneNumber()));
		variablesValueMap.put("~~Header_Mail~~", nullCheckStringField(sanctionModel.getBranchMailId()));
		variablesValueMap.put("~~Current_Date~~", nullCheckStringField(sanctionModel.getCurrentDate()));
		variablesValueMap.put("~~Date~~", nullCheckStringField(sanctionModel.getCurrentDate()));
		variablesValueMap.put("~~Branch_Address~~", nullCheckStringField(sanctionModel.getBranchAddress().toString()));
		variablesValueMap.put("~~To_Address~~", nullCheckStringField(sanctionModel.getCustomerAddress()));
		variablesValueMap.put("~~Loan_Amount~~", (sanctionModel.getAmountFinanced()));
		variablesValueMap.put("~~Loan_Amount_In_Words~~", convertToIndianCurrency(String.valueOf(sanctionModel.getAmountFinanced())));
		variablesValueMap.put("~~Upfront_Processing_Fee~~", nullCheckStringField(sanctionModel.getProcessingFee()));
		variablesValueMap.put("~~Term~~", (sanctionModel.getTerm()));
		variablesValueMap.put("~~Moratorium_Period~~", (sanctionModel.getMoratoriumPeriod()));
		variablesValueMap.put("~~Net_Rate~~", (sanctionModel.getNetRate()));
		variablesValueMap.put("~~ROI~~", (sanctionModel.getNetRate()));
		variablesValueMap.put("~~EMI~~", sanctionModel.getEmiAmount());
		variablesValueMap.put("~~Account_No~~", nullCheckStringField(sanctionModel.getAccountNo()));
		variablesValueMap.put("~~Purpose_of_Loan~~", nullCheckStringField(sanctionModel.getPurposeOfLoan()));
		variablesValueMap.put("~~End_Use_of_Loan~~", nullCheckStringField(sanctionModel.getEndUseOfLoan()));
		variablesValueMap.put("~~Applicant~~", nullCheckStringField(sanctionModel.getApplicant()));
		variablesValueMap.put("~~Admin_Fee~~", nullCheckStringField(null));
		variablesValueMap.put("~~Co-Applicant 1~~", nullCheckStringField(sanctionModel.getCoApplicant1()));
		variablesValueMap.put("~~Co-Applicant 2~~", nullCheckStringField(sanctionModel.getCoApplicant2()));
		variablesValueMap.put("~~Product~~", "Non-Housing Loans");
		variablesValueMap.put("~~Balance_Payable~~", sanctionModel.getBalancePayable());
		variablesValueMap.put("~~Documentation_Charges~~", String.valueOf(sanctionModel.getDocumentationCharges()));
		variablesValueMap.put("~~CERSAI_Charges~~", "100");
		variablesValueMap.put("~~Appraisal_Charges~~", "Not Applicable"); //
		variablesValueMap.put("~~Switch_Fee~~", "0");  //Not Applicable
		variablesValueMap.put("~~Retrieval_Charges~~", "Nil");
		variablesValueMap.put("~~Conversion_Charges~~", "Not Applicable");
		variablesValueMap.put("~~Cheque_Return_Charges~~", sanctionModel.getChequeReturnCharges());
		variablesValueMap.put("~~GST_Tamilnadu~~", "1200");  
		variablesValueMap.put("~~GST_Andra~~", "1500"); 
		variablesValueMap.put("~~GST_Karnataka~~","1550"); 
		variablesValueMap.put("~~GST_Others~~", "2500"); 
		variablesValueMap.put("~~Repricing_Fee~~",  "0"); //Not Applicable
		variablesValueMap.put("~~CA_Certification_Fee~~", "10000"); //
		variablesValueMap.put("~~Outstation_Cheque_Charges~~", "4"); 
		variablesValueMap.put("~~Outstation_Cheque_Charges_Total~~","1000"); 
		variablesValueMap.put("~~PDC_Charges~~", "300"); 
		variablesValueMap.put("~~Swapping_Charges~~","500"); 
		variablesValueMap.put("~~Travelling_Expense~~", "200"); 
		variablesValueMap.put("~~Bureau_Charges_Individual_Customer~~","49"); 
		variablesValueMap.put("~~Bureau_Charges_Non_Individual_Customer~~","335"); 
		variablesValueMap.put("~~Prepayment_Charges~~", sanctionModel.getPrePaymentCharges());
		variablesValueMap.put("~~Penal_Interest~~","0"); //Not Applicable
		variablesValueMap.put("~~Cheque_Dishonour_Charges~~", "0"); //Not Applicable
		variablesValueMap.put("~~Life_Insurance~~", nullCheckStringField(sanctionModel.getLifeInsurance())); 
		variablesValueMap.put("~~Moratorium_Period~~", sanctionModel.getMoratoriumPeriod()); 
		variablesValueMap.put("~~GST_Andra~~", "1500"); 
		variablesValueMap.put("~~GST_Karnataka~~","1550"); 
		variablesValueMap.put("~~GST_Tamilnadu~~", "1200");  
		variablesValueMap.put("~~GST_Others~~", "2500"); 
		getDataForMOTD(variablesValueMap,sanctionModel);
		return variablesValueMap;
	}

	private void getDataForMOTD(Map<String, Object> variablesValueMap, LetterReportModel sanctionModel) {
		PropertyDetailModel propertyDetailModel = sanctionModel.getPropertyDetailModel();
		Set<String> firstMortagetitleHolderDetailList = new LinkedHashSet<>();
		Set<String> firstMortagetitleNameDetailList = new LinkedHashSet<>();
		Set<String> motdTitleHolderList = new LinkedHashSet<>();
		Set<String> supplementMotdTitleHolderList = new LinkedHashSet<>();
		Set<String> scheduleATableList = new LinkedHashSet<>();
		Set<String> scheduleBList = new LinkedHashSet<>();
		List<String> boundriesList = new LinkedList<>();
		List<String> measurementList = new LinkedList<>();
		String space5 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		String space10 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		String space20 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		String space25 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		String space30 = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
	
		if(Objects.nonNull(propertyDetailModel)) {
			Set<TitleHolderDetail> titleHolderDetailList = propertyDetailModel.getTitleHolderDetailList();
			Map<String, LinkedHashSet<ScheduleA>> scheduleAListMap = propertyDetailModel.getScheduleListMap();
			Map<String, ScheduleB> scheduleBMap = propertyDetailModel.getScheduleBListMap();
			Map<String, Boundries> boundriesMap = propertyDetailModel.getBoundriesListMap();
			Map<String, Measurement> measurementMap = propertyDetailModel.getMeasurementListMap();
			serialNo = 1;
			if(Objects.nonNull(titleHolderDetailList)) {
				titleHolderDetailList.stream().forEach(titleHolderDetail->{
					if(Objects.isNull(titleHolderDetail)) {
						return;
					}
					//smtitleholder
					StringBuilder firstMortagetitleHolderDetail =new StringBuilder(getString(titleHolderDetail.getTitle()) +"."+getString(titleHolderDetail.getTitleHolderName())+", Aadhaar No. "+
							getString(titleHolderDetail.getTitleAadharNo())+" aged about "+getStringFromObject(titleHolderDetail.getAge())+" years,"
							+ " S/o.W/o.Mr/s "+getString(titleHolderDetail.getTitleHolderGuardianName())
							+", residing at "+"<br>"+getString(titleHolderDetail.getTitleHolderAddress())+"<br>"
							+"referred to as the MORTGAGORS ”, the PARTY OF THE FIRST PART. "+"<br>"
							+ "(Which expression shall unless excluded by or repugnant to the context be deemed "+"<br>"
							+ "to include his / her / their successor and assigns)."
							+"<br>"+"<br>");

					firstMortagetitleHolderDetailList.add(firstMortagetitleHolderDetail.toString());
					//smotd name
					StringBuilder firstMortagetitleNameDetail =new StringBuilder("WHEREAS the first mortgagor of "+getStringFromObject(titleHolderDetail.getTitle()) +"."+getStringFromObject(titleHolderDetail.getTitleHolderName())
					+"herein is the sole and absolute owner of "+"<br>"+"herein is the sole and absolute owner of the property by the following document "
					+ getStringFromObject(titleHolderDetail.getOtdNumber())+" on the file of "+"("+sanctionModel.getSRO()+" ). "
					+"<br>"+"<br>");
					firstMortagetitleNameDetailList.add(firstMortagetitleNameDetail.toString());
					
					//motd tileholder
					StringBuilder motdTitleHolderBuilder =new StringBuilder(getString(titleHolderDetail.getTitle()) +"."+getString(titleHolderDetail.getTitleHolderName())+", Aadhaar No. "+
							getString(titleHolderDetail.getTitleAadharNo())+" aged about "+getStringFromObject(titleHolderDetail.getAge())+" years,"
							+ " S/o.W/o.Mr/s "+getString(titleHolderDetail.getTitleHolderGuardianName())
							+", residing at "+"<br>"+getString(titleHolderDetail.getTitleHolderAddress())+"<br>"
							+"referred to as the BORROWER/S”, the PARTY OF THE FIRST PART. "+"<br>"
							+ "(Which expression shall unless excluded by or repugnant to the context be deemed "+"<br>"
							+ "to include his / her / their successor and assigns)."
							+"<br>"+"<br>");

					motdTitleHolderList.add(motdTitleHolderBuilder.toString());
					
					//supplement motd title
					
					int a = serialNo++;
					String titleHolder = a+"."+getString(titleHolderDetail.getTitle()) +"."+getString(titleHolderDetail.getTitleHolderName())+" ,S/o.W/o.Mr/s of"+
							getString(titleHolderDetail.getTitleHolderGuardianName())+" ,aged about "+getStringFromObject(titleHolderDetail.getAge())+" years,"
							+" residing at "+"<br>"+getString(titleHolderDetail.getTitleHolderAddress());
					String valueCondition = ""+","+"<br>"+"<br>"+titleHolder;
					StringBuilder supplmentMotdTitleHolderBuilder =new StringBuilder(a!=1?valueCondition:titleHolder);
					
					supplementMotdTitleHolderList.add(supplmentMotdTitleHolderBuilder.toString());
					
					//scheduleA
					if(Objects.nonNull(scheduleAListMap)) {
						Set<ScheduleA> scheduleAList = scheduleAListMap.get(String.valueOf(titleHolderDetail.getPropertyNumber()));
						if(Objects.nonNull(scheduleAList)&&!scheduleAList.isEmpty()) {
							StringBuilder scheduleATable = new StringBuilder("Document details for item No."+titleHolderDetail.getPropertyNumber()+ "of Schedule -A");
							scheduleATable.append("<br>");
							scheduleATable.append("<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 20.25pt; border-collapse: collapse; mso-table-layout-alt: fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\"><tbody><tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\"><td style=\\\"width: 150pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document Name</td><td style=\\\"width: 150pt; border-top: 1pt solid black; border-right: 1pt solid black; border-bottom: 1pt solid black; border-image: initial; border-left: none;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document No</td><td style=\\\\\\\"width: 150.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\\\\\" valign=\\\\\\\"top\\\\\\\" width=\\\\\\\"200\\\\\\\">Document Date</td><td style=\\\\\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\\\\\" valign=\\\\\\\"top\\\\\\\" width=\\\\\\\"200\\\\\\\">Title Holder</td></tr>");
							scheduleAList.stream().forEach(scheduleA -> {
								scheduleATable.append(
										"<tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 250.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"250\\\"> ");
								scheduleATable
								.append(scheduleA.getDocumentName());
								scheduleATable.append(
										"</td><td style=\\\"width: 100.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"100\\\"> ");
								scheduleATable.append(scheduleA.getDocuemntNumber());
								scheduleATable.append(
										"</td><td style=\\\"width: 100.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"100\\\"> ");
								scheduleATable.append(scheduleA.getDocumentDate());
								scheduleATable.append(
										"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"150\\\"> ");
								scheduleATable.append(scheduleA.getTitleHolderName());
								scheduleATable.append("</td></tr>");
							});
							scheduleATable.append("</tbody></table>");
							scheduleATable.append("<br>");
							scheduleATableList.add(scheduleATable.toString());
						}
					}
					//scheduleB
					if(Objects.nonNull(scheduleBMap)) {
						ScheduleB scheduleB = scheduleBMap.get(String.valueOf(titleHolderDetail.getPropertyNumber()));
						if(Objects.nonNull(scheduleB)) {
								PropertyAddress proeprtyAddress = scheduleB.getPropertyAddress();
							StringBuilder scheduleBBuilder = new StringBuilder("Item "+titleHolderDetail.getPropertyNumber()+"<br>"+"<br>");
							scheduleBBuilder.append("SRO District "+space30.concat(space20).concat("&nbsp;&nbsp;").concat(getString(scheduleB.getSroDistrict())));
							scheduleBBuilder.append("<br>");
							scheduleBBuilder.append("SRO "+space30.concat(space30).concat(space5).concat(getString(scheduleB.getSro())));
							scheduleBBuilder.append("<br>");
							scheduleBBuilder.append("Survey No And Addl Survey No "+space20.concat("&nbsp;&nbsp;").concat(getString(scheduleB.getSurveyNo())));
							scheduleBBuilder.append("<br>");
							scheduleBBuilder.append("Plot No "+space30.concat(space25).concat(space5).concat("&nbsp;").concat(getString(scheduleB.getPlotNo())));
							scheduleBBuilder.append("<br>");
							scheduleBBuilder.append("Door No "+space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(scheduleB.getDoorNo())));
							scheduleBBuilder.append("<br>");
							scheduleBBuilder.append("Project Name(If Available) "+space25.concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(""));//projectName
							scheduleBBuilder.append("<br>");
							scheduleBBuilder.append("Flat No (if Available) "+space30.concat(space5).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(""));//flatno
							scheduleBBuilder.append("<br>");
							scheduleBBuilder.append("Floor (if Available) "+space30.concat(space10).concat("&nbsp;&nbsp;").concat(""));//floor
							scheduleBBuilder.append("<br>");
							scheduleBBuilder.append("Block No (if Available) "+space30.concat(space5).concat(""));//block
							scheduleBBuilder.append("<br>");
							if(Objects.nonNull(proeprtyAddress)) {
								scheduleBBuilder.append("Address 1 "+  space30.concat(space25).concat("&nbsp;&nbsp;").concat(getString(proeprtyAddress.getStreet())));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Address 2 "+  space30.concat(space25).concat("&nbsp;&nbsp;").concat(getString(proeprtyAddress.getAddress1())));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Address 3 "+ space30.concat(space25).concat("&nbsp;&nbsp;").concat(getString(proeprtyAddress.getAddress7())));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Pin Code "+  space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(proeprtyAddress.getPinCode())));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Land Extent "+ space25.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(proeprtyAddress.getLandExtent())));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("District "+space30.concat(space30).concat("&nbsp;").concat(getString(scheduleB.getDistrict())));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Taluk "+space30.concat(space30).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(scheduleB.getTaluk())));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Village "+space30.concat(space30).concat("&nbsp;").concat(getString(scheduleB.getVillage())));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("<br>");
							}else {
								scheduleBBuilder.append("Address 1 "+ space30.concat(space25).concat("&nbsp;&nbsp;").concat(""));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Address 2 "+ space30.concat(space25).concat("&nbsp;&nbsp;").concat(""));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Address 3 "+ space30.concat(space25).concat("&nbsp;&nbsp;").concat(""));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Pin Code "+ space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(""));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("Land Extent "+ space25.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(""));
								scheduleBBuilder.append("<br>");
								scheduleBBuilder.append("<br>");
							}
							scheduleBList.add(scheduleBBuilder.toString());
						}
					}

					//boundries
					if(Objects.nonNull(boundriesMap)) {
						Boundries boundries = boundriesMap.get(String.valueOf(titleHolderDetail.getPropertyNumber()));
						if(Objects.nonNull(boundries)) {
							StringBuilder boundroesBuilder = new StringBuilder("Boundaries");
							boundroesBuilder.append("<br>");
							boundroesBuilder.append("North By "+space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(boundries.getNorthBoundry())));
							boundroesBuilder.append("<br>");
							boundroesBuilder.append("South By "+space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(boundries.getSouthBoundry())));
							boundroesBuilder.append("<br>");
							boundroesBuilder.append("East By "+space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(boundries.getEastBoundry())));
							boundroesBuilder.append("<br>");
							boundroesBuilder.append("West By "+space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(boundries.getWestBoundry())));
							boundroesBuilder.append("<br>");
							boundroesBuilder.append("<br>");
							boundriesList.add(boundroesBuilder.toString());
						}
					}

					//measurement
					if(Objects.nonNull(measurementMap)) {
						Measurement measurerments = measurementMap.get(String.valueOf(titleHolderDetail.getPropertyNumber()));
						if(Objects.nonNull(measurerments)) {

							StringBuilder measurementBuilder = new StringBuilder("Measurement");
							measurementBuilder.append("<br>");
							measurementBuilder.append("North By "+space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(measurerments.getNorthMeasurement())));
							measurementBuilder.append("<br>");
							measurementBuilder.append("South By "+space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(measurerments.getSouthMeasurement())));
							measurementBuilder.append("<br>");
							measurementBuilder.append("East By "+space30.concat(space30).concat("&nbsp;").concat(getString(measurerments.getEastMeasurement())));
							measurementBuilder.append("<br>");
							measurementBuilder.append("West By "+space30.concat(space25).concat("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").concat(getString(measurerments.getWestMeasurement())));
							measurementBuilder.append("<br>");
							measurementBuilder.append("<br>");
							measurementList.add(measurementBuilder.toString());
						}
					}
				});
			}
		}
		StringBuilder firstMortagetitleHolderDetailStr = getStringFromSet(firstMortagetitleHolderDetailList);
		StringBuilder firstMortagetitleNameDetailStr = getStringFromSet(firstMortagetitleNameDetailList);
		StringBuilder supplementMotdTitleHolderStr = getStringFromSet(supplementMotdTitleHolderList);
		StringBuilder motdTitleHolderStr = getStringFromSet(motdTitleHolderList);
		StringBuilder scheduleATableStr = getStringFromSet(scheduleATableList);
		StringBuilder scheduleBListStr = getStringFromSet(scheduleBList);
		StringBuilder boundriesStr = getStringFromSet(boundriesList);
		StringBuilder measurementStr = getStringFromSet(measurementList);
		variablesValueMap.put("~~MOTD_First_Mortage_Title_Holder_Detail~~", firstMortagetitleHolderDetailStr.toString());
		variablesValueMap.put("~~MOTD_First_Mortage_Title_Name_Detail~~", firstMortagetitleNameDetailStr.toString());
		variablesValueMap.put("~~Supplement_MOTD_Title_Holder_Detail~~", supplementMotdTitleHolderStr.toString());
		variablesValueMap.put("~~MOTD_Title_Holder_Detail~~", motdTitleHolderStr.toString());
		variablesValueMap.put("~~Schedule_A_Table~~", scheduleATableStr.toString());
		variablesValueMap.put("~~Schedule_B_Detail~~", scheduleBListStr.toString());
		variablesValueMap.put("~~Boundries_Detail~~", boundriesStr.toString());
		variablesValueMap.put("~~Measurement_Detail~~", measurementStr.toString());
		variablesValueMap.put("~~MOTD_SRO~~", sanctionModel.getSRO()); //

		//day and month
		splitDayFromDate(sanctionModel,variablesValueMap);
		
		StringBuilder loanDetailsTable = new StringBuilder(
				"<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 55.25pt; border-collapse: collapse; mso-table-layout-alt: fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\"><tbody><tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\"><td style=\\\"width: 150pt; border: 1pt solid black; padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">File Number</td><td style=\\\"width: 150pt; border-top: 1pt solid black; border-right: 1pt solid black; border-bottom: 1pt solid black; border-image: initial; border-left: none;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Loan Amount (in Rs.)</td><td style=\\\\\\\"width: 150.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\\\\\" valign=\\\\\\\"top\\\\\\\" width=\\\\\\\"200\\\\\\\">Rate (in %)</td><td style=\\\\\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\\\\\" valign=\\\\\\\"top\\\\\\\" width=\\\\\\\"200\\\\\\\">Type</td><td style=\\\\\\\\\\\\\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\\\\\\\\\\\\\" valign=\\\\\\\\\\\\\\\"top\\\\\\\\\\\\\\\" width=\\\\\\\\\\\\\\\"200\\\\\\\\\\\\\\\">Tenor</td></tr>");
		loanDetailsTable.append(
				"<tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 150.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable
		.append(sanctionModel.getContractNumber());
		loanDetailsTable.append(
				"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable.append(sanctionModel.getAmountFinanced());
		loanDetailsTable.append(
				"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable.append(sanctionModel.getNetRate());
		loanDetailsTable.append(
				"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable.append(sanctionModel.getRateTypeString());
		loanDetailsTable.append(
				"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable.append(sanctionModel.getTerm());
		loanDetailsTable.append("</td></tr>");
		loanDetailsTable.append("</tbody></table>");
		variablesValueMap.put("~~MOTD_Loan_details_table~~", loanDetailsTable.toString());

	}
	private void splitDayFromDate(LetterReportModel sanctionModel, Map<String, Object> variablesValueMap) {
		// Parse the input date string
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy");
        String inputDateString = sanctionModel.getCurrentDate();
        Date date;
        try {
			date = inputFormat.parse(inputDateString);

            // Format the date to the desired output format
            SimpleDateFormat outputFormat = new SimpleDateFormat("d', 'MMMM yyyy");
            String outputDateString = outputFormat.format(date);
            String[] dateSplitdata = outputDateString.split(",");
            String dayOrdinal = addOrdinalSuffix(dateSplitdata[0]);
            variablesValueMap.put("~~MOTD_Date~~",dayOrdinal);
            variablesValueMap.put("~~MOTD_Month_Year~~",dateSplitdata[1] );
        } catch (ParseException e) {
            e.printStackTrace();
        }
		
	}
	
	public static String addOrdinalSuffix(String data) {
        int number = Integer.parseInt(data);
		if (number >= 11 && number <= 13) {
            return number + " th";
        }

        switch (number % 10) {
            case 1:
                return number + " st";
            case 2:
                return number + " nd";
            case 3:
                return number + " rd";
            default:
                return number + " th";
        }
    }

	private StringBuilder getStringFromSet(Set<String> firstMortagetitleHolderDetailList) {
		   // Display the list of sets without square brackets
		StringBuilder str = new StringBuilder();
            Iterator<String> iterator = firstMortagetitleHolderDetailList.iterator();
            while (iterator.hasNext()) {
            	str.append(iterator.next());
            }
            return str;
	}
	private StringBuilder getStringFromSet(List<String> firstMortagetitleHolderDetailList) {
		// Display the list of sets without square brackets
		StringBuilder str = new StringBuilder();
		Iterator<String> iterator = firstMortagetitleHolderDetailList.iterator();
		while (iterator.hasNext()) {
			str.append(iterator.next());
		}
		return str;
	}

	public <K, V> int getIndexValue(Map<K, V> input, String targetKey) { 
		int index = 1;
		for (K key : input.keySet()) { 
			if (key.equals(targetKey)) { 
				return index;
			} 
			index++;
		} 
		// Key not found 
		return -1;
	}

	public String nullCheckStringField(String fieldValue) {
		if(Objects.nonNull(fieldValue)) {
			return fieldValue;
		}
		return "Nil";
	}

	public String replaceVariable(String htmlContent, Map<String, Object> valuesMap) {
		StringBuilder returnValue = new StringBuilder(htmlContent);
		valuesMap.entrySet().stream().forEach(value -> {
			String temp = returnValue.toString();
			returnValue.delete(0, returnValue.length());
			returnValue.append(temp.replaceAll(String.valueOf("//" + value.getKey() + "//"),
					Objects.isNull(value.getValue()) ? "" : String.valueOf(value.getValue())));
		});
		//System.out.println(returnValue.toString());
		return returnValue.toString();
	}

	public BranchAddress fetchBranchAddressForMsSQL(String branchCode){
		logger.info("fetchBranchAddressForMsSQL method started");
		BranchAddress branchAddress = new BranchAddress();
		dynamicDataSourceService.switchToOracleDataSource();
		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		try (Connection connection = currentDataSource.getConnection();
				) {
			PreparedStatement preparedStatement1 = connection.prepareStatement("Select A.Obm_Address_Info.Street_L , A.Obm_Address_Info.Column1_L ,"
					+ "                A.Obm_Address_Info.Column2_L, A.Obm_Address_Info.Column3_L,"
					+ "                A.Obm_Address_Info.Column4_L, A.Obm_Address_Info.Column5_L,"
					+ "                A.Obm_Address_Info.Column7_L,"
					+ "                A.Obm_Address_Info.Pin_Zip_Code_L,"
					+ "                Nvl (Trim (A.Obm_Address_Info.Office_Phone_No),"
					+ "                     Trim (A.Obm_Address_Info.Column6_L)"
					+ "                    ),"
					+ "                Trim (A.Obm_Address_Info.Office_Fax_No) From Sa_Organization_Branch_Master A  Where Upper (Obm_Branch_Code) = Upper (?) And Rownum < 2");
			preparedStatement1.setString(1, branchCode);
			ResultSet resultSet1 = preparedStatement1.executeQuery();
			while (resultSet1.next()) {
				logger.info("branchaddress fetch method started");
				branchAddress.setStreet(resultSet1.getString(1));
				branchAddress.setAddress1(resultSet1.getString(2));
				branchAddress.setAddress2(resultSet1.getString(3));
				branchAddress.setAddress3(resultSet1.getString(4));
				branchAddress.setAddress4(resultSet1.getString(5));
				branchAddress.setAddress5(resultSet1.getString(6));
				branchAddress.setAddress7(resultSet1.getString(7));
				branchAddress.setPinCode(resultSet1.getString(8));
				branchAddress.setTelePhoneNumber(resultSet1.getString(9));
				branchAddress.setOfficeFaxNo(resultSet1.getString(10));
			}
			PreparedStatement preparedStatement2 = connection.prepareStatement("Select City_Name"
					+ "   From Hfs_Vw_City"
					+ "   Where City_Code = ?"
					+ "   And State_Record_Id ="
					+ "   (Select Record_Id"
					+ "   From Hfs_Vw_State"
					+ "   Where State_Code = ?"
					+ "   And Country_Code = ?)");
			preparedStatement2.setString(1, branchAddress.getAddress4());
			preparedStatement2.setString(2, branchAddress.getAddress3());
			preparedStatement2.setString(3, branchAddress.getAddress2());
			ResultSet resultSet2 = preparedStatement2.executeQuery();
			while (resultSet2.next()) {
				branchAddress.setDistrictName(resultSet2.getString(1));
			}
			PreparedStatement preparedStatement3 = connection.prepareStatement("Select Location_Name"
					+ "   From Hfs_Vw_Postal_Code"
					+ "   Where Location_Code =?"
					+ "   And City_Code = ?"
					+ "   And State_Code = ?"
					+ "   And Country_Code = ?");
			preparedStatement3.setString(1, branchAddress.getAddress5());
			preparedStatement3.setString(2, branchAddress.getAddress4());
			preparedStatement3.setString(3, branchAddress.getAddress3());
			preparedStatement3.setString(4, branchAddress.getAddress2());
			ResultSet resultSet3 = preparedStatement3.executeQuery();
			while (resultSet3.next()) {
				branchAddress.setLocationName(resultSet3.getString(1));
			}
			logger.info("branchaddress fetch method ended" +branchAddress);
		}catch (Exception e) {
			// TODO: handle exception
		}
		return branchAddress;

	}
	private List<LetterReportModel> fetchDataForOracleDataBase(GenerateTemplateModel model) {

		List<LetterReportModel> letterModelList = new ArrayList<>();
		BranchAddress branchAddress = new BranchAddress();
		dynamicDataSourceService.switchToOracleDataSource();
		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection=null;
		try {
			connection = currentDataSource.getConnection();
			String query1 = "SELECT CONTRACT_NUMBER,CONTRACT_BRANCH,CUSTOMER_CODE,AMOUNT_FINANCED,PURPOSE_OF_LOAN,APPLICATION_NUMBER,APPLICATION_DATE,PREMIUM_AMT,MORATORIUM_PERIOD FROM cc_contract_master where application_number=?";
			String query2 = "SELECT CONTRACT_NUMBER,CONTRACT_BRANCH,CUSTOMER_CODE,AMOUNT_FINANCED,PURPOSE_OF_LOAN,APPLICATION_NUMBER,APPLICATION_DATE,PREMIUM_AMT,MORATORIUM_PERIOD FROM cc_contract_master where application_date=?";
			String sql = "";
			String value = "";
			if(Objects.nonNull(model.getApplicationNumber()) && !(model.getApplicationNumber().isEmpty())) {
				sql = query1;
				value = model.getApplicationNumber();
			}else {
				if(model.getSanctionDate()!=null) {
					sql = query2;
					SimpleDateFormat inputFormater = new SimpleDateFormat("dd/MM/YYYY");
					SimpleDateFormat outputFormater = new SimpleDateFormat("dd-MM-yy");
					String outputDateStr ="";
					try {
						Date dates = inputFormater.parse(model.getSanctionDate());
						outputDateStr = outputFormater.format(dates);		
					} catch (ParseException e) {
						e.printStackTrace();
					}
					value = outputDateStr;
				}
			}
			if(sql.isEmpty()) {
				return letterModelList;
			}

			PreparedStatement preparedStatement = connection.prepareStatement(sql);
			preparedStatement.setString(1, value);
			ResultSet resultSet = preparedStatement.executeQuery();
			if(!resultSet.isBeforeFirst()) {
				return letterModelList;
			}
			while (resultSet.next()) {
				LetterReportModel letterModel = new LetterReportModel();
				letterModel.setContractNumber(resultSet.getString(1));
				letterModel.setBranchCode(resultSet.getString(2));
				letterModel.setCustomerCode(resultSet.getString(3));
				letterModel.setAmountFinanced(convertRoundedValue(resultSet.getString(4)));
				letterModel.setPurposeOfLoan(String.valueOf(resultSet.getInt(5)));
				letterModel.setApplicationNumber(resultSet.getString(6));
				letterModel.setApplicationDate(resultSet.getString(7));
				letterModel.setApplicationDate(resultSet.getString(7));
				letterModel.setLifeInsurance(resultSet.getString(8));
				String period = resultSet.getString(9);
				if(Objects.nonNull(period)) {
					letterModel.setMoratoriumPeriod(Integer.parseInt(period));
				}else {
					letterModel.setMoratoriumPeriod(0);
				}

				PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT NET_RATE, TERM, EMI_AMOUNT,PRINCIPAL_OS,RATE_TYPE FROM Cc_Contract_Rate_Details where contract_number=?  order by occurance_number desc fetch first 1 row only");
				preparedStatement1.setString(1, letterModel.getContractNumber());
				ResultSet resultSet1 = preparedStatement1.executeQuery();
				while (resultSet1.next()) {
					letterModel.setNetRate(convertDecimalValue(resultSet1.getString(1)));
					letterModel.setTerm((resultSet1.getInt(2)));
					letterModel.setEmiAmount(convertRoundedValue(String.valueOf(resultSet1.getInt(3))));
					letterModel.setPrincipalOutstanding(convertRoundedValue(String.valueOf(resultSet1.getInt(4))));
					letterModel.setRateType(resultSet1.getString(5));
				}

				PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT PF_RECEIVABLE FROM Cc_Contract_Fee_Details where contract_number=?");
				preparedStatement2.setString(1, letterModel.getContractNumber());
				ResultSet resultSet2 = preparedStatement2.executeQuery();
				while (resultSet2.next()) {
					letterModel.setProcessingFee(resultSet2.getString(1));
				}


				PreparedStatement preparedStatement40 = connection.prepareStatement("SELECT BASE_FILE_NUMBER FROM Hfs_File_Auto_Topup_Upload where customer_code=?");
				preparedStatement40.setString(1, letterModel.getCustomerCode());
				ResultSet resultSet4 = preparedStatement40.executeQuery();
				while (resultSet4.next()) {
					letterModel.setBaseFileNumber(resultSet4.getString(1));
				}
				PreparedStatement preparedStatement23 = connection.prepareStatement("Select purpose_of_loan from hfs_file_auto_topup_details where base_file_number=?");
				preparedStatement23.setString(1, letterModel.getBaseFileNumber());
				ResultSet resultSet23 = preparedStatement23.executeQuery();
				while (resultSet23.next()) {
					letterModel.setPurposeOfLoan(resultSet23.getString(1));
				}
				PreparedStatement preparedStatement17 = connection.prepareStatement("SELECT NACH_BANK_ACC_NUM FROM Hfs_File_Auto_Topup_details where base_file_number=?");
				preparedStatement17.setString(1, letterModel.getBaseFileNumber());
				ResultSet resultSet17 = preparedStatement17.executeQuery();
				while (resultSet17.next()) {
					letterModel.setAccountNo(resultSet17.getString(1));
				}


				PreparedStatement preparedStatement6 = connection.prepareStatement("SELECT ocm_company_name FROM sa_organization_company_master");
				ResultSet resultSet6 = preparedStatement6.executeQuery();
				while (resultSet6.next()) {
					letterModel.setCompanyName(resultSet6.getString(1));
				}
				PreparedStatement preparedStatement7 = connection.prepareStatement("SELECT a.obm_address_info.email"
						+ "  FROM sa_organization_branch_master a"
						+ "  WHERE obm_branch_code = ?");
				preparedStatement7.setString(1, letterModel.getBranchCode());
				ResultSet resultSet7 = preparedStatement7.executeQuery();

				while (resultSet7.next()) {
					letterModel.setBranchMailId(resultSet7.getString(1));
				}

				PreparedStatement preparedStatement8 = connection.prepareStatement("Select A.Obm_Address_Info.Street_L , A.Obm_Address_Info.Column1_L ,"
						+ "                A.Obm_Address_Info.Column2_L, A.Obm_Address_Info.Column3_L,"
						+ "                A.Obm_Address_Info.Column4_L, A.Obm_Address_Info.Column5_L,"
						+ "                A.Obm_Address_Info.Column7_L,"
						+ "                A.Obm_Address_Info.Pin_Zip_Code_L,"
						+ "                Nvl (Trim (A.Obm_Address_Info.Office_Phone_No),"
						+ "                     Trim (A.Obm_Address_Info.Column6_L)"
						+ "                    ),"
						+ "                Trim (A.Obm_Address_Info.Office_Fax_No) From Sa_Organization_Branch_Master A  Where Upper (Obm_Branch_Code) = Upper (?) And Rownum < 2");
				preparedStatement8.setString(1, letterModel.getBranchCode());
				ResultSet resultSet8 = preparedStatement8.executeQuery();
				while (resultSet8.next()) {
					branchAddress.setStreet(resultSet8.getString(1));
					branchAddress.setAddress1(resultSet8.getString(2));
					branchAddress.setAddress2(resultSet8.getString(3));
					branchAddress.setAddress3(resultSet8.getString(4));
					branchAddress.setAddress4(resultSet8.getString(5));
					branchAddress.setAddress5(resultSet8.getString(6));
					branchAddress.setAddress7(resultSet8.getString(7));
					branchAddress.setPinCode(resultSet8.getString(8));
					branchAddress.setTelePhoneNumber(resultSet8.getString(9));
					branchAddress.setOfficeFaxNo(resultSet8.getString(10));
				}

				PreparedStatement preparedStatement9 = connection.prepareStatement("Select City_Name"
						+ "   From Hfs_Vw_City"
						+ "   Where City_Code = ?"
						+ "   And State_Record_Id ="
						+ "   (Select Record_Id"
						+ "   From Hfs_Vw_State"
						+ "   Where State_Code = ?"
						+ "   And Country_Code = ?)");
				preparedStatement9.setString(1, branchAddress.getAddress4());
				preparedStatement9.setString(2, branchAddress.getAddress3());
				preparedStatement9.setString(3, branchAddress.getAddress2());
				ResultSet resultSet9 = preparedStatement9.executeQuery();
				while (resultSet9.next()) {
					branchAddress.setDistrictName(resultSet9.getString(1));
				}
				PreparedStatement preparedStatement10 = connection.prepareStatement("Select Location_Name"
						+ "   From Hfs_Vw_Postal_Code"
						+ "   Where Location_Code =?"
						+ "   And City_Code = ?"
						+ "   And State_Code = ?"
						+ "   And Country_Code = ?");
				preparedStatement10.setString(1, branchAddress.getAddress5());
				preparedStatement10.setString(2, branchAddress.getAddress4());
				preparedStatement10.setString(3, branchAddress.getAddress3());
				preparedStatement10.setString(4, branchAddress.getAddress2());
				ResultSet resultSet10 = preparedStatement10.executeQuery();
				while (resultSet10.next()) {
					branchAddress.setLocationName(resultSet10.getString(1));
				}



				PreparedStatement preparedStatement11 = connection.prepareStatement("SELECT A.CUM_NAME_INFO.NAME_1_L,A.CUM_NAME_INFO.NAME_2_L"
						+ ",A.CUM_NAME_INFO.NAME_3_L,"
						+ "A.CUM_NAME_INFO.NAME_4_L,A.CUM_NAME_INFO.NAME_5_L FROM Sa_Customer_Master A "
						+ "Where CUM_Customer_Code = ?");
				preparedStatement11.setString(1, letterModel.getCustomerCode());
				ResultSet resultSet11 = preparedStatement11.executeQuery();
				while (resultSet11.next()) {
					letterModel.setApplicant(resultSet11.getString(3));
					String custName = appendCustomerName(resultSet11);
					letterModel.setCustomerName(custName);
				}
				List<String> customerCodeList = new ArrayList<>();
				PreparedStatement preparedStatement15 = connection.prepareStatement("SELECT customer_code FROM cc_contract_addl_appl_dtls where contract_number=?"
						+ " and customer_type='CO'"
						+ "");
				preparedStatement15.setString(1, letterModel.getContractNumber());
				ResultSet resultSet15 = preparedStatement15.executeQuery();
				while (resultSet15.next()) {
					customerCodeList.add(resultSet15.getString(1));
				}
				List<String> applicantNameList = new ArrayList<>();
				customerCodeList.stream().forEach(code->{
					try {
						preparedStatement11.setString(1, code);
						ResultSet resultSet16 = preparedStatement11.executeQuery();
						while (resultSet16.next()) {
							applicantNameList.add(resultSet16.getString(3));
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
				});
				if(!applicantNameList.isEmpty()) {
					getCoApplicantNames(applicantNameList,letterModel);
				}
				PreparedStatement preparedStatement12 = connection.prepareStatement("Select Nvl (Trim (A.Obm_Address_Info.Office_Phone_No),"
						+ "   Trim (A.Obm_Address_Info.Column6_L)"
						+ "     ),"
						+ "                Trim (A.Obm_Address_Info.Office_Fax_No)"
						+ "           From Sa_Organization_Branch_Master A"
						+ "          Where Upper (Obm_Branch_Code) = Upper (?)"
						+ "                And Rownum < 2");
				preparedStatement12.setString(1, letterModel.getBranchCode());
				ResultSet resultSet12 = preparedStatement12.executeQuery();
				while (resultSet12.next()) {
					letterModel.setTelePhoneNumber(getConvertedPhoneNumber(resultSet12.getString(1),resultSet12.getString(2)));
				}


				String branchAddressString = convertBranchAddress(branchAddress);
				letterModel.setBranchAddress(branchAddressString);

				PreparedStatement preparedStatement5 = connection.prepareStatement("Select Listagg(Loan_Desc,', ') Within Group (Order By Loan_Desc)"
						+ "      From ("
						+ "      Select Upper(Usage_Of_Loan_Desc)||' - '||Listagg(End_Use_Desc,', ') Within Group (Order By Usage_Of_Loan_Code) Loan_Desc From"
						+ "      Hfs_Tb_End_Of_Usage_Loan"
						+ "      Where File_Number = ? Group By Usage_Of_Loan_Desc)");
				preparedStatement5.setString(1, letterModel.getContractNumber());
				ResultSet resultSet5 = preparedStatement5.executeQuery();
				while (resultSet5.next()) {
					letterModel.setEndUseOfLoan(resultSet5.getString(1));
				}
				PreparedStatement preparedStatement18 = connection.prepareStatement("select C.Caa_Address_Info.Street_L,C.Caa_Address_Info.Column1_L,C.Caa_Address_Info.Column2_L,C.Caa_Address_Info.Column3_L,"
						+ "C.Caa_Address_Info.Column4_L,C.Caa_Address_Info.Column5_L,C.Caa_Address_Info.Column7_L, C.Caa_Address_Info.Pin_Zip_Code_L Zipcode FROM Sa_Customer_Addl_Address_Dtls C "
						+ "where C.caa_customer_code=? And C.CAA_ADDRESS_TYPE_CODE = 1");
				preparedStatement18.setString(1, letterModel.getCustomerCode());
				ResultSet resultSet18 = preparedStatement18.executeQuery();
				CustomerAddress customerAddress = new CustomerAddress();
				while (resultSet18.next()) {
					customerAddress.setStreet(resultSet18.getString(1));
					customerAddress.setAddress1(resultSet18.getString(2));
					customerAddress.setAddress2(resultSet18.getString(3));
					customerAddress.setAddress3(resultSet18.getString(4));
					customerAddress.setAddress4(resultSet18.getString(5));
					customerAddress.setAddress5(resultSet18.getString(6));
					customerAddress.setAddress7(resultSet18.getString(7));
					customerAddress.setZipCode(resultSet18.getString(8));
				}
				PreparedStatement preparedStatement19 = connection.prepareStatement("Select Gld_Geo_Level_Desc"
						+ "	From   SA_GEOGRAPHICAL_LEVEL_DETAILS"
						+ "	Where  Gld_Geo_Level_Code   = ?"
						+ "	And    GLD_GEO_LEVEL_STATUS = 'A'"
						+ "	And    GLD_GEO_LEVEL_STRING = ?||':'||?||':'||?"
						+ "	And    GLD_GEO_LEVEL_NUMBER = (Select GL_GEO_LEVEL_NUMBER"
						+ "	From  SA_GEOGRAPHICAL_LEVELS"
						+ "	Where  GL_GEO_LEVEL_NAME = 'LOCATION')");
				preparedStatement19.setString(1, customerAddress.getAddress5());
				preparedStatement19.setString(2, customerAddress.getAddress2());
				preparedStatement19.setString(3, customerAddress.getAddress3());
				preparedStatement19.setString(4, customerAddress.getAddress4());
				ResultSet resultSet19 = preparedStatement19.executeQuery();
				while (resultSet19.next()) {
					customerAddress.setLocation(resultSet19.getString(1));
				}

				PreparedStatement preparedStatement20 = connection.prepareStatement("SELECT CITY_NAME FROM HFS_VW_CITY"
						+ " WHERE CITY_CODE = ? AND STATE_RECORD_ID ="
						+ " (SELECT RECORD_ID FROM HFS_VW_STATE WHERE STATE_CODE = ? AND COUNTRY_CODE = ?)");
				preparedStatement20.setString(1, customerAddress.getAddress4());
				preparedStatement20.setString(2, customerAddress.getAddress3());
				preparedStatement20.setString(3, customerAddress.getAddress2());
				ResultSet resultSet20 = preparedStatement20.executeQuery();
				while (resultSet20.next()) {
					customerAddress.setCity(resultSet20.getString(1));
				}

				PreparedStatement preparedStatement21 = connection.prepareStatement("Select Gld_Geo_Level_Desc From SA_GEOGRAPHICAL_LEVEL_DETAILS Where Gld_Geo_Level_Code   = ?"
						+ "	 And GLD_GEO_LEVEL_STATUS = 'A' And GLD_GEO_LEVEL_STRING = To_Char(Nvl(?,1)) And GLD_GEO_LEVEL_NUMBER = "
						+ "  (Select GL_GEO_LEVEL_NUMBER From SA_GEOGRAPHICAL_LEVELS Where GL_GEO_LEVEL_NAME = 'STATE')");
				preparedStatement21.setString(1, customerAddress.getAddress3());
				preparedStatement21.setString(2, customerAddress.getAddress2());
				ResultSet resultSet21 = preparedStatement21.executeQuery();
				while (resultSet21.next()) {
					customerAddress.setState(resultSet21.getString(1));
				}

				PreparedStatement preparedStatement22 = connection.prepareStatement("select Gld_Geo_Level_Desc from SA_GEOGRAPHICAL_LEVEL_DETAILS Where Gld_Geo_Level_Code  = ? And GLD_GEO_LEVEL_STATUS = 'A'"
						+ " And GLD_GEO_LEVEL_NUMBER = (Select GL_GEO_LEVEL_NUMBER From SA_GEOGRAPHICAL_LEVELS Where GL_GEO_LEVEL_NAME = 'COUNTRY')");
				preparedStatement22.setString(1, customerAddress.getAddress2());
				ResultSet resultSet22 = preparedStatement22.executeQuery();
				while (resultSet22.next()) {
					customerAddress.setCountry(resultSet22.getString(1));
				}
				Map<String,Object> prepareStatementList = new HashMap<>();
				prepareStatementList.put("preparedStatement18",preparedStatement18);
				prepareStatementList.put("preparedStatement19",preparedStatement19);
				prepareStatementList.put("preparedStatement20",preparedStatement20);
				prepareStatementList.put("preparedStatement21",preparedStatement21);
				prepareStatementList.put("preparedStatement22",preparedStatement22);
				String customerAddressString = appendCustomerAddress(customerAddress,letterModel.getCustomerAddress());
				letterModel.setCustomerAddress(customerAddressString);

				//				fetchOracleDataForMITC(letterModel);
				fetchOracleDataForMOTD(letterModel,prepareStatementList);

				Date date = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
				letterModel.setCurrentDate(formatter.format(date));
				letterModelList.add(letterModel);
			}

		} catch (Exception e) {
			// Handle SQL exception
			e.printStackTrace();
		}finally {
			try {
				if(connection!=null) {
					connection.close();
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return letterModelList;
	}

	private void fetchOracleDataForMITC(LetterReportModel letterModel) {
		dynamicDataSourceService.switchToOracleDataSource();
		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;		
		try {
			connection = currentDataSource.getConnection();
			PreparedStatement preparedStatement24 = connection.prepareStatement("select flat_fee,fee_rate From Hfs_Doc_Fee_Master_Header A ,"
					+ "   Hfs_Doc_Fee_Master_Dtls B"
					+ "   Where A.Bucket_Key=B.Bucket_Key"
					+ "   And ? Between Start_Term And End_Term"
					+ "   And ? Between Start_Amount And End_Amount"
					+ "   And A.Bucket_Code="
					+ "   (Select Max(Bucket_Code)"
					+ "   From Hfs_Doc_Fee_Master_Header C ,"
					+ "   Hfs_Doc_Fee_Master_Branch_Dtls E"
					+ "   Where (?) Between to_date(Start_Date,'dd-MM-yy') And to_date(End_Date,'dd-MM-yy')"
					+ "   And"
					+ "   E.Bucket_Key =C.Bucket_Key And E.Branch_Code=?)");

			String effectiveDate = convertDateFormat((letterModel.getApplicationDate()));
			preparedStatement24.setInt(1,letterModel.getTerm());
			preparedStatement24.setInt(2, letterModel.getAmountFinanced());
			preparedStatement24.setString(3, effectiveDate);
			preparedStatement24.setString(4, letterModel.getBranchCode());
			ResultSet resultSet24 = preparedStatement24.executeQuery();
			while (resultSet24.next()) {
				letterModel.setFlatFee(resultSet24.getString(1));
				letterModel.setFlatRate(resultSet24.getString(2));
			}
			setDocumentChargesValue(letterModel);

			//			PreparedStatement preparedStatement25 = connection.prepareStatement("Select A.Division_Code,A.Product_Code,A.Scheme_Code,B.Rate_Type"
			//					+ "    From Cc_Contract_Master A,"
			//					+ "      Cc_Contract_Rate_Details B"
			//					+ "    Where A.Contract_Number = B.Contract_Number"
			//					+ "    And B.Occurance_Number  ="
			//					+ "      (Select Max(C.Occurance_Number)"
			//					+ "      From Cc_Contract_Rate_Details C"
			//					+ "      Where C.Contract_Number = A.Contract_Number)"
			//					+ "    And A.Contract_Number = ?");
			//			preparedStatement25.setString(1,letterModel.getApplicationNumber());
			//			ResultSet resultSet25 = preparedStatement25.executeQuery();
			//			while (resultSet25.next()) {
			//				letterModel.setDivisionCode(resultSet25.getString(1));
			//				letterModel.setProductCode(resultSet25.getString(2));
			//				letterModel.setSchemeCode(resultSet25.getString(3));
			//				letterModel.setRateType(resultSet25.getString(4));
			//			}
			//			PreparedStatement preparedStatement26 = connection.prepareStatement("Select Decode(A.Product_Type,'I',1,'C',2)"
			//					+ " From Sa_Division_Master A"
			//					+ "  Where Product_Code = ?"
			//					+ "  And Division_Code  = ?");
			//			preparedStatement26.setString(1,letterModel.getProductCode());
			//			preparedStatement26.setString(2, letterModel.getDivisionCode());
			//			ResultSet resultSet26 = preparedStatement26.executeQuery();
			//			while (resultSet26.next()) {
			//				letterModel.setBorrower(resultSet26.getString(1));
			//			}
			//			PreparedStatement preparedStatement27 = connection.prepareStatement("Select Distinct Usage_Of_Loan_Code"
			//					+ "  From Hfs_Tb_End_Of_Usage_Loan"
			//					+ "  Where File_Number = ?");
			//			preparedStatement27.setString(1,letterModel.getContractNumber());
			//			ResultSet resultSet27 = preparedStatement27.executeQuery();
			//			List<Integer> codeList = new ArrayList<>();
			//			while (resultSet27.next()) {
			//				int code = resultSet27.getInt(1);
			//				codeList.add(code);
			//			}
			//			if(codeList.size()>1) {
			//				letterModel.setEndUseOfLoanCode("1");
			//			}else {
			//				letterModel.setEndUseOfLoanCode(null);
			//			}
			//			PreparedStatement preparedStatement28 = connection.prepareStatement("Select REFERENCE"
			//					+ " From Cc_Contract_Master"
			//					+ " Where CONTRACT_NUMBER = ?");
			//			preparedStatement28.setString(1,letterModel.getContractNumber());
			//			ResultSet resultSet28 = preparedStatement28.executeQuery();
			//			while (resultSet28.next()) {
			//				letterModel.setReference(resultSet28.getString(1));
			//			}
			//			if(letterModel.getReference().equals("T")&&Objects.isNull(letterModel.getEndUseOfLoanCode())) {
			//				PreparedStatement preparedStatement29 = connection.prepareStatement("Select Distinct Usage_Of_Loan_Code"
			//						+ "  From Hfs_Tb_End_Of_Usage_Loan"
			//						+ "  Where File_Number = (Select Acct_No From CC_Contract_Master Where Contract_number = ?)");
			//				preparedStatement29.setString(1,letterModel.getContractNumber());
			//				ResultSet resultSet29 = preparedStatement29.executeQuery();
			//				codeList.clear();
			//				while (resultSet29.next()) {
			//					int code = resultSet29.getInt(1);
			//					codeList.add(code);
			//				}
			//				if(codeList.size()>1) {
			//					letterModel.setEndUseOfLoanCode("1");
			//				}else {
			//					letterModel.setEndUseOfLoanCode(null);
			//				}
			//			}
			//			PreparedStatement preparedStatement30 = connection.prepareStatement("Select Prepayment_Percentage"
			//					+ "  From Hfs_Prepayment_Charge_Master A"
			//					+ "  Where A.Borrower_Type         = ?"
			//					+ "  And A.Business_Type           = ?"
			//					+ "  And A.Rate_Type               = ?"
			//					+ "  And A.Usage_Of_Loan           = ?"
			//					+ "  And A.Prepayment_Chrge_Reason = ?"
			//					+ "  And Effective_Date="
			//					+ " (Select Max(Effective_Date)"
			//					+ "  From Hfs_Prepayment_Charge_Master B"
			//					+ "  Where A.Borrower_Type         = B.Borrower_Type"
			//					+ "  And A.Business_Type           = B.Business_Type"
			//					+ "  And A.Rate_Type               = B.Rate_Type"
			//					+ "  And A.Usage_Of_Loan           = B.Usage_Of_Loan"
			//					+ "  And A.Prepayment_Chrge_Reason = B.Prepayment_Chrge_Reason)");
			//			preparedStatement30.setString(1,letterModel.getBorrower());
			//			preparedStatement30.setString(2, letterModel.getProductCode());
			//			preparedStatement30.setString(3, letterModel.getRateType());
			//			preparedStatement30.setString(4, letterModel.getEndUseOfLoanCode());
			//			preparedStatement30.setString(5, "");
			//			ResultSet resultSet30 = preparedStatement30.executeQuery();
			//			while (resultSet30.next()) {
			//				letterModel.setPrePaymentCharges(resultSet30.getString(1));
			//			}
			//			letterModel.setPrePaymentCharges("0");
			//			PreparedStatement preparedStatement31 = connection.prepareStatement("SELECT A.CHEQUE_BOUNCE_CHARGES FROM Sa_Product_Scheme_Dtls A ,Sa_Product_Scheme_Master_Hdr B"
			//					+ " WHERE A.Scheme_Code  =? AND A.Division_Code=?"
			//					+ "	AND A.Header_Key=B.Header_Key AND A.Effective_Date<=?"
			//					+ " AND A.Effective_Date ="
			//					+ "(SELECT MAX(Effective_Date) FROM Sa_Product_Scheme_Dtls"
			//					+ "  WHERE A.Header_Key =Header_Key AND Effective_Date<=?"
			//					+ "  AND ? BETWEEN Minimum_Term AND Maximum_Term"
			//					+ "  AND ? BETWEEN Minimum_Loan_Amount AND Maximum_Loan_Amount)"
			//					+ "  AND ? BETWEEN A.Minimum_Term AND A.Maximum_Term"
			//					+ "  AND ? BETWEEN A.Minimum_Loan_Amount AND A.Maximum_Loan_Amount");
			//			preparedStatement31.setString(1,letterModel.getSchemeCode() );
			//			preparedStatement31.setString(2, letterModel.getDivisionCode());
			//			preparedStatement31.setString(3, effectiveDate);
			//			preparedStatement31.setString(4, effectiveDate);
			//			preparedStatement31.setInt(5, letterModel.getTerm());
			//			preparedStatement31.setInt(6, letterModel.getAmountFinanced());
			//			preparedStatement31.setInt(7, letterModel.getTerm());
			//			preparedStatement31.setInt(8, letterModel.getAmountFinanced());
			//			ResultSet resultSet31 = preparedStatement31.executeQuery();
			//			while (resultSet31.next()) {
			//				letterModel.setChequeReturnCharges(resultSet31.getString(1));
			//			}
			//			PreparedStatement	preparedStatement32 = connection.prepareStatement("SELECT Cash_Hand_Charges,"
			//					+ "        Denomination"
			//					+ "      FROM Sa_Cash_Hand_Charges A"
			//					+ "      WHERE ? BETWEEN From_Receipt_Amount AND To_Receipt_Amount"
			//					+ "      AND A.Effective_Date ="
			//					+ "        (SELECT MAX (Effective_Date) FROM Sa_Cash_Hand_Charges B"
			//					+ "        )");
			//			preparedStatement32.setInt(1,letterModel.getAmountFinanced());
			//			ResultSet resultSet32 = preparedStatement32.executeQuery();
			//			List<CashHandlingChargesModel> cashHandlingList = new ArrayList<>();
			//			while (resultSet32.next()) { 
			//				CashHandlingChargesModel cashHandlingChargesModel = new CashHandlingChargesModel();
			//				String denomination = resultSet32.getString(2);
			//				if(denomination.equals("L")) {
			//					cashHandlingChargesModel.setCashHandlingCharges(0);
			//				}else if(denomination.equals("R")) {
			//					cashHandlingChargesModel.setCashHandlingCharges(resultSet32.getInt(1));
			//				}
			//				cashHandlingList.add(cashHandlingChargesModel);
			//			}
			//			letterModel.setCashHandlingCharges(cashHandlingList);

		}catch (Exception e) {
			e.printStackTrace();
		}finally {
			try {
				if(connection!=null) {
					connection.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private void fetchOracleDataForMOTD(LetterReportModel letterModel, Map<String, Object> prepareStatementList) {
		dynamicDataSourceService.switchToOracleDataSource();
		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		LinkedHashSet<PropertyNumberModel> propertyNumberModelList = new LinkedHashSet<>();
		LinkedHashSet<TitleHolderDetail> titleHolderList = new LinkedHashSet<>();
		TreeMap<String,LinkedHashSet<ScheduleA>> scheduleListMap = new TreeMap<>();
		TreeMap<String,ScheduleB> scheduleBListMap= new TreeMap<>();
		TreeMap<String,Boundries> boundriesListMap= new TreeMap<>();
		TreeMap<String,Measurement> measurementListMap= new TreeMap<>();
		Set<ScheduleA> scheduleAList = new LinkedHashSet<>();
		PropertyDetailModel propertyDetailModel = new PropertyDetailModel();
		try {
			connection = currentDataSource.getConnection();
			if(Objects.nonNull(letterModel.getRateType())) {
				PreparedStatement preparedStatement1 = connection.prepareStatement("Select Rate_Type, Rate_Type_Desc From Sa_Rate_Type_Dir where Rate_Type=?");
				preparedStatement1.setString(1, letterModel.getRateType());
				ResultSet resultSet1 = preparedStatement1.executeQuery();
				while (resultSet1.next()) {
					letterModel.setRateTypeString(resultSet1.getString(2));
				}
			}
			//property main

			PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT property_customer_code,property_number FROM cc_property_details where contract_number=? and property_customer_code=? order by property_number");
			preparedStatement2.setString(1, letterModel.getContractNumber());
			preparedStatement2.setString(2, letterModel.getCustomerCode());
			ResultSet resultSet2 = preparedStatement2.executeQuery();
			while (resultSet2.next()) {
				PropertyNumberModel propertyNumberModel = new PropertyNumberModel();
				propertyNumberModel.setPropertyCustomerCode(resultSet2.getString(1));
				propertyNumberModel.setPropertyNumber(resultSet2.getInt(2));
				propertyNumberModelList.add(propertyNumberModel);
			}
			propertyDetailModel.setPropertyNumberModelList(propertyNumberModelList);
			//share detail

			propertyNumberModelList.stream().forEach(propertyNumberModel->{
				PreparedStatement preparedStatement3;
				try {
					Connection connection1 = currentDataSource.getConnection();
					preparedStatement3 = connection1.prepareStatement("Select share_customer_code ,Title_Holder_Name, Customer_Code,property_number"
							+ " From Sa_Customer_Property_Share where customer_code=? and property_number =? order by property_number");

					preparedStatement3.setString(1, propertyNumberModel.getPropertyCustomerCode());
					preparedStatement3.setInt(2, propertyNumberModel.getPropertyNumber());
					ResultSet resultSet3 = preparedStatement3.executeQuery();
					while (resultSet3.next()) {
						TitleHolderDetail titleHolderDetail = new TitleHolderDetail();
						titleHolderDetail.setCustomerShareCode(resultSet3.getString(1));
						titleHolderDetail.setTitleHolderName(resultSet3.getString(2));
						titleHolderDetail.setCustomerCode(resultSet3.getString(3));
						titleHolderDetail.setPropertyNumber(resultSet3.getInt(4));
						//title
						getTitleInfo(titleHolderDetail);

						//aadhar
						getAadharForTitle(titleHolderDetail);
						//age
						getAgeForTitle(titleHolderDetail);

						//address
						getAddressForTitle(titleHolderDetail,prepareStatementList);

						//getScheduleA
						getScheduleADetail(letterModel,propertyDetailModel,titleHolderDetail,scheduleListMap);

						//schedule B &boundries&measurement
						getschedulBAndBoundriyMeasurment(letterModel,titleHolderDetail,boundriesListMap,measurementListMap,scheduleBListMap);

						titleHolderList.add(titleHolderDetail);

					}
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			propertyDetailModel.setTitleHolderDetailList(titleHolderList);
			propertyDetailModel.setScheduleListMap(scheduleListMap);
			propertyDetailModel.setScheduleBListMap(scheduleBListMap);
			propertyDetailModel.setMeasurementListMap(measurementListMap);
			propertyDetailModel.setBoundriesListMap(boundriesListMap);
			letterModel.setPropertyDetailModel(propertyDetailModel);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void getschedulBAndBoundriyMeasurment(LetterReportModel letterModel, TitleHolderDetail titleHolderDetail, Map<String, Boundries> boundriesListMap, Map<String, Measurement> measurementListMap, Map<String, ScheduleB> scheduleBListMap) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		Boundries boundries = new Boundries();
		Measurement measurement = new Measurement();
		ScheduleB scheduleB = new ScheduleB();
		PropertyAddress propertyAddress = new PropertyAddress();
		try {
			connection = currentDataSource.getConnection();
			Connection connection3 = currentDataSource.getConnection();
			PreparedStatement preparedStatement10 = connection3.prepareStatement("Select Contract_Number,Bounded_North,"
					+ " Bounded_South, Bounded_East, Bounded_West ,"
					+ " Survey, Plot, Door_No, Building_Society_Name, State_Name, District, Taluk_Tehsil,"
					+ " Town, Village, Sro, City_Code From cc_property_category_details where contract_number=?"
					+ " and customer_code=? and property_number=?");
			preparedStatement10.setString(1, letterModel.getContractNumber());
			preparedStatement10.setString(2, titleHolderDetail.getCustomerShareCode());
			preparedStatement10.setInt(3,titleHolderDetail.getPropertyNumber());
			ResultSet resultSet10 = preparedStatement10.executeQuery();
			if(!resultSet10.isBeforeFirst()) {
				scheduleB = null;
				boundries = null;
			}else {
				while (resultSet10.next()) {
					boundries.setNorthBoundry(resultSet10.getString(2));
					boundries.setSouthBoundry(resultSet10.getString(3));
					boundries.setEastBoundry(resultSet10.getString(4));
					boundries.setWestBoundry(resultSet10.getString(5));
					//
					scheduleB.setSurveyNo(resultSet10.getString(6));
					scheduleB.setPlotNo(resultSet10.getString(7));
					scheduleB.setDoorNo(resultSet10.getString(8));
					scheduleB.setBuildingSocietyName(resultSet10.getString(9));
					scheduleB.setStateName(resultSet10.getString(10));
					//scheduleB.setDistrict(resultSet10.getString(11));
					//scheduleB.setTaluk(resultSet10.getString(12));
					scheduleB.setTown(resultSet10.getString(13));
					//scheduleB.setVillage(resultSet10.getString(14));
					scheduleB.setSro(resultSet10.getString(15));
					scheduleB.setCityCode(resultSet10.getString(15));
					letterModel.setSRO(resultSet10.getString(15));

					//propertyAddress
					PreparedStatement preparedStatement11 = connection3.prepareStatement("Select A.Property_Address.Street_L,A.Property_Address.Column1_L,"
							+ "	A.Property_Address.Column2_L,A.Property_Address.Column3_L,"
							+ "	A.Property_Address.Column4_L,A.Property_Address.Column5_L,"
							+ "	A.Property_Address.Column6_L,A.Property_Address.Column7_L,"
							+ "	A.Property_Address.Column8_L,A.Property_Address.Column9_L,"
							+ "	A.Property_Address.Column10_L,A.Property_Address.Pin_Zip_Code_L,"
							+ "	A.Property_Address.Office_Phone_No,A.Property_Address.Residence_Phone_No,"
							+ "	A.Property_Address.Office_Fax_No,A.Property_Address.Residence_Fax_No,"
							+ "	A.Property_Address.Mobile_No,A.Property_Address.Pager_No,"
							+ "	A.Property_Address.Email,A.Land_Area_Sq_Ft,A.flat_no,A.flat_floor_no,A.flat_remarks"
							+ "	From Sa_Customer_Property_Dtls A where customer_code=? and property_number=?");
					preparedStatement11.setString(1, titleHolderDetail.getCustomerShareCode());
					preparedStatement11.setInt(2, titleHolderDetail.getPropertyNumber());
					ResultSet resultSet11 = preparedStatement11.executeQuery();

					while (resultSet11.next()) {
						propertyAddress.setStreet(resultSet11.getString(1));
						propertyAddress.setAddress1(resultSet11.getString(2));
						propertyAddress.setAddress2(resultSet11.getString(3));
						propertyAddress.setAddress3(resultSet11.getString(4));
						propertyAddress.setAddress4(resultSet11.getString(5));
						propertyAddress.setAddress5(resultSet11.getString(6));
						propertyAddress.setAddress6(resultSet11.getString(7));
						propertyAddress.setAddress7(resultSet11.getString(8));
						propertyAddress.setAddress8(resultSet11.getString(9));
						propertyAddress.setAddress9(resultSet11.getString(10));
						propertyAddress.setAddress10(resultSet11.getString(11));
						propertyAddress.setPinCode(resultSet11.getString(12));
						propertyAddress.setOfficePhoneNo(resultSet11.getString(13));
						propertyAddress.setResidencePhoneNo(resultSet11.getString(14));
						propertyAddress.setOfficeFaxNo(resultSet11.getString(15));
						propertyAddress.setResidenceFaxNo(resultSet11.getString(16));
						propertyAddress.setMobileNo(resultSet11.getString(17));
						propertyAddress.setPagerNo(resultSet11.getString(18));
						propertyAddress.setEmail(resultSet11.getString(19));
						propertyAddress.setLandExtent(resultSet11.getString(20));
						propertyAddress.setFlatNo(resultSet11.getString(21));
						propertyAddress.setFloorNo(resultSet11.getString(22));
						propertyAddress.setBlock(resultSet11.getString(23));

						PreparedStatement preparedStatement12 = connection3.prepareStatement("Select City_Name"
								+ "   From Hfs_Vw_City"
								+ "   Where City_Code = ?"
								+ "   And State_Record_Id ="
								+ "   (Select Record_Id"
								+ "   From Hfs_Vw_State"
								+ "   Where State_Code = ?"
								+ "   And Country_Code = ?)");
						preparedStatement12.setString(1, propertyAddress.getAddress4());
						preparedStatement12.setString(2, propertyAddress.getAddress3());
						preparedStatement12.setString(3, propertyAddress.getAddress2());
						ResultSet resultSet12 = preparedStatement12.executeQuery();
						while (resultSet12.next()) {
							propertyAddress.setCityName(resultSet12.getString(1));
						}
						PreparedStatement preparedStatement13 = connection3.prepareStatement("Select Location_Name"
								+ "   From Hfs_Vw_Postal_Code"
								+ "   Where Location_Code =?"
								+ "   And City_Code = ?"
								+ "   And State_Code = ?"
								+ "   And Country_Code = ?");
						preparedStatement13.setString(1, propertyAddress.getAddress5());
						preparedStatement13.setString(2, propertyAddress.getAddress4());
						preparedStatement13.setString(3, propertyAddress.getAddress3());
						preparedStatement13.setString(4, propertyAddress.getAddress2());
						ResultSet resultSet13 = preparedStatement13.executeQuery();
						while (resultSet13.next()) {
							propertyAddress.setLocationName(resultSet13.getString(1));
						}
						scheduleB.setPropertyAddress(propertyAddress);
					}
				}
			}
			PreparedStatement preparedStatement14 = connection3.prepareStatement("Select North_By,South_By,East_By,West_By,"
					+ "North_By_Measurements,South_By_Measurements,"
					+ "East_By_Measurements,West_By_Measurements "
					+ "From Cc_Technical_Valuation_Report where contract_number=? and property_number =? and property_cust_code=?");
			preparedStatement14.setString(1, letterModel.getContractNumber());
			preparedStatement14.setInt(2, titleHolderDetail.getPropertyNumber());
			preparedStatement14.setString(3, titleHolderDetail.getCustomerShareCode());
			ResultSet resultSet14 = preparedStatement14.executeQuery();
			if(!resultSet14.isBeforeFirst()) {
				measurement = null;
			}else {
				while (resultSet14.next()) {
					measurement.setNorthMeasurement(resultSet14.getString(5));
					measurement.setSouthMeasurement(resultSet14.getString(6));
					measurement.setEastMeasurement(resultSet14.getString(7));
					measurement.setWestMeasurement(resultSet14.getString(8));
				}
			}
			boundriesListMap.put(String.valueOf(titleHolderDetail.getPropertyNumber()),boundries);
			measurementListMap.put(String.valueOf(titleHolderDetail.getPropertyNumber()),measurement);
			scheduleBListMap.put(String.valueOf(titleHolderDetail.getPropertyNumber()),scheduleB);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void getScheduleADetail(LetterReportModel letterModel, PropertyDetailModel propertyDetailModel, TitleHolderDetail titleHolderDetail, Map<String, LinkedHashSet<ScheduleA>> scheduleListMap) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		LinkedHashSet<ScheduleA> scheduleAList = new LinkedHashSet<>();
		try {
			connection = currentDataSource.getConnection();
			PreparedStatement preparedStatement8 = connection.prepareStatement("SELECT Contract_Number, Document_Id Doc_Id, Collection_Date, Original_Document, Document_Number, Title_Holder_Name, Document_Type, Document_Date FROM Dc_Document_Processing where "
					+ "Contract_Number=? and entity_code=? and asset_number=?");
			preparedStatement8.setString(1, letterModel.getContractNumber());
			preparedStatement8.setString(2, titleHolderDetail.getCustomerShareCode());
			preparedStatement8.setInt(3, titleHolderDetail.getPropertyNumber());
			ResultSet resultSet8 = preparedStatement8.executeQuery();
			while(resultSet8.next()) {
				ScheduleA scheduleA = new ScheduleA();
				scheduleA.setDocumentId(resultSet8.getString(2));
				scheduleA.setCollectionDate(resultSet8.getString(3));
				scheduleA.setOriginalDocument(resultSet8.getString(4));
				scheduleA.setDocuemntNumber(resultSet8.getString(5));
				titleHolderDetail.setOtdNumber(resultSet8.getString(5));
				scheduleA.setTitleHolderName(getStringFromObject(resultSet8.getString(6)));
				scheduleA.setDocmentType(resultSet8.getString(7));
				scheduleA.setDocumentDate(resultSet8.getString(8));
				PreparedStatement preparedStatement9 = connection.prepareStatement("Select Dty_Document_Id Doc_Id, Dty_Document_Desc Document_Name "
						+ "From Sa_Document_Type_Master where Dty_Document_Id=?");
				preparedStatement9.setString(1, scheduleA.getDocumentId());
				ResultSet resultSet12 = preparedStatement9.executeQuery();
				while (resultSet12.next()) {
					scheduleA.setDocumentName(resultSet12.getString(2));
				}
				scheduleAList.add(scheduleA);
			}
			scheduleListMap.put(String.valueOf(titleHolderDetail.getPropertyNumber()), scheduleAList);
		}catch (Exception e) {
			e.printStackTrace();
		}		
	}

	private void getAddressForTitle(TitleHolderDetail titleHolderDetail, Map<String, Object> prepareStatementList) {
		try {
			PreparedStatement preparedStatement7 = (PreparedStatement) prepareStatementList.get("preparedStatement18");
			preparedStatement7.setString(1, titleHolderDetail.getCustomerShareCode());
			ResultSet resultSet7 = preparedStatement7.executeQuery();
			setOtherAddress(resultSet7,prepareStatementList,titleHolderDetail);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void getAgeForTitle(TitleHolderDetail titleHolderDetail) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		try {
			connection = currentDataSource.getConnection();
			PreparedStatement preparedStatement6 = connection.prepareStatement("Select Hcoi_Dob Dob From Hfs_Customer_Other_Info where hcoi_customer_code =?");
			preparedStatement6.setString(1, titleHolderDetail.getCustomerShareCode());
			ResultSet resultSet6 = preparedStatement6.executeQuery();
			while (resultSet6.next()) {
				titleHolderDetail.setDateOfBirth(resultSet6.getString(1));
				int age =getAgeFromDate(titleHolderDetail);
				titleHolderDetail.setAge(age);
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void getAadharForTitle(TitleHolderDetail titleHolderDetail) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		try {
			connection = currentDataSource.getConnection();
			PreparedStatement preparedStatement5 = connection.prepareStatement("SELECT CUM_AADHAR_NO FROM sa_customer_master where cum_customer_code=?");
			preparedStatement5.setString(1, titleHolderDetail.getCustomerShareCode());
			ResultSet resultSet5 = preparedStatement5.executeQuery();
			while (resultSet5.next()) {
				titleHolderDetail.setTitleAadharNo(resultSet5.getString(1));
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void getTitleInfo(TitleHolderDetail titleHolderDetail) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		try {
			connection = currentDataSource.getConnection();
			PreparedStatement preparedStatement4 = connection.prepareStatement("SELECT A.CUM_NAME_INFO.NAME_1_L"
					+ " FROM Sa_Customer_Master A Where CUM_Customer_Code = ?");
			preparedStatement4.setString(1, titleHolderDetail.getCustomerShareCode());
			ResultSet resultSet4 = preparedStatement4.executeQuery();
			while (resultSet4.next()) {
				titleHolderDetail.setTitle(resultSet4.getString(1));
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private int getAgeFromDate(TitleHolderDetail titleHolderDetail) {
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDate inputDate = LocalDate.parse(titleHolderDetail.getDateOfBirth(),format);
		LocalDate currentDate = LocalDate.now();
		int age =  Period.between(inputDate, currentDate).getYears();
		return age;

	}

	private void setOtherAddress(ResultSet resultSet18,  Map<String, Object> prepareStatementList, TitleHolderDetail titleHolderDetail) {
		CustomerAddress customerAddress = new CustomerAddress();
		PreparedStatement preparedStatement19 = (PreparedStatement) prepareStatementList.get("preparedStatement19");
		PreparedStatement preparedStatement20 = (PreparedStatement) prepareStatementList.get("preparedStatement20");
		PreparedStatement preparedStatement21 = (PreparedStatement) prepareStatementList.get("preparedStatement21");
		PreparedStatement preparedStatement22 = (PreparedStatement) prepareStatementList.get("preparedStatement22");
		try {
			while (resultSet18.next()) {
				customerAddress.setStreet(resultSet18.getString(1));
				customerAddress.setAddress1(resultSet18.getString(2));
				customerAddress.setAddress2(resultSet18.getString(3));
				customerAddress.setAddress3(resultSet18.getString(4));
				customerAddress.setAddress4(resultSet18.getString(5));
				customerAddress.setAddress5(resultSet18.getString(6));
				customerAddress.setAddress7(resultSet18.getString(7));
				customerAddress.setZipCode(resultSet18.getString(8));
			}

			preparedStatement19.setString(1, customerAddress.getAddress5());
			preparedStatement19.setString(2, customerAddress.getAddress2());
			preparedStatement19.setString(3, customerAddress.getAddress3());
			preparedStatement19.setString(4, customerAddress.getAddress4());
			ResultSet resultSet19 = preparedStatement19.executeQuery();
			while (resultSet19.next()) {
				customerAddress.setLocation(resultSet19.getString(1));
			}

			preparedStatement20.setString(1, customerAddress.getAddress4());
			preparedStatement20.setString(2, customerAddress.getAddress3());
			preparedStatement20.setString(3, customerAddress.getAddress2());
			ResultSet resultSet20 = preparedStatement20.executeQuery();
			while (resultSet20.next()) {
				customerAddress.setCity(resultSet20.getString(1));
			}

			preparedStatement21.setString(1, customerAddress.getAddress3());
			preparedStatement21.setString(2, customerAddress.getAddress2());
			ResultSet resultSet21 = preparedStatement21.executeQuery();
			while (resultSet21.next()) {
				customerAddress.setState(resultSet21.getString(1));
			}

			preparedStatement22.setString(1, customerAddress.getAddress2());
			ResultSet resultSet22 = preparedStatement22.executeQuery();
			while (resultSet22.next()) {
				customerAddress.setCountry(resultSet22.getString(1));
			}
			appendTitleHolderAddress(customerAddress,titleHolderDetail);
		}catch (Exception e) {
			// TODO: handle exception
		}

	}

	private void appendTitleHolderAddress(CustomerAddress customerAddress, TitleHolderDetail titleHolderDetail) {
		String customerAddressString=	customerAddress.getStreet()+","+customerAddress.getAddress1()+","+customerAddress.getAddress7()+","+customerAddress.getLocation()+
				","+customerAddress.getCity()+"-"+customerAddress.getZipCode()+","+customerAddress.getState()+","+customerAddress.getCountry();
		titleHolderDetail.setTitleHolderAddress(customerAddressString);
	}


	private String convertDateFormat(String dateValue) {
		SimpleDateFormat inputFormater = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss",Locale.ENGLISH);
		SimpleDateFormat outputFormater = new SimpleDateFormat("dd-MM-yy");
		String outputDateStr ="";
		try {
			Date dates = inputFormater.parse(dateValue);
			outputDateStr = outputFormater.format(dates);		
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return outputDateStr;
	}

	private void setDocumentChargesValue(LetterReportModel letterModel) {
		if(Objects.nonNull(letterModel.getFlatFee())&&
				Integer.parseInt(letterModel.getFlatFee())>0) {
			letterModel.setDocumentationCharges(letterModel.getFlatFee());
		}else if(Objects.nonNull(letterModel.getFlatRate())&&
				Integer.parseInt(letterModel.getFlatRate())>0) {
			int processingFee =  Integer.parseInt(letterModel.getFlatRate())*(letterModel.getAmountFinanced());
			letterModel.setDocumentationCharges(String.valueOf(processingFee));
		}

	}

	private void getCoApplicantNames(List<String> applicantNameList, LetterReportModel letterModel) {
		int size = applicantNameList.size();

		if(size>0 && Objects.nonNull(applicantNameList.get(0))) {
			letterModel.setCoApplicant1(applicantNameList.get(0));
		}
		if(size>1 && Objects.nonNull(applicantNameList.get(1))) {
			letterModel.setCoApplicant2(applicantNameList.get(1));
		}
		if(size>2 && Objects.nonNull(applicantNameList.get(2))) {
			letterModel.setCoApplicant3(applicantNameList.get(2));
		}

	}

	private String appendCustomerAddress(CustomerAddress customerAddress, String customerName) {
		String CustomerAddressString ="";
		if(Objects.nonNull(customerName)) {
			CustomerAddressString = customerName;
		}
		if(Objects.nonNull(customerAddress.getStreet())) {
			CustomerAddressString =  CustomerAddressString.isEmpty()?customerAddress.getStreet():CustomerAddressString+",<br>"+customerAddress.getStreet();
		}
		if(Objects.nonNull(customerAddress.getAddress1())) {
			CustomerAddressString = CustomerAddressString +",<br>"+customerAddress.getAddress1();
		}
		if(Objects.nonNull(customerAddress.getAddress7())) {
			CustomerAddressString = CustomerAddressString +",<br>"+customerAddress.getAddress7();
		}
		if(Objects.nonNull(customerAddress.getLocation())) {
			CustomerAddressString = CustomerAddressString +",<br>"+customerAddress.getLocation();
		}
		if(Objects.nonNull(customerAddress.getCity())&&(Objects.nonNull(customerAddress.getZipCode()))) {
			CustomerAddressString = CustomerAddressString +",<br>"+customerAddress.getCity()+"-"+customerAddress.getZipCode();
		}
		if(Objects.nonNull(customerAddress.getState())) {
			CustomerAddressString = CustomerAddressString +",<br>"+customerAddress.getState();
		}
		if(Objects.nonNull(customerAddress.getCountry())) {
			CustomerAddressString = CustomerAddressString +",<br>"+customerAddress.getCountry();
		}
		return CustomerAddressString;
	}

	private String appendCustomerName(ResultSet resultSet) throws SQLException {
		String customerName = "";
		if(Objects.nonNull(resultSet.getString(1))) {
			customerName = resultSet.getString(1);
		}
		if(Objects.nonNull(resultSet.getString(3))) {
			customerName = customerName + "."+resultSet.getString(3);
		}
		//		if(Objects.nonNull(resultSet.getString(3))) {
		//			if(Objects.isNull(resultSet.getString(2))) {
		//				customerName = customerName + "."+resultSet.getString(3);
		//			}else {
		//				customerName = customerName + " "+resultSet.getString(3);
		//			}
		//		}
		//		if(Objects.nonNull(resultSet.getString(4))) {
		//			if(Objects.isNull(resultSet.getString(2)) && Objects.isNull(resultSet.getString(3))) {
		//				customerName = customerName + "."+resultSet.getString(4);
		//			}else {
		//				customerName = customerName + " "+resultSet.getString(4);
		//			}
		//		}
		//		if(Objects.nonNull(resultSet.getString(5))) {
		//			if(Objects.isNull(resultSet.getString(2)) && Objects.isNull(resultSet.getString(3))&& Objects.isNull(resultSet.getString(4))) {
		//				customerName = customerName + "."+resultSet.getString(5);
		//			}else {
		//				customerName = customerName + " "+resultSet.getString(5);
		//			}
		//		}
		return customerName;
	}

	private String getConvertedPhoneNumber(String telePhoneNumber, String faxNumber) {
		String phoneNumber = "";
		if(Objects.nonNull(faxNumber)) {
			if(Objects.nonNull(telePhoneNumber)) {
				phoneNumber = telePhoneNumber+" "+"Fax"+"-"+faxNumber;
			}else {
				phoneNumber = "Fax" +"-"+faxNumber;
			}
		}else if(Objects.nonNull(telePhoneNumber)) {
			phoneNumber = "Tel" +'-' + telePhoneNumber;
		}
		return phoneNumber;
	}

	private String convertBranchAddress(BranchAddress branchAddress) {
		logger.info("convertBranchAddress method started");
		String brnachAddressString ="";
		if(Objects.nonNull(branchAddress.getStreet())) {
			brnachAddressString = brnachAddressString+branchAddress.getStreet();
		} if(Objects.nonNull(branchAddress.getAddress1())) {
			brnachAddressString = brnachAddressString+","+" "+branchAddress.getAddress1();
		} if(Objects.nonNull(branchAddress.getAddress6())) {
			brnachAddressString = brnachAddressString+","+" "+branchAddress.getAddress6();
		} if(Objects.nonNull(branchAddress.getLocationName())) {
			brnachAddressString = brnachAddressString+","+" "+branchAddress.getLocationName();
		} if(Objects.nonNull(branchAddress.getDistrictName())) {
			brnachAddressString = brnachAddressString+","+" "+branchAddress.getDistrictName();
		} if(Objects.nonNull(branchAddress.getPinCode())) {
			brnachAddressString = brnachAddressString+'-'+branchAddress.getPinCode();
		}
		logger.info("convertBranchAddress method ended");
		return brnachAddressString;
	}

	public List<String> getOracleApplicationNumber(List<LetterProduct> letterproductData) {
		// Your condition to switch to Oracle database
		List<String> applicationNumberList = new ArrayList<>();
		dynamicDataSourceService.switchToOracleDataSource();
		String query1="SELECT CONTRACT_NUMBER FROM CC_CONTRACT_MASTER WHERE CONTRACT_STATUS=1 AND CONTRACT_NUMBER IS NOT NULL ";
		//		query1 = "SELECT GENERATED_TRN FROM Hfs_File_Auto_Topup_Upload where GENERATED_TRN is not null";
		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		PreparedStatement preparedStatement = null;
		try  {
			connection = currentDataSource.getConnection();
			preparedStatement = connection.prepareStatement(query1);
			ResultSet resultSet = preparedStatement.executeQuery();
			// Process the result set
			while (resultSet.next()) {
				applicationNumberList.add(resultSet.getString(1));
			}

		} catch (SQLException e) {
			// Handle SQL exception
			e.printStackTrace();

		}finally {
			try {
				if(connection!=null) {
					connection.close();
				}
				if(preparedStatement!=null) {
					preparedStatement.close();
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return applicationNumberList;
	}

	public ResponseEntity<Map<String, Object>> fetchDataBasedOnDB(GenerateTemplateModel model) {
		String dataBase = "MSSQL";
		LetterProduct letterProduct = letterProductRepo.findByProductCodeAndLetterName(model.getProductCode(),
				model.getTemplateName());
		Map<String,Object> returnMap = new HashMap<>();
		List<LetterReportModel> letterModelList = new ArrayList<>();
		if(Objects.nonNull(letterProduct)) {
			dataBase = letterProduct.getDataBase();
			if(dataBase.equals("ORACLE")) {
				letterModelList = fetchDataForOracleDataBase(model);
			}else {
				letterModelList = fetchDataForMsSqlDataBase(model);
				logger.info("fetchDataForMsSqlDataBase method completed"+letterModelList);
			}
			if(letterModelList.isEmpty()) {
				returnMap.put("status", "No Related Data present");
			}else {
				returnMap.put("status", "Fetched Realted Data Successfully");
			}
			Blob blob;
			try{
				String jsonValue = objectMapper.writeValueAsString(letterModelList);
				logger.info("data converted"+jsonValue);
				letterProduct.setProductData(jsonValue);
				letterProductRepo.save(letterProduct);
				logger.info("data saved"+letterProduct);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		logger.info("output"+returnMap);
		return ResponseEntity.ok(returnMap);
	}




	private List<LetterReportModel> fetchDataForMsSqlDataBase(GenerateTemplateModel model) {
		logger.info("fetchDataForMsSqlDataBase method started");
		List<LetterReportModel> letterModelList = new ArrayList<>();
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");

		Map<String, Object> dataMap = new HashMap<>();
		List<Map<String, Object>> returnResponseList = new ArrayList<>();
		try {
			// Get Los Customer Data
			returnResponseList  = getcustomerDataFromLos(model);
			returnResponseList.stream().forEach(returnResponse->{
				logger.info("returnResponse iteration"+returnResponse);
				LetterReportModel letterModel = new LetterReportModel();
				letterModel.setApplicationNumber(getStringFromObject(returnResponse.get("applicationNum")));
				letterModel.setCustomerCode(getStringFromObject(returnResponse.get("customerId")));
				letterModel.setCustomerName(getStringFromObject(returnResponse.get("customerName")));
				letterModel.setCurrentDate(formatter.format(date));
				letterModel.setBranchCode(getStringFromObject(returnResponse.get("branchCode")));
				letterModel.setAmountFinanced(convertRoundedValue(getStringFromObject(returnResponse.get("loanAmt"))));
				letterModel.setTerm(Integer.parseInt(getStringFromObject(returnResponse.get("tenure"))));
				String netRate = convertDecimalValue(getStringFromObject(returnResponse.get("rateOfInterest")));
				letterModel.setNetRate(netRate);
				logger.info("letterModel fetched from los"+letterModel);
				BranchAddress branchAddress = fetchBranchAddressForMsSQL(letterModel.getBranchCode());
				String branchAddressString = convertBranchAddress(branchAddress);
				letterModel.setBranchAddress(branchAddressString);
				logger.info("convertBranchAddress method completed"+letterModel);
				//customer address
				String customerAddress = getCustomerAddress(Integer.parseInt(letterModel.getCustomerCode()), String.valueOf(letterModel.getCustomerName()));
				letterModel.setCustomerAddress(customerAddress);
				logger.info("setCustomerAddress method completed"+letterModel);
				//purpose of loan from los
				letterModel = getLosApplicationSQL(letterModel.getApplicationNumber(),letterModel);
				logger.info("getLosApplicationSQL method completed");
				dataMap.put("applicationNum",letterModel.getApplicationNumber());
				dataMap.put("type", "accrual");
				getAccountNo(letterModel);
				//get processingfee & documentation charges
				//getFeeDataForLetterGeneration(dataMap,letterModel);

				//				try {
				//					// Amort Calculation for Balance Payable
				//					Calendar calendar = Calendar.getInstance();
				//					Date currentDate = getDate(calendar.getTime());
				//					calendar.set(Calendar.DATE, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
				//					DateFormat dateFormatforReqDate = new SimpleDateFormat("MM/dd/yyyy");
				//					Date dates = new Date();
				//					String dateValue = dateFormatforReqDate.format(dates);
				//					Double balancePayable = 0.0;
				//					Date dueStartDate = getDate(calendar.getTime());
				//					dataMap.put("requestedDate", dateValue);
				//
				//					ResponseEntity<List<Amort>> amortDataResponse = webClient.post()
				//							.uri(stlapServerUrl + "/repayment/getAmortListResponse").bodyValue(dataMap)
				//							.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).retrieve().toEntityList(Amort.class)
				//							.block();
				//					if(Objects.nonNull(amortDataResponse)) {
				//						List<Amort> amortData = amortDataResponse.getBody();
				//						if(Objects.nonNull(amortData) && !amortData.isEmpty()) {
				//							balancePayable = amortData.stream().mapToDouble(Amort::getEmiDue).sum();
				//							letterModel.setBalancePayable(String.valueOf(balancePayable));
				//						}
				//					}
				//				}catch (Exception e) {
				//					e.printStackTrace();
				//				}

				//Cash Handling Charges Calculation
				//				 logger.info("cashHandlingResponse loop started");
				//				ResponseEntity<List<CashHandlingChargesModel>> cashHandlingResponse = webClient.get()
				//						.uri(stlapServerUrl + "/cashHandlingCharges/findByMaxEffectiveDate")
				//						.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).retrieve()
				//						.toEntityList(CashHandlingChargesModel.class).block();
				//				logger.info("CashHandlingChargesModel loop fetched"+cashHandlingResponse);
				//				List<CashHandlingChargesModel> cashHandlingChargesList = cashHandlingResponse.getBody();
				//				logger.info("cashHandlingChargesList loop fetched"+cashHandlingChargesList);
				//				letterModel.setCashHandlingCharges(cashHandlingChargesList);
				//				logger.info("all data"+letterModel);

				// Prepayment Charges Calculation
				//				dataMap.put("prepayment_reason", "PRE - OWN FUNDS");
				//				PrepaymentChargesModel prepaymentModel =  getDataFromPrepaymentCharges(dataMap);
				//				String prepaymentCharge = "";
				//				if(prepaymentModel!=null ) {
				//					prepaymentCharge = String.valueOf(Objects.nonNull(prepaymentModel.getRate())?prepaymentModel.getRate().intValue():0);
				//					letterModel.setProduct(prepaymentModel.getProduct());
				//				}
				//				letterModel.setPrePaymentCharges(prepaymentCharge);

				// ChequeReturnCharges Calculation
				//				dataMap.put("parameterName", "ChequeReturnCharges");
				//				Map<String, Object> parameterResponse = webClient.post().uri(stlapServerUrl + "/parameter/getParameterByName")
				//						.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).bodyValue(dataMap).retrieve()
				//						.bodyToMono(Map.class).block();
				//				String todayDate = formatter.format(currentDate);
				//				String chequeReturnCharges = (todayDate.compareTo(parameterResponse.get("paramEffStartDate").toString()) >= 0
				//						&& todayDate.compareTo(parameterResponse.get("paramEffEndDate").toString()) <= 0)
				//						? parameterResponse.get("paramValue").toString()
				//								: "0";
				//				letterModel.setChequeReturnCharges(chequeReturnCharges);
				letterModelList.add(letterModel);
				logger.info("all data"+letterModelList);
			});
		}catch(Exception e) {
			e.printStackTrace();
		}
		return letterModelList;
	}




	private void getAccountNo(LetterReportModel letterModel) {
		String query = "select bank_account_num from st_tb_los_bank_dtl where application_number=? and internal_customer_id=?";
		try (Connection connection = dataSource.getConnection()) {
			logger.info("getAccountNo method started");
			try (PreparedStatement statement = connection.prepareStatement(query)) {
				statement.setString(1, letterModel.getApplicationNumber());
				statement.setString(2, letterModel.getCustomerCode());
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						letterModel.setAccountNo(resultSet.getString(1));
						logger.info("getAccountNo method completed");
					}
				}catch (SQLException e) {
					e.printStackTrace();
				}
			}catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private List<Map<String, Object>> getcustomerDataFromLos(GenerateTemplateModel model) throws ParseException {
		logger.info("getcustomerDataFromLos method started");
		List<Map<String, Object>> returnResponseList = new ArrayList<>();
		String sql = "";
		String value = "";
		if(Objects.nonNull(model.getApplicationNumber()) && !(model.getApplicationNumber().isEmpty())) {
			sql = "SELECT application_num,customer_id,customer_name,branch_code,loan_amt,sanction_amt,tenure,rate_of_interest FROM ST_TB_LOS_CUSTOMER WHERE application_num = ?";
			value = model.getApplicationNumber();
		}else if(model.getSanctionDate()!=null){
			sql = "SELECT application_num,customer_id,customer_name,branch_code,loan_amt,sanction_amt,tenure,rate_of_interest FROM ST_TB_LOS_CUSTOMER WHERE effective_date = ?";
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
			LocalDate date = LocalDate.parse(model.getSanctionDate(), inputFormatter);
			LocalDateTime dateTime = date.atStartOfDay();
			DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS");
			value =  dateTime.format(outputFormatter);
		}
		if(sql.isEmpty()) {
			return returnResponseList;
		}

		try (Connection connection = dataSource.getConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.setString(1, value);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						logger.info("getcustomerDataFromLos query method started"+resultSet);
						Map<String, Object> responseMap = new HashMap<>();
						responseMap.put("applicationNum", resultSet.getString(1));
						responseMap.put("customerId", resultSet.getString(2));
						responseMap.put("customerName", resultSet.getString(3));
						responseMap.put("branchCode", resultSet.getString(4));
						responseMap.put("loanAmt", resultSet.getFloat(5));
						responseMap.put("sanctionAmt", resultSet.getFloat(6));
						responseMap.put("tenure", resultSet.getInt(7));
						responseMap.put("rateOfInterest", resultSet.getFloat(8));
						returnResponseList.add(responseMap);
						logger.info("getcustomerDataFromLos query method ended"+returnResponseList);
					}
				}catch (SQLException e) {
					e.printStackTrace();
				}
			}catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return returnResponseList;
	}

	private List<String> getLosApplicationNumber(String applicationNumber) {
		List<String> responseMapList = new ArrayList<>();
		String query = "SELECT application_num FROM ST_TB_LOS_CUSTOMER WHERE los_status = ?";
		try (Connection connection = dataSource.getConnection()) {
			try (PreparedStatement statement = connection.prepareStatement(query)) {
				statement.setString(1, applicationNumber);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						responseMapList.add(resultSet.getString(1));
					}
				}catch (SQLException e) {
					e.printStackTrace();
				}
			}catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return responseMapList;
	}

	private String convertDecimalValue(String value) {
		DecimalFormat format = new DecimalFormat("0.00");
		if(Objects.nonNull(value)) {
			String outputValue = format.format(Double.parseDouble(value));
			return outputValue;
		}else {
			return "0.00";
		}
	}

	private LetterReportModel getLosApplicationSQL(String applicationNumber, LetterReportModel letterModel) {
		logger.info("getLosApplicationSQL method started");
		try (Connection connection = dataSource.getConnection()) {
			String query = "SELECT purpose_of_loan,emi_amount FROM st_tb_los_application_information WHERE application_number = ?";

			try (PreparedStatement statement = connection.prepareStatement(query)) {
				statement.setString(1, applicationNumber);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						logger.info("getLosApplicationSQL query started");
						letterModel.setPurposeOfLoan(resultSet.getString(1));
						letterModel.setEmiAmount(convertRoundedValue(resultSet.getString(2)));
					}
				}catch (SQLException e) {
					e.printStackTrace();
				}
			}catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		logger.info("getLosApplicationSQL method ended"+letterModel);
		return letterModel;
	}
	public int convertRoundedValue(String value) {
		if(Objects.nonNull(value)) {
			return (int)Math.round(Double.parseDouble(value));
		}
		return 0;
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

	public void insertProductData() {
		List<String> productList = new ArrayList<>();
		productList.add("HOMEFIN");
		productList.add("STLAP");
		List<LetterProduct> entityList = letterProductRepo.findByProductCodeIn(productList);
		if(entityList.isEmpty()) {
			LetterProduct entity1 = new LetterProduct(1,"HOMEFIN",null,"ORACLE",null);
			LetterProduct entity2 = new LetterProduct(2,"HOMEFIN",null,"ORACLE",null);
			LetterProduct entity3 = new LetterProduct(3,"STLAP",null,"MSSQL",null);
			entityList.add(entity3);
			entityList.add(entity2);
			entityList.add(entity1);
			letterProductRepo.saveAll(entityList);
		}
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
