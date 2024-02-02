package com.letter.report.dynamicReportTemplate.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Sort;
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
import com.letter.report.dynamicReportTemplate.letterModel.LinkedSroDetails;
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
	private int scheduleANo = 0;
	private int scheduleBNo = 0;


	@Value("${stlap.server.url}")
	private String stlapServerUrl;

	@Value("${mail.server.url}")
	private String mailServerUrl;

	public ResponseEntity<String> saveTemplate(DynamicTemplateModel dynamicTemplateModel) {
		//String decodedContent = "";
		//		try {
		//			//decodedContent = URLDecoder.decode(dynamicTemplateModel.getContent(), "UTF-8");
		//		} catch (UnsupportedEncodingException e) {
		//			logger.error("failed to parse",e.getMessage());
		//			e.printStackTrace();
		//		}
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
		Sort sort = Sort.by("templateKey");
		List<DynamicTemplate> dynamicTemplateList = dynamicTemplateRepo.findByProductCodeAndTemplateName(dataMap.get("productCode"), dataMap.get("templateName"),sort);
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
		Connection connection = null;
		try {
			connection = dataSource.getConnection();
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
		AtomicInteger processingFee = new AtomicInteger(0);
		AtomicInteger documentationCharges = new AtomicInteger(0);
		AtomicInteger lifeInsuranceChrages = new AtomicInteger(0);
		AtomicInteger adminFee = new AtomicInteger(0);
		AtomicInteger totalFee = new AtomicInteger(0);
		int balancePayable=0;
		try {
			logger.info("processingfee loop starts");
			memorandumSavedData.stream().forEach(action -> {
				if(Objects.nonNull(action)) {
					logger.info("memorandumSavedData loop "+action);
					if (Objects.nonNull(action.getMemoCode()) && action.getMemoCode().equalsIgnoreCase("PROCESSING FEE")) {
						if (Objects.nonNull(action.getTxnIndicator()) && action.getTxnIndicator().equals("accrual")) {
							logger.info("processingFee loop "+action);
							processingFee.set(processingFee.get() + (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
						} else {
							logger.info("processingFee loop "+action);
							processingFee.set(processingFee.get() - (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
						}
					} 
				}
			});
		}catch (Exception e) {
			logger.info("processingfee loop fails",e);
			e.printStackTrace();
		}
		logger.info("processingFee loop completed"+String.valueOf(processingFee.get()));
		try {
			logger.info("documentationCharges loop starts");
			memorandumSavedData.stream().forEach(action -> {
				if(Objects.nonNull(action)) {
					if (Objects.nonNull(action.getMemoCode()) && action.getMemoCode().equalsIgnoreCase("DOCUMENTATION CHARGES")
							|| action.getMemoCode().equalsIgnoreCase("DOCUMENTATION FEE")) {
						if (Objects.nonNull(action.getTxnIndicator()) && action.getTxnIndicator().equals("accrual")) {
							documentationCharges.set(documentationCharges.get() + (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
						} else {
							documentationCharges.set(documentationCharges.get() - (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
						}
					}
				}
			});
		}catch (Exception e) {
			logger.info("documentationCharges loop fails",e);
			e.printStackTrace();
		}
		logger.info("documentationCharges loop completed"+String.valueOf(documentationCharges.get()));
		try {
			memorandumSavedData.stream().forEach(action -> {
				logger.info("lifeInsuranceChrages loop starte");
				if(Objects.nonNull(action)) {
					if (Objects.nonNull(action.getMemoCode()) && action.getMemoCode().equalsIgnoreCase("LIFE INSURANCE")
							|| action.getMemoCode().equalsIgnoreCase("PROPERTY INSURANCE")) {
						if ( Objects.nonNull(action.getTxnIndicator()) && action.getTxnIndicator().equals("accrual")) {
							logger.info("lifeInsuranceChrages loop added");
							lifeInsuranceChrages.set(lifeInsuranceChrages.get() + (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
						} else {
							logger.info("lifeInsuranceChrages loop minus");
							lifeInsuranceChrages.set(lifeInsuranceChrages.get() - (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
						}
					}
				}
			});
		}catch (Exception e) {
			logger.info("lifeInsuranceChrages loop failed",e);
			e.printStackTrace();
		}
		logger.info("lifeInsuranceChrages loop completed"+String.valueOf(lifeInsuranceChrages.get()));
		try {
			logger.info("adminfee loop starts");
			memorandumSavedData.stream().forEach(action -> {
				if(Objects.nonNull(action)) {
					if (Objects.nonNull(action.getMemoCode()) && action.getMemoCode().equalsIgnoreCase("ADMINISTRATION CHARGES")
							|| action.getMemoCode().equalsIgnoreCase("ADMINISTRATION FEE")|| action.getMemoCode().equalsIgnoreCase("ADMINISTRATION FEES GST")) {
						if (Objects.nonNull(action.getTxnIndicator()) && action.getTxnIndicator().equals("accrual")) {
							adminFee.set(documentationCharges.get() + (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
						} else {
							adminFee.set(documentationCharges.get() - (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
						}
					}
				}
			});
		}catch (Exception e) {
			logger.info("documentationCharges loop fails",e);
			e.printStackTrace();
		}
		try {
			logger.info("balance payable loop starts");
			memorandumSavedData.stream().forEach(action -> {
				if(Objects.nonNull(action)) {
					if (Objects.nonNull(action.getTxnIndicator()) && action.getTxnIndicator().equals("accrual")) {
						logger.info("processingFee loop "+action);
						totalFee.set(totalFee.get() + (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
					} else {
						logger.info("processingFee loop "+action);
						totalFee.set(totalFee.get() - (Objects.nonNull(action.getTxnAmt())?action.getTxnAmt():0));
					}
				}
			});
			logger.info("totalFee completed"+totalFee);
			balancePayable = totalFee.get()-processingFee.get();
			logger.info("balancePayable completed"+balancePayable);
		}catch (Exception e) {
			logger.info("processingfee loop fails",e);
			e.printStackTrace();
		}
		 
		letterModel.setDocumentationCharges(getNilValues(documentationCharges.get()));
		letterModel.setProcessingFee(getNilValues(processingFee.get()));
		letterModel.setLifeInsurance(getNilValues(lifeInsuranceChrages.get()));
		letterModel.setAdminFee(getNilValues(adminFee.get()));
		letterModel.setBalancePayable(getNilValues(balancePayable));
		logger.info("process completed"+letterModel);
	}

	private String getNilValues(int value) {
		if(value!=0) {
			return String.valueOf(value);
		}else {
			return "NIL";
		}
	}

	private PrepaymentChargesModel getDataFromPrepaymentCharges(Map<String, Object> dataMap) {
		PrepaymentChargesModel chargesResponse = new PrepaymentChargesModel();
		AtomicInteger rowId = new AtomicInteger(0);
		Connection connection = null;
		try {
			connection = dataSource.getConnection();
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
		return chargesResponse;
	}

	private String getString(String name) {
		return Objects.nonNull(name) ? name : "";
	}
	private String getStringFromModel(Object proeprtyAddress, String name) {
		if(Objects.nonNull(proeprtyAddress)) {
			return Objects.nonNull(name) ? name : "&nbsp;";
		}else {
			return "&nbsp;";
		}
	}
	private String getUnknownValueFromObject(Object name) {
		return Objects.nonNull(name) ? ("<b>"+"<u>"+String.valueOf(name)+"</u>"+"</b>") : "___________";
	}
	private String getUnknownValueForSRO(Object name) {
		return Objects.nonNull(name) ? String.valueOf(name) : "___________";
	}
	private String getStringFromObject(Object name) {
		return Objects.nonNull(name) ? String.valueOf(name) : "";
	}

	public String getCustomerAddress(int customerId, String applicantName) {
		logger.info("getCustomerAddress method completed"+applicantName);
		StringBuilder addressBuilder = new StringBuilder();
		Connection connection =null;
		try  {
			connection = dataSource.getConnection();
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
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
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
				,"//~~Schedule_B_Detail~~//","//~~Boundries_Detail~~//","//~~Measurement_Detail~~//","//~~MOTD_Term~~//",
				"//~~Supplement_MOTD_Title_Holder_Detail~~//","//~~Linked_Motd_Date~~//","//~~Linked_Motd_No~~//","//~~Linked_Motd_Sro~~//","//~~linked_motd_sro_district~~//","//~~Linked_Motd_Reg_Doc_Date~~//"
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
			//String decodedContent = URLDecoder.decode(dynamicTemplate.getContent(), "UTF-8");
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
		variablesValueMap.put("~~Product~~", "STLAP");
		variablesValueMap.put("~~Purpose_of_Loan~~", nullCheckStringField(sanctionModel.getPurposeOfLoan()));
		variablesValueMap.put("~~Term~~", nullCheckStringField(sanctionModel.getTerm()));
		variablesValueMap.put("~~ROI~~", nullCheckStringField(sanctionModel.getNetRate()));
		variablesValueMap.put("~~EMI~~", nullCheckStringField(sanctionModel.getEmiAmount()));
		variablesValueMap.put("~~Upfront_Processing_Fee~~", nullCheckStringField(sanctionModel.getProcessingFee())); 
		variablesValueMap.put("~~Balance_Payable~~", nullCheckStringField(sanctionModel.getBalancePayable()));
		variablesValueMap.put("~~Documentation_Charges~~", String.valueOf(sanctionModel.getDocumentationCharges()));
		variablesValueMap.put("~~CERSAI_Charges~~", "100");
		variablesValueMap.put("~~Appraisal_Charges~~", "________"); //
		variablesValueMap.put("~~Switch_Fee~~", "________");  //Not Applicable
		variablesValueMap.put("~~Retrieval_Charges~~", "________");
		variablesValueMap.put("~~Document_Retrieval_Charges~~", "________");
		variablesValueMap.put("~~Conversion_Charges~~", "________");
		variablesValueMap.put("~~Cheque_Return_Charges~~",nullCheckStringField(sanctionModel.getChequeReturnCharges()));
		variablesValueMap.put("~~GST_Tamilnadu~~", "1200");  
		variablesValueMap.put("~~GST_Andra~~", "1500"); 
		variablesValueMap.put("~~GST_Karnataka~~","1550"); 
		variablesValueMap.put("~~GST_Others~~", "2500"); 
		variablesValueMap.put("~~Repricing_Fee~~",  "________"); //Not Applicable
		variablesValueMap.put("~~CA_Certification_Fee~~", "10000"); //
		variablesValueMap.put("~~Outstation_Cheque_Charges~~", "4"); 
		variablesValueMap.put("~~Outstation_Cheque_Charges_Total~~","1000"); 
		variablesValueMap.put("~~PDC_Charges~~", "300"); 
		variablesValueMap.put("~~Swapping_Charges~~","500"); 
		variablesValueMap.put("~~Travelling_Expense~~", "200"); 
		variablesValueMap.put("~~Bureau_Charges_Individual_Customer~~","49"); 
		variablesValueMap.put("~~Bureau_Charges_Non_Individual_Customer~~","335"); 
		variablesValueMap.put("~~Prepayment_Charges~~", nullCheckStringField(sanctionModel.getPrePaymentCharges()));
		variablesValueMap.put("~~Penal_Interest~~","________"); //Not Applicable
		variablesValueMap.put("~~Cheque_Dishonour_Charges~~", "________"); //Not Applicable
		variablesValueMap.put("~~Life_Insurance~~", Objects.nonNull(sanctionModel.getLifeInsurance())?sanctionModel.getLifeInsurance():"0"); 
		variablesValueMap.put("~~Moratorium_Period~~", nullCheckStringField(sanctionModel.getMoratoriumPeriod())); 
		variablesValueMap.put("~~Applicant~~", nullCheckStringField(sanctionModel.getApplicant()));
		variablesValueMap.put("~~Admin_Fee~~", nullCheckStringField(sanctionModel.getAdminFee()));
		variablesValueMap.put("~~Co-Applicant 1~~", nullCheckStringField(sanctionModel.getCoApplicant1()));
		variablesValueMap.put("~~Co-Applicant 2~~", nullCheckStringField(sanctionModel.getCoApplicant2()));

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
			variablesValueMap.put("~~Cash_Handling_Charges_Table~~", nullCheckStringField(cashHandlingChargesTables.toString()));
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
		variablesValueMap.put("~~Admin_Fee~~", nullCheckStringField(sanctionModel.getAdminFee()));
		variablesValueMap.put("~~Co-Applicant 1~~", nullCheckStringField(sanctionModel.getCoApplicant1()));
		variablesValueMap.put("~~Co-Applicant 2~~", nullCheckStringField(sanctionModel.getCoApplicant2()));
		variablesValueMap.put("~~Product~~", nullCheckStringField(sanctionModel.getProduct()));
		variablesValueMap.put("~~Balance_Payable~~", nullCheckStringField(sanctionModel.getBalancePayable()));
		variablesValueMap.put("~~Documentation_Charges~~", nullCheckStringField(sanctionModel.getDocumentationCharges()));
		variablesValueMap.put("~~CERSAI_Charges~~", "100");
		variablesValueMap.put("~~Appraisal_Charges~~", nullCheckStringField(null)); //
		variablesValueMap.put("~~Switch_Fee~~", "________");  //Not Applicable
		variablesValueMap.put("~~Retrieval_Charges~~", "________");
		variablesValueMap.put("~~Conversion_Charges~~", "________");
		variablesValueMap.put("~~Document_Retrieval_Charges~~", "________");
		variablesValueMap.put("~~Cheque_Return_Charges~~", nullCheckStringField(sanctionModel.getChequeReturnCharges()));
		variablesValueMap.put("~~GST_Tamilnadu~~", "1200");  
		variablesValueMap.put("~~GST_Andra~~", "1500"); 
		variablesValueMap.put("~~GST_Karnataka~~","1550"); 
		variablesValueMap.put("~~GST_Others~~", "2500"); 
		variablesValueMap.put("~~Repricing_Fee~~",  "________"); //Not Applicable
		variablesValueMap.put("~~CA_Certification_Fee~~", "10000"); //
		variablesValueMap.put("~~Outstation_Cheque_Charges~~", "4"); 
		variablesValueMap.put("~~Outstation_Cheque_Charges_Total~~","1000"); 
		variablesValueMap.put("~~PDC_Charges~~", "300"); 
		variablesValueMap.put("~~Swapping_Charges~~","500"); 
		variablesValueMap.put("~~Travelling_Expense~~", "200"); 
		variablesValueMap.put("~~Bureau_Charges_Individual_Customer~~","49"); 
		variablesValueMap.put("~~Bureau_Charges_Non_Individual_Customer~~","335"); 
		variablesValueMap.put("~~Prepayment_Charges~~", nullCheckStringField(sanctionModel.getPrePaymentCharges()));
		variablesValueMap.put("~~Penal_Interest~~","________"); //Not Applicable
		variablesValueMap.put("~~Cheque_Dishonour_Charges~~", "________"); //Not Applicable
		variablesValueMap.put("~~Life_Insurance~~", nullCheckStringField(sanctionModel.getLifeInsurance())); 
		variablesValueMap.put("~~Moratorium_Period~~", nullCheckStringField(sanctionModel.getMoratoriumPeriod())); 
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
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		if(Objects.nonNull(propertyDetailModel)) {
			Set<TitleHolderDetail> titleHolderDetailList = propertyDetailModel.getTitleHolderDetailList();
			Map<String, LinkedHashSet<ScheduleA>> scheduleAListMap = propertyDetailModel.getScheduleListMap();
			Map<String, ScheduleB> scheduleBMap = propertyDetailModel.getScheduleBListMap();
			Map<String, Boundries> boundriesMap = propertyDetailModel.getBoundriesListMap();
			Map<String, Measurement> measurementMap = propertyDetailModel.getMeasurementListMap();
			serialNo = 1;
			scheduleANo = 0;
			scheduleBNo = 0;
			if(Objects.nonNull(titleHolderDetailList)) {
				titleHolderDetailList.stream().forEach(titleHolderDetail->{
					String combinationKey ="";
					if(Objects.nonNull(titleHolderDetail) && Objects.nonNull(titleHolderDetail.getPropertyNumber())&& Objects.nonNull(titleHolderDetail.getCustomerShareCode())) {
						combinationKey = titleHolderDetail.getPropertyNumber()+"-"+titleHolderDetail.getCustomerShareCode();
					}
					String titleHolderName = "";
					if(StringUtils.isEmpty(getString(titleHolderDetail.getTitle()))) {
						titleHolderName = "___"+getUnknownValueFromObject(titleHolderDetail.getTitleHolderName());
					}else {						
						titleHolderName = "<b>"+"<u>"+titleHolderDetail.getTitle()+"."+"</u>"+"</b>"+getUnknownValueFromObject(titleHolderDetail.getTitleHolderName());
					}
					int a = serialNo++;
					String titleHolderPrefix ="";
					long titleSize = getSizeOfTitleMap(titleHolderDetailList);
					if(titleSize>1) {
						titleHolderPrefix = a+"."+titleHolderName;
					}else {
						titleHolderPrefix = titleHolderName;
					}
					String aadharNo = "____________";
					//smtitleholder
					StringBuilder firstMortagetitleHolderDetail =new StringBuilder(titleHolderPrefix+", Aadhaar No. "+ aadharNo
							+"aged about "+ getUnknownValueFromObject(titleHolderDetail.getAge())+" years,"
							+ " S/o.W/o.Mr/s "+getUnknownValueFromObject(titleHolderDetail.getTitleHolderGuardianName())
							+",residing at "+getUnknownValueFromObject(titleHolderDetail.getTitleHolderAddress())
							+" referred to as the MORTGAGORS ”,the PARTY OF THE FIRST PART."
							+ "(Which expression shall unless excluded by or repugnant to the context be deemed "
							+ "to include his / her / their successor and assigns)."
							+"<br>"+"<br>");

					firstMortagetitleHolderDetailList.add(firstMortagetitleHolderDetail.toString());
					//smotd name
					StringBuilder firstMortagetitleNameDetail =new StringBuilder("WHEREAS the first mortgagor of "
							+titleHolderName
							+ " herein is the sole and absolute owner of the property by the following document ("
							+getUnknownValueForSRO(titleHolderDetail.getOtdNumber())+") on the file of "
							+"("+getSROValue(scheduleBMap,combinationKey)+" ). "
							+"<br>"+"<br>");
					firstMortagetitleNameDetailList.add(firstMortagetitleNameDetail.toString());

					//motd tileholder
					StringBuilder motdTitleHolderBuilder =new StringBuilder(titleHolderPrefix+",Aadhaar No."+aadharNo
							+" aged about "+getUnknownValueFromObject(titleHolderDetail.getAge())+" years,"
							+ " S/o.W/o.Mr/s "+getUnknownValueFromObject(titleHolderDetail.getTitleHolderGuardianName())
							+",residing at "+getUnknownValueFromObject(titleHolderDetail.getTitleHolderAddress())
							+" referred to as the BORROWER/S”, the PARTY OF THE FIRST PART."
							+ "(Which expression shall unless excluded by or repugnant to the context be deemed "
							+ "to include his / her / their successor and assigns)."
							+"<br>"+"<br>");

					motdTitleHolderList.add(motdTitleHolderBuilder.toString());


					//supplement motd title

					String titleHolder = titleHolderPrefix+" ,S/o.W/o.Mr/s of "+
							getUnknownValueFromObject(titleHolderDetail.getTitleHolderGuardianName())+" ,aged about "
							+getUnknownValueFromObject(titleHolderDetail.getAge())+" years,"
							+" residing at "+getUnknownValueFromObject(titleHolderDetail.getTitleHolderAddress());
					String valueCondition = ""+","+"<br>"+"<br>"+titleHolder;
					StringBuilder supplmentMotdTitleHolderBuilder =new StringBuilder(a!=1?valueCondition:titleHolder);

					supplementMotdTitleHolderList.add(supplmentMotdTitleHolderBuilder.toString());


					//scheduleA
					if(Objects.nonNull(scheduleAListMap)) {
						Set<ScheduleA> scheduleAList = scheduleAListMap.get(combinationKey);
						long size = getSizeOfScheduleAMap(scheduleAListMap);
						if(Objects.nonNull(scheduleAList)&&!scheduleAList.isEmpty()) {
							scheduleANo++;
							int scheduleAIndex = getIndexValue(scheduleAListMap,combinationKey);
							StringBuilder scheduleATable = new StringBuilder();
							scheduleATable.append("<b>");
							scheduleATable.append("<u>");
							if(size>1) {
								scheduleATable.append("Document details for item No."+scheduleANo+ " of Schedule -A");
							}else {
								scheduleATable.append("Document details for Schedule -A");
							}
							scheduleATable.append("</u>");
							scheduleATable.append("</b>");
							scheduleATable.append("<br>");
							scheduleATable.append("<table class=\\\"MsoNormalTable\\\" style=\\\"table-layout:fixed; margin-left: 20.25pt; border-collapse: collapse; mso-table-layout-alt: fixed; table-layout:fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\"><tbody>"
									+ "<tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\">"
									+ "<td style=\\\"width: 150pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document Name</td>"
									+ "<td style=\\\"width: 150pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document No</td>"
									+ "<td style=\\\"width: 150.0pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document Date</td>"
									+ "<td style=\\\"width: 150.0pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Title Holder</td></tr>");
							scheduleAList.stream().forEach(scheduleA -> {
								String value = getStringFromObject(scheduleA.getDocumentDate());
								String outputVlaue = "";
								if(!value.isEmpty()) {
									LocalDateTime ds = LocalDateTime.parse(value,inputFormatter);
									outputVlaue = ds.format(outputFormatter);
								}
								scheduleATable.append(
										"<tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 250.0pt; border: 1pt solid black;  padding:0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"250\\\"> ");
								scheduleATable
								.append(getStringFromObject(scheduleA.getDocumentName()));
								scheduleATable.append(
										"</td><td style=\\\"width: 100.0pt;border: 1pt solid black;  padding:0in; height: 12.5pt;text-align: center;\\\" valign=\\\"top\\\" width=\\\"100\\\"> ");
								scheduleATable.append(getStringFromObject(scheduleA.getDocuemntNumber()));
								scheduleATable.append(
										"</td><td style=\\\"width: 100.0pt;border: 1pt solid black;  padding:0in; height: 12.5pt;text-align: center;\\\" valign=\\\"top\\\" width=\\\"100\\\"> ");
								scheduleATable.append(outputVlaue);
								scheduleATable.append(
										"</td><td style=\\\"width: 150.0pt; border: 1pt solid black;  padding:0in; height: 12.5pt;text-align: center;\\\" valign=\\\"top\\\" width=\\\"150\\\"> ");
								scheduleATable.append(getStringFromObject(scheduleA.getTitleHolderName()));
								scheduleATable.append("</td></tr>");
							});
							scheduleATable.append("</tbody></table>");
							scheduleATable.append("<br>");
							scheduleATableList.add(scheduleATable.toString());
						}else {
							if(size==0) {
								StringBuilder scheduleATable = new StringBuilder();
								scheduleATable.append("Document details for Schedule -A");
								scheduleATable.append("<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 20.25pt; border-collapse: collapse; mso-table-layout-alt: fixed;table-layout:fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\"><tbody>"
										+ "<tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\">"
										+ "<td style=\\\"width: 150pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document Name</td>"
										+ "<td style=\\\"width: 150pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document No</td>"
										+ "<td style=\\\"width: 150.0pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Document Date</td>"
										+ "<td style=\\\"width: 150.0pt; border: 1pt solid black;  padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Title Holder</td></tr>");
								scheduleATable.append("</tbody></table>");
								scheduleATable.append("<br>");
								scheduleATableList.add(scheduleATable.toString());
							}
						}
					}

					//scheduleB
					if(Objects.nonNull(scheduleBMap)) {
						ScheduleB scheduleB = scheduleBMap.get(combinationKey);
						long size = getSizeOfScheduleBMap(scheduleBMap);
						StringBuilder scheduleBBuilder = new StringBuilder();
						if(Objects.nonNull(scheduleB)) {
							scheduleBNo++;
							PropertyAddress proeprtyAddress = scheduleB.getPropertyAddress();
							String addSurveyNo ="";
							if(!getString(scheduleB.getAddlSurveyNo()).isEmpty()) {
								addSurveyNo = " And "+getString(scheduleB.getAddlSurveyNo());
							}
							String scheduleString =getScheduleBBuilder(scheduleB,proeprtyAddress,size,scheduleBBuilder);
							scheduleBList.add(scheduleString);
						}else {
							if(size==0) {
								ScheduleB scheduleB1 = new ScheduleB();
								PropertyAddress propertyAddress = new PropertyAddress();
								scheduleB1.setPropertyAddress(propertyAddress);
								String scheduleString =getScheduleBBuilder(scheduleB1,propertyAddress,size,scheduleBBuilder);
								scheduleBList.add(scheduleString);
							}
						}
					}

					//boundries
					if(Objects.nonNull(boundriesMap)) {
						long size = getSizeOfBoundriesMap(boundriesMap);
						Boundries boundries = boundriesMap.get(combinationKey);
						StringBuilder boundroesBuilder = new StringBuilder("");
						boundroesBuilder.append("<b>");
						boundroesBuilder.append("<u>");
						boundroesBuilder.append("Boundaries");
						boundroesBuilder.append("</u>");
						boundroesBuilder.append("</b>");
						if(Objects.nonNull(boundries)) {
							String boundryString = getBoundriesBuilder(boundroesBuilder,boundries);
							boundriesList.add(boundryString);
						}else {
							if(size==0) {
								boundries = new Boundries();
								String boundryString = getBoundriesBuilder(boundroesBuilder,boundries);
								boundriesList.add(boundryString);
							}
						}
					}

					//measurement
					if(Objects.nonNull(measurementMap)) {
						Measurement measurerments = measurementMap.get(combinationKey);
						long size = getSizeOfMeasurementMap(measurementMap);
						StringBuilder measurementBuilder = new StringBuilder("");
						measurementBuilder.append("<b>");
						measurementBuilder.append("<u>");
						measurementBuilder.append("Measurement");
						measurementBuilder.append("</u>");
						measurementBuilder.append("</b>");
						if(Objects.nonNull(measurerments)) {
							String measurementString = getMeasurermentBuilder(measurementBuilder,measurerments);
							measurementList.add(measurementString);
						}else {
							if(size==0) {
								measurerments = new Measurement();
								String measurementString = getMeasurermentBuilder(measurementBuilder,measurerments);
								measurementList.add(measurementString);
							}
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
		variablesValueMap.put("~~MOTD_Term~~", ("<b>"+"<u>"+getYearsFromMonth(sanctionModel.getTerm())+"</u>"+"</b>")); //
		//sro detail
		LinkedSroDetails sroDetail = sanctionModel.getLinkedSroDetails();
		String motdDate = getDateWithoutTiming(Objects.nonNull(sroDetail)?sroDetail.getLinkedDocumentDate():null);
		String motdRegDate = getDateWithChangeFormat(sanctionModel.getCurrentDate());
		variablesValueMap.put("~~Linked_Motd_Date~~", (getUnknownValueFromObject(motdDate)));
		variablesValueMap.put("~~Linked_Motd_Reg_Doc_Date~~", (getUnknownValueFromObject(motdRegDate)));
		variablesValueMap.put("~~Linked_Motd_No~~", (getUnknownValueFromObject(Objects.nonNull(sroDetail)?sroDetail.getLinkedDocumentNumber():null)));
		variablesValueMap.put("~~Linked_Motd_Sro~~", (getUnknownValueFromObject(Objects.nonNull(sroDetail)?sroDetail.getLinkedSro():null)));
		variablesValueMap.put("~~linked_motd_sro_district~~", (getUnknownValueFromObject(Objects.nonNull(sroDetail)?sroDetail.getLinkedSroDistrict():null)));
		variablesValueMap.put("~~MOTD_SRO~~", (getUnknownValueFromObject(Objects.nonNull(sroDetail)?sroDetail.getLinkedSro():null))); //
		//day and month
		splitDayFromDate(sanctionModel,variablesValueMap);

		StringBuilder loanDetailsTable = new StringBuilder(
				"<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 55.25pt; border-collapse: collapse; mso-table-layout-alt: fixed;table-layout:fixed; border: none; mso-border-alt: solid black .5pt; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in; mso-border-insideh: .5pt solid black; mso-border-insidev: .5pt solid black;\\\" border=\\\"1\\\" cellspacing=\\\"0\\\" cellpadding=\\\"0\\\"><tbody>"
						+ "<tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt; text-align: center;\\\">"
						+ "<td style=\\\"width: 150pt; border: 1pt solid black; padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">File Number</td>"
						+ "<td style=\\\"width: 150pt; border: 1pt solid black; padding: 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Loan Amount (in Rs.)</td>"
						+ "<td style=\\\"width: 150pt; border:1pt solid black; padding:0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Rate (in %)</td>"
						+ "<td style=\\\"width: 150pt; border:1pt solid black; padding:0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Type</td>"
						+ "<td style=\\\"width: 150pt; border:1pt solid black; padding:0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\">Tenor</td></tr>");
		loanDetailsTable.append(
				"<tr style=\\\"mso-yfti-irow: 2; height: 12.5pt;\\\"><td style=\\\"width: 150.0pt; border: solid black 1.0pt; border-top: none; mso-border-top-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable
		.append(sanctionModel.getContractNumber());
		loanDetailsTable.append(
				"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable.append(sanctionModel.getAmountFinanced());
		loanDetailsTable.append(
				"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable.append(sanctionModel.getNetRate());
		loanDetailsTable.append(
				"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable.append(sanctionModel.getRateTypeString());
		loanDetailsTable.append(
				"</td><td style=\\\"width: 150.0pt; border-top: none; border-left: none; border-bottom: solid black 1.0pt; border-right: solid black 1.0pt; mso-border-top-alt: solid black .5pt; mso-border-left-alt: solid black .5pt; mso-border-alt: solid black .5pt; padding: 0in 0in 0in 0in; height: 12.5pt; text-align: center;\\\" valign=\\\"top\\\" width=\\\"200\\\"> ");
		loanDetailsTable.append(sanctionModel.getTerm());
		loanDetailsTable.append("</td></tr>");
		loanDetailsTable.append("</tbody></table>");
		variablesValueMap.put("~~MOTD_Loan_details_table~~", loanDetailsTable.toString());

	}

	private long getSizeOfMeasurementMap(Map<String, Measurement> measurementMap) {
		long nonNullValueCount = measurementMap.entrySet()
				.stream()
				.filter(entry -> entry.getValue() != null)
				.count();
		return nonNullValueCount;
	}
	private long getSizeOfBoundriesMap(Map<String, Boundries> boundriesMap) {
		long nonNullValueCount = boundriesMap.entrySet()
				.stream()
				.filter(entry -> entry.getValue() != null)
				.count();
		return nonNullValueCount;
	}

	private String getBoundriesBuilder(StringBuilder boundroesBuilder, Boundries boundries) {
		boundroesBuilder.append("<html>\n<body>\n");

		// Left-aligned content
		boundroesBuilder.append("<div style=\"float: left; width: 40%;\">");
		boundroesBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">North By</p>");
		boundroesBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">South By</p>");
		boundroesBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">East By</p>");
		boundroesBuilder.append("<p style=\"display: inline; margin-top:0;margin-left:0;margin-right:0;margin-bottom: 10px;padding: 0;\">West By</p>");
		boundroesBuilder.append("</div>");

		//right alinged content
		boundroesBuilder.append("<div style=\"float: right; width: 60%;\">");
		boundroesBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+(getStringFromModel(boundries,boundries.getNorthBoundry()))+"</p>");
		boundroesBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+(getStringFromModel(boundries,boundries.getSouthBoundry()))+"</p>");
		boundroesBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+(getStringFromModel(boundries,boundries.getEastBoundry()))+"</p>");
		boundroesBuilder.append("<p style=\"display: inline; margin-top:0;margin-left:0;margin-right:0;margin-bottom: 10px; padding: 0;\">"+(getStringFromModel(boundries,boundries.getWestBoundry()))+"</p>");
		boundroesBuilder.append("</div>");

		boundroesBuilder.append("</body>\n</html>\n");


		return boundroesBuilder.toString();
	}
	private String getMeasurermentBuilder(StringBuilder measuremrentBuilder, Measurement measurement) {
		measuremrentBuilder.append("<html>\n<body>\n");

		// Left-aligned content
		measuremrentBuilder.append("<div style=\"float: left; width: 40%;\">");
		measuremrentBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">North By</p>");
		measuremrentBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">South By</p>");
		measuremrentBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">East By</p>");
		measuremrentBuilder.append("<p style=\"display: inline; margin-top:0;margin-left:0;margin-right:0;margin-bottom: 10px;padding: 0;\">West By</p>");
		measuremrentBuilder.append("</div>");

		//right alinged content
		measuremrentBuilder.append("<div style=\"float: right; width: 60%;\">");
		measuremrentBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+(getStringFromModel(measurement,measurement.getNorthMeasurement()))+"</p>");
		measuremrentBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+(getStringFromModel(measurement,measurement.getSouthMeasurement()))+"</p>");
		measuremrentBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+(getStringFromModel(measurement,measurement.getEastMeasurement()))+"</p>");
		measuremrentBuilder.append("<p style=\"display: inline; margin-top:0;margin-left:0;margin-right:0;margin-bottom: 10px; padding: 0;\">"+(getStringFromModel(measurement,measurement.getWestMeasurement()))+"</p>");
		measuremrentBuilder.append("</div>");

		measuremrentBuilder.append("</body>\n</html>\n");


		return measuremrentBuilder.toString();
	}

	private String getScheduleBBuilder(ScheduleB scheduleB, PropertyAddress proeprtyAddress,long size, StringBuilder scheduleBBuilder) {
		String addSurveyNo ="";
		if(!getString(scheduleB.getAddlSurveyNo()).isEmpty()) {
			addSurveyNo = " And "+getString(scheduleB.getAddlSurveyNo());
		}
		long maxWidth = getMaxWidthOfContent(scheduleB,proeprtyAddress);
		// Start HTML document
		scheduleBBuilder.append("<b>");
		scheduleBBuilder.append("<u>");
		if(size>1) {
			scheduleBBuilder.append("Item "+scheduleBNo+"<br>");
		}else {
			scheduleBBuilder.append("<br>");
		}
		scheduleBBuilder.append("</u>");
		scheduleBBuilder.append("</b>");
scheduleBBuilder.append("<html>\n<body>\n");
//				scheduleBBuilder.append("<table style=\"border-collapse: collapse; width: 100%;\">");
//		        scheduleBBuilder.append("<tbody>");
//		
//		            scheduleBBuilder.append("<tr>");
//		            scheduleBBuilder.append("<td style=\"border-collapse: collapse; width: 40%;\">").append("SRO District").append("</td>");
//		            scheduleBBuilder.append("<td style=\"border-collapse: collapse; width: 60%; \">").append(getStringFromModel(scheduleB,scheduleB.getSroDistrict())).append("</td>");
//		            scheduleBBuilder.append("</tr>");
//		            scheduleBBuilder.append("<tr>");
//		            scheduleBBuilder.append("<td style=\"border-collapse: collapse; width: 40%;\">").append("SRO").append("</td>");
//		            scheduleBBuilder.append("<td style=\"border-collapse: collapse; width: 60%;\">").append(getStringFromModel(scheduleB,scheduleB.getSro())).append("</td>");
//		            scheduleBBuilder.append("</tr>");
//		            scheduleBBuilder.append("<tr>");
//		            scheduleBBuilder.append("<td style=\"border-collapse: collapse; width: 40%;\">").append("Survey No And Addl Survey No").append("</td>");
//		            scheduleBBuilder.append("<td style=\"display: inline;border-collapse: collapse; width: 60%;word-wrap:break-word;\">").append(getStringFromModel(scheduleB,scheduleB.getSurveyNo()).concat(addSurveyNo)).append("</td>");
//		            scheduleBBuilder.append("</tr>");
//		            scheduleBBuilder.append("<tr>");
//		            scheduleBBuilder.append("<td style=\"border-collapse: collapse; width: 40%;\">").append("Plot No").append("</td>");
//		            scheduleBBuilder.append("<td style=\"border-collapse: collapse; width: 60%;\">").append(getStringFromModel(scheduleB,scheduleB.getPlotNo())).append("</td>");
//		            scheduleBBuilder.append("</tr>");
//		
//		        scheduleBBuilder.append("</tbody></table>");
		//table
//				scheduleBBuilder.append("<table class=\\\"MsoNormalTable\\\" style=\\\"margin-left: 20.25pt;width:100%; border-collapse: collapse; mso-table-layout-alt: fixed; border: none; mso-yfti-tbllook: 480; mso-padding-alt: 0in 0in 0in 0in;  cellspacing=\\\"0\\\" cellpadding=\\\"0\\\"><tbody>"
//						+ "<tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\">"
//						+ "<td style=\\\"padding: 0in; height: 12.5pt; text-align: left;\\\" valign=\\\"top\\\" width=\\\"200\\\">SRO District</td>"
//						+ "<td style=\\\"padding: 0in; height: 12.5pt; text-align: left;\\\" valign=\\\"top\\\" width=\\\"200\\\">"
//						+getStringFromModel(scheduleB,scheduleB.getSroDistrict())
//						+ "</td></tr>");
//					scheduleBBuilder.append("<tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\">"
//							+ "<td style=\\\"padding: 0in; height: 12.5pt; text-align: left;\\\" valign=\\\"top\\\" width=\\\"200\\\">SRO</td>"
//							+ "<td style=\\\"padding: 0in; height: 12.5pt; text-align: left;\\\" valign=\\\"top\\\" width=\\\"200\\\">"
//							+getStringFromModel(scheduleB,scheduleB.getSro())
//							+ "</td></tr>");
//					scheduleBBuilder.append("<tr style=\\\"mso-yfti-irow: 0; mso-yfti-firstrow: yes; height: 12.5pt;\\\">"
//							+ "<td style=\\\"padding: 0in; height: 12.5pt; text-align: left;\\\" valign=\\\"top\\\" >Survey No And Addl Survey No</td>"
//							+ "<td style=\\\"padding: 0in; height: 12.5pt; text-align: left;\\\" valign=\\\"top\\\" >"
//							+(getStringFromModel(scheduleB,scheduleB.getSurveyNo()).concat(addSurveyNo))
//							+ "</td></tr>");
//				scheduleBBuilder.append("</tbody></table>");

				// Left-aligned content
		scheduleBBuilder.append("<div style=\"float: left; width: 40%;\">");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">SRO District</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">SRO</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Survey No And Addl Survey No</p>");

		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Plot No</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Door No</p>");
		if(!getString(scheduleB.getProjectName()).isEmpty()) {
			scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Project Name</p>");
		}
		if(Objects.nonNull(proeprtyAddress)&&!getString(proeprtyAddress.getFlatNo()).isEmpty()) {
			scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Flat No</p>");
		}
		if(Objects.nonNull(proeprtyAddress)&&!getString(proeprtyAddress.getFloorNo()).isEmpty()) {
			scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Floor</p>");
		}
		if(Objects.nonNull(proeprtyAddress)&&!getString(proeprtyAddress.getBlock()).isEmpty()) {
			scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Block No</p>");
		}
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Address 1</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Address 2</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Address 3</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Pin Code</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Land Extent</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">District</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">Taluk</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin-top:0;margin-left:0;margin-right:0;margin-bottom: 10px; padding: 0;white-space:nowrap;\">Village</p>");
		scheduleBBuilder.append("</div>");

		// Right-aligned content //white-space:nowrap;
		scheduleBBuilder.append("<div style=\"float: right; width: 60%;\">");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+getStringFromModel(scheduleB,scheduleB.getSroDistrict())+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+getStringFromModel(scheduleB,scheduleB.getSro())+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+(getStringFromModel(scheduleB,scheduleB.getSurveyNo()).concat(addSurveyNo))+"</p>");
		//		scheduleBBuilder.append("<p style=\"display: inline-block; margin: 0;;margin-bottom: 10px; padding: 0; white-space:nowrap; padding-left: ").append(maxWidth * 7).append("px;\">")
		//        .append(getStringFromModel(scheduleB, scheduleB.getSurveyNo()).concat(addSurveyNo)+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+getStringFromModel(scheduleB,scheduleB.getPlotNo())+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+getStringFromModel(scheduleB,scheduleB.getDoorNo())+"</p>");
		if(!getString(scheduleB.getProjectName()).isEmpty()) {
			scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+getString(scheduleB.getProjectName())+"</p>");
		}
		if(Objects.nonNull(proeprtyAddress)&&!getString(proeprtyAddress.getFlatNo()).isEmpty()) {
			scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+getString(proeprtyAddress.getFlatNo())+"</p>");
		}
		if(Objects.nonNull(proeprtyAddress)&&!getString(proeprtyAddress.getFloorNo()).isEmpty()) {
			scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+getString(proeprtyAddress.getFloorNo())+"</p>");
		}
		if(Objects.nonNull(proeprtyAddress)&&!getString(proeprtyAddress.getBlock()).isEmpty()) {
			scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;\">"+getString(proeprtyAddress.getBlock())+"</p>");
		}
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+(getStringFromModel(proeprtyAddress,proeprtyAddress.getStreet()))+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+(getStringFromModel(proeprtyAddress,proeprtyAddress.getAddress1()))+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+(getStringFromModel(proeprtyAddress,proeprtyAddress.getAddress7()))+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+(getStringFromModel(proeprtyAddress,proeprtyAddress.getPinCode()))+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+(getStringFromModel(proeprtyAddress,proeprtyAddress.getLandExtent()))+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+(getStringFromModel(scheduleB,scheduleB.getDistrict()))+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin: 0; padding: 0;white-space:nowrap;\">"+(getStringFromModel(scheduleB,scheduleB.getTaluk()))+"</p>");
		scheduleBBuilder.append("<p style=\"display: inline; margin-top:0;margin-left:0;margin-right:0;margin-bottom: 10px; padding: 0;white-space:nowrap;\">"+(getStringFromModel(scheduleB,scheduleB.getVillage()))+"</p>");
		scheduleBBuilder.append("<br>");
		scheduleBBuilder.append("</div>");

		// End HTML document
		scheduleBBuilder.append("</body>\n</html>\n");

		// Print or use the HTML content
		String htmlContent = scheduleBBuilder.toString();
		return htmlContent;

	}

	private long getMaxWidthOfContent(ScheduleB scheduleB, PropertyAddress proeprtyAddress) {
		long maxValue =0;
		if(Objects.nonNull(scheduleB)) {
			maxValue = Math.max(getString(scheduleB.getSroDistrict()).length(),
					Math.max(getString(scheduleB.getSro()).length(),
							Math.max(getString(scheduleB.getDistrict()).length(),
									Math.max(getString(scheduleB.getTaluk()).length(),
											Math.max(getString(scheduleB.getTown()).length(),
													Math.max(getString(scheduleB.getVillage()).length(),
															Math.max(getString(scheduleB.getBuildingSocietyName()).length(),
																	Math.max(getString(scheduleB.getStateName()).length(),
																			Math.max((getString(scheduleB.getSurveyNo()).concat(getString(scheduleB.getAddlSurveyNo()))).length(),
																					Math.max(getString(scheduleB.getDoorNo()).length(),
																							Math.max(getString(scheduleB.getPlotNo()).length(),
																									Math.max(getString(scheduleB.getFloorNo()).length(),
																											Math.max(getString(scheduleB.getBlockNo()).length(),
																													getString(scheduleB.getProjectName()).length())
																											))))))))))));
			if(Objects.nonNull(proeprtyAddress)) {
				maxValue = Math.max(maxValue,
						Math.max(getString(proeprtyAddress.getStreet()).length(),
								Math.max(getString(proeprtyAddress.getAddress1()).length(),
										Math.max(getString(proeprtyAddress.getAddress7()).length(),
												Math.max(getString(proeprtyAddress.getPinCode()).length(),
														Math.max(getString(proeprtyAddress.getLandExtent()).length(),
																Math.max(getString(proeprtyAddress.getFlatNo()).length(),
																		Math.max(getString(proeprtyAddress.getFloorNo()).length(),
																				getString(proeprtyAddress.getBlock()).length()
																				)))))))
						);
			}
		}
		return maxValue;
	}

	private long getSizeOfTitleMap(Set<TitleHolderDetail> titleHolderDetailList) {
		long nonNullValueCount = titleHolderDetailList
				.stream()
				.filter(entry -> Objects.nonNull(entry))
				.count();
		return nonNullValueCount;
	}

	private String getDateWithoutTiming(Object valueString) {
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
		String value = getStringFromObject(valueString);
		String outputVlaue = null;
		if(!value.isEmpty()) {
			LocalDateTime ds = LocalDateTime.parse(value,inputFormatter);
			outputVlaue = ds.format(outputFormatter);
		}
		return outputVlaue;		
	}
	private String getDateWithChangeFormat(Object valueString) {
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
		String value = getStringFromObject(valueString);
		String outputVlaue = null;
		if(!value.isEmpty()) {
			LocalDateTime ds = LocalDateTime.now();
			outputVlaue = ds.format(outputFormatter);
		}
		return outputVlaue;		
	}


	private String getSROValue(Map<String, ScheduleB> scheduleBMap, String combinationKey) {
		if(Objects.nonNull(scheduleBMap)) {
			ScheduleB scheduleB = scheduleBMap.get(combinationKey);
			if(Objects.nonNull(scheduleB)) {
				return getUnknownValueForSRO(scheduleB.getSro());
			}else {
				return getUnknownValueForSRO(null);
			}
		}else {
			return getUnknownValueForSRO(null);
		}
	}

	private long getSizeOfScheduleBMap(Map<String, ScheduleB> scheduleBMap) {
		long nonNullValueCount = scheduleBMap.entrySet()
				.stream()
				.filter(entry -> entry.getValue() != null)
				.count();
		return nonNullValueCount;
	}

	private long getSizeOfScheduleAMap(Map<String, LinkedHashSet<ScheduleA>> map) {
		long nonNullValueCount = map.entrySet()
				.stream()
				.filter(entry -> !entry.getValue().isEmpty())
				.count();
		return nonNullValueCount;
	}


	private Object getYearsFromMonth(int term) {
		if(term>0) {
			int data = term/12;
			BigDecimal decimal = new BigDecimal(data);
			String formattedValue = decimal.setScale(1, BigDecimal.ROUND_HALF_UP)
					.stripTrailingZeros()
					.toPlainString();
			return formattedValue;
		}
		return "";
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
			variablesValueMap.put("~~MOTD_Date~~",("<b>"+"<u>"+dayOrdinal+"</b>"+"</u>"));
			variablesValueMap.put("~~MOTD_Month_Year~~",("<b>"+"<u>"+dateSplitdata[1]+"</b>"+"</u>"));
		} catch (ParseException e) {
			e.printStackTrace();
		}

	}

	public static String addOrdinalSuffix(String data) {
		int number = Integer.parseInt(data);
		if (number >= 11 && number <= 13) {
			return number + "th";
		}

		switch (number % 10) {
		case 1:
			return number + "st";
		case 2:
			return number + "nd";
		case 3:
			return number + "rd";
		default:
			return number + "th";
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

	public String nullCheckStringField(Object fieldValue) {
		if(Objects.nonNull(fieldValue)) {
			return String.valueOf(fieldValue);
		}
		return "________";
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
		Connection connection =null;
		try {
			connection = currentDataSource.getConnection();
			try(PreparedStatement preparedStatement1 = connection.prepareStatement("Select A.Obm_Address_Info.Street_L , A.Obm_Address_Info.Column1_L ,"
					+ "                A.Obm_Address_Info.Column2_L, A.Obm_Address_Info.Column3_L,"
					+ "                A.Obm_Address_Info.Column4_L, A.Obm_Address_Info.Column5_L,"
					+ "                A.Obm_Address_Info.Column7_L,"
					+ "                A.Obm_Address_Info.Pin_Zip_Code_L,"
					+ "                Nvl (Trim (A.Obm_Address_Info.Office_Phone_No),"
					+ "                     Trim (A.Obm_Address_Info.Column6_L)"
					+ "                    ),"
					+ "                Trim (A.Obm_Address_Info.Office_Fax_No) From Sa_Organization_Branch_Master A  Where Upper (Obm_Branch_Code) = Upper (?) And Rownum < 2");){

				preparedStatement1.setString(1, branchCode);
				try(ResultSet resultSet1 = preparedStatement1.executeQuery();){
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
				}
			}
			try(PreparedStatement preparedStatement2 = connection.prepareStatement("Select City_Name"
					+ "   From Hfs_Vw_City"
					+ "   Where City_Code = ?"
					+ "   And State_Record_Id ="
					+ "   (Select Record_Id"
					+ "   From Hfs_Vw_State"
					+ "   Where State_Code = ?"
					+ "   And Country_Code = ?)");){
				preparedStatement2.setString(1, branchAddress.getAddress4());
				preparedStatement2.setString(2, branchAddress.getAddress3());
				preparedStatement2.setString(3, branchAddress.getAddress2());
				try(ResultSet resultSet2 = preparedStatement2.executeQuery();){
					while (resultSet2.next()) {
						branchAddress.setDistrictName(resultSet2.getString(1));
					}
				}
			}
			try(PreparedStatement preparedStatement3 = connection.prepareStatement("Select Location_Name"
					+ "   From Hfs_Vw_Postal_Code"
					+ "   Where Location_Code =?"
					+ "   And City_Code = ?"
					+ "   And State_Code = ?"
					+ "   And Country_Code = ?");){
				preparedStatement3.setString(1, branchAddress.getAddress5());
				preparedStatement3.setString(2, branchAddress.getAddress4());
				preparedStatement3.setString(3, branchAddress.getAddress3());
				preparedStatement3.setString(4, branchAddress.getAddress2());
				try(ResultSet resultSet3 = preparedStatement3.executeQuery();){
					while (resultSet3.next()) {
						branchAddress.setLocationName(resultSet3.getString(1));
					}
				}
			}
			logger.info("branchaddress fetch method ended" +branchAddress);
		}catch (Exception e) {
			logger.info("branchaddress fetch method fails",e); 
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
		return branchAddress;

	}
	private List<LetterReportModel> fetchDataForOracleDataBase(GenerateTemplateModel model) {
		List<LetterReportModel> letterModelList = new ArrayList<>();
		BranchAddress branchAddress = new BranchAddress();
		dynamicDataSourceService.switchToOracleDataSource();

		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		List<String> contractNumberList = new ArrayList<>();
		try(Connection connection = currentDataSource.getConnection()) {
			String sql ="";
			if(Objects.nonNull(model.getApplicationNumber())  && !(model.getApplicationNumber().isEmpty())) {
				logger.info("applicationnumber fetch started");
				sql = "SELECT DISTINCT(A.CONTRACT_NUMBER) from cc_contract_stage_details A,CC_CONTRACT_MASTER B where A.CONTRACT_NUMBER=? AND A.STATUS =1"
						+ "AND A.CONTRACT_NUMBER = B.CONTRACT_NUMBER and B.CONTRACT_STATUS=1";
			}else if(model.getSanctionDate()!=null) {
				logger.info("sanctionDate fetch started");
				sql = "SELECT DISTINCT(A.CONTRACT_NUMBER) from cc_contract_stage_details A,CC_CONTRACT_MASTER B where A.START_DATE = "
						+ "to_date(" + "'" + model.getSanctionDate()+  "', 'dd-MM-yy')"
						+ "AND a.STATUS =1 AND A.CONTRACT_NUMBER = B.CONTRACT_NUMBER and B.CONTRACT_STATUS=1";
			}else {
				return letterModelList;
			}

			try(PreparedStatement preparedStatement = connection.prepareStatement(sql)){
				if(Objects.nonNull(model.getApplicationNumber())  && !(model.getApplicationNumber().isEmpty())) {
					preparedStatement.setString(1, model.getApplicationNumber());
				}
				try(ResultSet resultSet = preparedStatement.executeQuery()){
					if(!resultSet.isBeforeFirst()) {
						logger.info("no data");
						return letterModelList;
					}
					while (resultSet.next()) {
						contractNumberList.add(resultSet.getString(1));
					}
				}catch (Exception e) {
					e.printStackTrace();
					logger.info("fail to fetch contract data");
				}
			}
			contractNumberList.stream().forEach(contractNumber->{
				String query1 = "SELECT CONTRACT_NUMBER,CONTRACT_BRANCH,CUSTOMER_CODE,AMOUNT_FINANCED,PURPOSE_OF_LOAN,APPLICATION_NUMBER,APPLICATION_DATE,PREMIUM_AMT,MORATORIUM_PERIOD FROM cc_contract_master where application_number=?";
				try(PreparedStatement preparedStatement = connection.prepareStatement(query1)){
					preparedStatement.setString(1, contractNumber);
					try(ResultSet resultSet = preparedStatement.executeQuery()){
						if(!resultSet.isBeforeFirst()) {
							logger.info("no data");
							return;
						}
						while (resultSet.next()) {
							LetterReportModel letterModel = new LetterReportModel();
							letterModel.setContractNumber(resultSet.getString(1));
							letterModel.setBranchCode(resultSet.getString(2));
							letterModel.setCustomerCode(resultSet.getString(3));
							letterModel.setAmountFinanced(convertRoundedValue(resultSet.getString(4)));
							letterModel.setPurposeOfLoanCode(String.valueOf(resultSet.getInt(5)));
							letterModel.setApplicationNumber(resultSet.getString(6));
							letterModel.setContractNumber(contractNumber);
							letterModel.setApplicationDate(resultSet.getString(7));
							letterModel.setApplicationDate(resultSet.getString(7));
							int lifeInsurance = Objects.nonNull(resultSet.getString(8))?resultSet.getInt(8):0;
							letterModel.setLifeInsurance(getNilValues(lifeInsurance));
							String period = resultSet.getString(9);
							if(Objects.nonNull(period)) {
								letterModel.setMoratoriumPeriod(Integer.parseInt(period));
							}else {
								letterModel.setMoratoriumPeriod(0);
							}

							logger.info("contract_master data fetch completed",letterModel);
							try {
								logger.info("data in rate details started");
								try(PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT NET_RATE, TERM, EMI_AMOUNT,PRINCIPAL_OS,RATE_TYPE FROM Cc_Contract_Rate_Details where contract_number=?  order by occurance_number desc fetch first 1 row only");){
									preparedStatement1.setString(1, letterModel.getContractNumber());
									try(ResultSet resultSet1 = preparedStatement1.executeQuery();){
										while (resultSet1.next()) {
											letterModel.setNetRate(convertDecimalValue(resultSet1.getString(1)));
											letterModel.setTerm((resultSet1.getInt(2)));
											letterModel.setEmiAmount(convertRoundedValue(String.valueOf(resultSet1.getInt(3))));
											letterModel.setPrincipalOutstanding(convertRoundedValue(String.valueOf(resultSet1.getInt(4))));
											letterModel.setRateType(resultSet1.getString(5));
											logger.info("data in rate details completed",letterModel);
										}
									}catch (Exception e) {
										logger.info("data in rate details failed",e);
										e.printStackTrace();
									}
								}
							}catch (Exception e) {
								logger.info("data in rate details failed",e);
								e.printStackTrace();
							}


							try {
								logger.info("purpose of loan value started");
								try(PreparedStatement preparedStatement12 = connection.prepareStatement("SELECT HDLD_REF_CODE, HDLD_REF_VALUE, HDLD_REF_CODE_VALUE"
										+ "  FROM HFS_DYNAMIC_LOV_DTLS "
										+ "	WHERE HDLD_REF_CODE =220"
										+ "   AND HDLD_STATUS = 'A' And HDLD_REF_CODE_VALUE=?");){
									preparedStatement12.setString(1, letterModel.getPurposeOfLoanCode());
									try(ResultSet resultSet12 = preparedStatement12.executeQuery();){
										while (resultSet12.next()) {
											letterModel.setPurposeOfLoan(resultSet12.getString(2));
											logger.info("purpose of loan",letterModel);
										}
									}catch (Exception e) {
										logger.info("data in Processing failed",e);
										e.printStackTrace();
									}
								}
							}catch (Exception e) {
								logger.info("data in Processing failed",e);
								e.printStackTrace();
							}
							String processingFee="0";
						

							try {
								logger.info("data in basefile number started");
								try(PreparedStatement preparedStatement40 = connection.prepareStatement("SELECT BASE_FILE_NUMBER FROM Hfs_File_Auto_Topup_Upload where customer_code=?");){
									preparedStatement40.setString(1, letterModel.getCustomerCode());
									try(ResultSet resultSet4 = preparedStatement40.executeQuery();){
										while (resultSet4.next()) {
											letterModel.setBaseFileNumber(resultSet4.getString(1));
											logger.info("data in basefile number",letterModel);
										}
									}catch (Exception e) {
										logger.info("data in basefile number failed",e);
										e.printStackTrace();
									}
								}
							}catch (Exception e) {
								logger.info("data in basefile number failed",e);
								e.printStackTrace();
							}

							try {
								logger.info("data in purposeofloan started");
								try(PreparedStatement preparedStatement23 = connection.prepareStatement("Select purpose_of_loan from hfs_file_auto_topup_details where base_file_number=?");){
									preparedStatement23.setString(1, letterModel.getBaseFileNumber());
									try(ResultSet resultSet23 = preparedStatement23.executeQuery();){
										while (resultSet23.next()) {
											letterModel.setPurposeOfLoan(resultSet23.getString(1));
											logger.info("data in purposeofloan",letterModel);
										}
									}catch (Exception e) {
										logger.info("data in purposeofloan failed",e);
										e.printStackTrace();
									}
								}
							}catch (Exception e) {
								logger.info("data in purposeofloan failed",e);
								e.printStackTrace();
							}
							try {
								logger.info("data in accountno started");
								try(PreparedStatement preparedStatement17 = connection.prepareStatement("SELECT NACH_BANK_ACC_NUM FROM Hfs_File_Auto_Topup_details where base_file_number=?");){
									preparedStatement17.setString(1, letterModel.getBaseFileNumber());
									try(ResultSet resultSet17 = preparedStatement17.executeQuery();){
										while (resultSet17.next()) {
											letterModel.setAccountNo(resultSet17.getString(1));
											logger.info("data in accountno",letterModel);
										}
									}catch (Exception e) {
										logger.info("data in accountno failed",e);
										e.printStackTrace();
									}
								}
							}catch (Exception e) {
								logger.info("data in accountno failed",e);
								e.printStackTrace();
							}


							try {
								try(PreparedStatement preparedStatement6 = connection.prepareStatement("SELECT ocm_company_name FROM sa_organization_company_master");
										ResultSet resultSet6 = preparedStatement6.executeQuery();){
									while (resultSet6.next()) {
										letterModel.setCompanyName(resultSet6.getString(1));
										logger.info("data in companyName",letterModel);
									}
								}
							}catch (Exception e) {
								logger.info("data in companyName failed",e);
								e.printStackTrace();
							}

							try(PreparedStatement preparedStatement7 = connection.prepareStatement("SELECT a.obm_address_info.email"
									+ "  FROM sa_organization_branch_master a"
									+ "  WHERE obm_branch_code = ?");){
								preparedStatement7.setString(1, letterModel.getBranchCode());
								try(ResultSet resultSet7 = preparedStatement7.executeQuery();){
									while (resultSet7.next()) {
										letterModel.setBranchMailId(resultSet7.getString(1));
									}
								}catch (Exception e) {
									logger.info("data in companyName failed",e);
									e.printStackTrace();
								}
							}catch (Exception e) {
								logger.info("data in companyName failed",e);
								e.printStackTrace();
							}


							try(PreparedStatement preparedStatement8 = connection.prepareStatement("Select A.Obm_Address_Info.Street_L , A.Obm_Address_Info.Column1_L ,"
									+ "                A.Obm_Address_Info.Column2_L, A.Obm_Address_Info.Column3_L,"
									+ "                A.Obm_Address_Info.Column4_L, A.Obm_Address_Info.Column5_L,"
									+ "                A.Obm_Address_Info.Column7_L,"
									+ "                A.Obm_Address_Info.Pin_Zip_Code_L,"
									+ "                Nvl (Trim (A.Obm_Address_Info.Office_Phone_No),"
									+ "                     Trim (A.Obm_Address_Info.Column6_L)"
									+ "                    ),"
									+ "                Trim (A.Obm_Address_Info.Office_Fax_No) From Sa_Organization_Branch_Master A  Where Upper (Obm_Branch_Code) = Upper (?) And Rownum < 2");){
								preparedStatement8.setString(1, letterModel.getBranchCode());
								try(ResultSet resultSet8 = preparedStatement8.executeQuery();){

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
								}catch (Exception e) {
									logger.info("data in branchaddress failed",e);
									e.printStackTrace();
								}
							}catch (Exception e) {
								logger.info("data in branchaddress failed",e);
								e.printStackTrace();
							}

							try(PreparedStatement preparedStatement9 = connection.prepareStatement("Select City_Name"
									+ "   From Hfs_Vw_City"
									+ "   Where City_Code = ?"
									+ "   And State_Record_Id ="
									+ "   (Select Record_Id"
									+ "   From Hfs_Vw_State"
									+ "   Where State_Code = ?"
									+ "   And Country_Code = ?)");){
								preparedStatement9.setString(1, branchAddress.getAddress4());
								preparedStatement9.setString(2, branchAddress.getAddress3());
								preparedStatement9.setString(3, branchAddress.getAddress2());
								try(ResultSet resultSet9 = preparedStatement9.executeQuery();){
									while (resultSet9.next()) {
										branchAddress.setDistrictName(resultSet9.getString(1));
									}
								}catch (Exception e) {
									logger.info("data in district failed",e);
									e.printStackTrace();
								}
							}catch (Exception e) {
								logger.info("data in district failed",e);
								e.printStackTrace();
							}
							try(PreparedStatement preparedStatement10 = connection.prepareStatement("Select Location_Name"
									+ "   From Hfs_Vw_Postal_Code"
									+ "   Where Location_Code =?"
									+ "   And City_Code = ?"
									+ "   And State_Code = ?"
									+ "   And Country_Code = ?");){
								preparedStatement10.setString(1, branchAddress.getAddress5());
								preparedStatement10.setString(2, branchAddress.getAddress4());
								preparedStatement10.setString(3, branchAddress.getAddress3());
								preparedStatement10.setString(4, branchAddress.getAddress2());
								try(ResultSet resultSet10 = preparedStatement10.executeQuery();){
									while (resultSet10.next()) {
										branchAddress.setLocationName(resultSet10.getString(1));
									}
								}catch (Exception e) {
									logger.info("data in location failed",e);
									e.printStackTrace();
								}
							}catch (Exception e) {
								logger.info("data in location failed",e);
								e.printStackTrace();
							}


							try(PreparedStatement preparedStatement11 = connection.prepareStatement("SELECT A.CUM_NAME_INFO.NAME_1_L,A.CUM_NAME_INFO.NAME_2_L"
									+ ",A.CUM_NAME_INFO.NAME_3_L,"
									+ "A.CUM_NAME_INFO.NAME_4_L,A.CUM_NAME_INFO.NAME_5_L FROM Sa_Customer_Master A "
									+ "Where CUM_Customer_Code = ?");){
								preparedStatement11.setString(1, letterModel.getCustomerCode());
								try(ResultSet resultSet11 = preparedStatement11.executeQuery();){
									while (resultSet11.next()) {
										letterModel.setApplicant(resultSet11.getString(3));
										String custName = appendCustomerName(resultSet11);
										letterModel.setCustomerName(custName);
									}
									List<String> customerCodeList = new ArrayList<>();
									try(PreparedStatement preparedStatement15 = connection.prepareStatement("SELECT customer_code FROM cc_contract_addl_appl_dtls where contract_number=?"
											+ " and customer_type='CO'"
											+ "");){
										preparedStatement15.setString(1, letterModel.getContractNumber());
										try(ResultSet resultSet15 = preparedStatement15.executeQuery();){
											while (resultSet15.next()) {
												customerCodeList.add(resultSet15.getString(1));
											}
										}catch (Exception e) {
											logger.info("data in  co-applicant failed",e);
											e.printStackTrace();
										}
									}catch (Exception e) {
										logger.info("data in  co-applicant failed",e);
										e.printStackTrace();
									}
									List<String> applicantNameList = new ArrayList<>();
									customerCodeList.stream().forEach(code->{
										try {
											preparedStatement11.setString(1, code);
											try(ResultSet resultSet16 = preparedStatement11.executeQuery();){
												while (resultSet16.next()) {
													applicantNameList.add(resultSet16.getString(3));
												}
											}catch (Exception e) {
												logger.info("data in co-applicant failed",e);
												e.printStackTrace();
											}
										} catch (SQLException e) {
											e.printStackTrace();
										}
									});
									if(!applicantNameList.isEmpty()) {
										getCoApplicantNames(applicantNameList,letterModel);
									}
								}catch (Exception e) {
									logger.info("data in location failed",e);
									e.printStackTrace();
								}
							}catch (Exception e) {
								logger.info("data in location failed",e);
								e.printStackTrace();
							}

							try(PreparedStatement preparedStatement12 = connection.prepareStatement("Select Nvl (Trim (A.Obm_Address_Info.Office_Phone_No),"
									+ "   Trim (A.Obm_Address_Info.Column6_L)"
									+ "     ),"
									+ "                Trim (A.Obm_Address_Info.Office_Fax_No)"
									+ "           From Sa_Organization_Branch_Master A"
									+ "          Where Upper (Obm_Branch_Code) = Upper (?)"
									+ "                And Rownum < 2");){
								preparedStatement12.setString(1, letterModel.getBranchCode());
								try(ResultSet resultSet12 = preparedStatement12.executeQuery();){
									while (resultSet12.next()) {
										letterModel.setTelePhoneNumber(getConvertedPhoneNumber(resultSet12.getString(1),resultSet12.getString(2)));
									}
								}catch (Exception e) {
									logger.info("data in address failed",e);
									e.printStackTrace();
								}

							}catch (Exception e) {
								logger.info("data in address failed",e);
								e.printStackTrace();
							}

							String branchAddressString = convertBranchAddress(branchAddress);
							letterModel.setBranchAddress(branchAddressString);

							try(PreparedStatement preparedStatement5 = connection.prepareStatement("Select Listagg(Loan_Desc,', ') Within Group (Order By Loan_Desc)"
									+ "      From ("
									+ "      Select Upper(Usage_Of_Loan_Desc)||' - '||Listagg(End_Use_Desc,', ') Within Group (Order By Usage_Of_Loan_Code) Loan_Desc From"
									+ "      Hfs_Tb_End_Of_Usage_Loan"
									+ "      Where File_Number = ? Group By Usage_Of_Loan_Desc)");){
								preparedStatement5.setString(1, letterModel.getContractNumber());
								try(ResultSet resultSet5 = preparedStatement5.executeQuery();){
									while (resultSet5.next()) {
										letterModel.setEndUseOfLoan(resultSet5.getString(1));
									}
								}catch (Exception e) {
									logger.info("data in address failed",e);
									e.printStackTrace();
								}
							}catch (Exception e) {
								logger.info("data in address failed",e);
								e.printStackTrace();
							}
							CustomerAddress customerAddress = new CustomerAddress();
							Map<String,String> prepareStatementList = new HashMap<>();
							String sql18 = "select C.Caa_Address_Info.Street_L,C.Caa_Address_Info.Column1_L,C.Caa_Address_Info.Column2_L,C.Caa_Address_Info.Column3_L,"
									+ "C.Caa_Address_Info.Column4_L,C.Caa_Address_Info.Column5_L,C.Caa_Address_Info.Column7_L, C.Caa_Address_Info.Pin_Zip_Code_L Zipcode FROM Sa_Customer_Addl_Address_Dtls C "
									+ "where C.caa_customer_code=? And C.CAA_ADDRESS_TYPE_CODE = 1";
							try(PreparedStatement preparedStatement18 = connection.prepareStatement(sql18);){
								preparedStatement18.setString(1, letterModel.getCustomerCode());
								try(ResultSet resultSet18 = preparedStatement18.executeQuery();){
									prepareStatementList.put("preparedStatement18",sql18);
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
								}catch (Exception e) {
									logger.info("data in address failed",e);
									e.printStackTrace();
								}
							}catch (Exception e) {
								logger.info("data in address failed",e);
								e.printStackTrace();
							}
							String sql19 = "Select Gld_Geo_Level_Desc"
									+ "	From   SA_GEOGRAPHICAL_LEVEL_DETAILS"
									+ "	Where  Gld_Geo_Level_Code   = ?"
									+ "	And    GLD_GEO_LEVEL_STATUS = 'A'"
									+ "	And    GLD_GEO_LEVEL_STRING = ?||':'||?||':'||?"
									+ "	And    GLD_GEO_LEVEL_NUMBER = (Select GL_GEO_LEVEL_NUMBER"
									+ "	From  SA_GEOGRAPHICAL_LEVELS"
									+ "	Where  GL_GEO_LEVEL_NAME = 'LOCATION')";
							try(PreparedStatement preparedStatement19 = connection.prepareStatement(sql19);){
								prepareStatementList.put("preparedStatement19",sql19);
								preparedStatement19.setString(1, customerAddress.getAddress5());
								preparedStatement19.setString(2, customerAddress.getAddress2());
								preparedStatement19.setString(3, customerAddress.getAddress3());
								preparedStatement19.setString(4, customerAddress.getAddress4());
								try(ResultSet resultSet19 = preparedStatement19.executeQuery();){
									while (resultSet19.next()) {
										customerAddress.setLocation(resultSet19.getString(1));
									}
								}catch (Exception e) {
									logger.info("data in address failed",e);
									e.printStackTrace();
								}
							}catch (Exception e) {
								logger.info("data in address failed",e);
								e.printStackTrace();
							}
							String sql20="SELECT CITY_NAME FROM HFS_VW_CITY"
									+ " WHERE CITY_CODE = ? AND STATE_RECORD_ID ="
									+ " (SELECT RECORD_ID FROM HFS_VW_STATE WHERE STATE_CODE = ? AND COUNTRY_CODE = ?)";
							try(PreparedStatement preparedStatement20 = connection.prepareStatement(sql20);){
								prepareStatementList.put("preparedStatement20",sql20);
								preparedStatement20.setString(1, customerAddress.getAddress4());
								preparedStatement20.setString(2, customerAddress.getAddress3());
								preparedStatement20.setString(3, customerAddress.getAddress2());
								try(ResultSet resultSet20 = preparedStatement20.executeQuery();){
									while (resultSet20.next()) {
										customerAddress.setCity(resultSet20.getString(1));
									}
								}
							}
							String sql21 = "Select Gld_Geo_Level_Desc From SA_GEOGRAPHICAL_LEVEL_DETAILS Where Gld_Geo_Level_Code   = ?"
									+ "	 And GLD_GEO_LEVEL_STATUS = 'A' And GLD_GEO_LEVEL_STRING = To_Char(Nvl(?,1)) And GLD_GEO_LEVEL_NUMBER = "
									+ "  (Select GL_GEO_LEVEL_NUMBER From SA_GEOGRAPHICAL_LEVELS Where GL_GEO_LEVEL_NAME = 'STATE')";
							try(PreparedStatement preparedStatement21 = connection.prepareStatement(sql21);){
								prepareStatementList.put("preparedStatement21",sql21);
								preparedStatement21.setString(1, customerAddress.getAddress3());
								preparedStatement21.setString(2, customerAddress.getAddress2());
								try(ResultSet resultSet21 = preparedStatement21.executeQuery();){
									while (resultSet21.next()) {
										customerAddress.setState(resultSet21.getString(1));
									}
								}
							}
							String sql22 = "select Gld_Geo_Level_Desc from SA_GEOGRAPHICAL_LEVEL_DETAILS Where Gld_Geo_Level_Code  = ? And GLD_GEO_LEVEL_STATUS = 'A'"
									+ " And GLD_GEO_LEVEL_NUMBER = (Select GL_GEO_LEVEL_NUMBER From SA_GEOGRAPHICAL_LEVELS Where GL_GEO_LEVEL_NAME = 'COUNTRY')";
							try(PreparedStatement preparedStatement22 = connection.prepareStatement(sql22);){
								preparedStatement22.setString(1, customerAddress.getAddress2());
								prepareStatementList.put("preparedStatement22",sql22);
								try(ResultSet resultSet22 = preparedStatement22.executeQuery();){
									while (resultSet22.next()) {
										customerAddress.setCountry(resultSet22.getString(1));
									}
								}
							}

							String customerAddressString = appendCustomerAddress(customerAddress,letterModel.getCustomerAddress());
							letterModel.setCustomerAddress(customerAddressString);

							fetchOracleDataForMITC(letterModel);
							fetchOracleDataForMOTD(letterModel,prepareStatementList);

							Date date = new Date();
							SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
							letterModel.setCurrentDate(formatter.format(date));
							letterModelList.add(letterModel);
						}
					}catch (Exception e) {
						logger.info("Faile to fetch the main data");
						e.printStackTrace();
					}
				}catch (Exception e) {
					logger.info("Faile to fetch the main data");
					e.printStackTrace();
				}
			});




		} catch (Exception e) {
			// Handle SQL exception
			e.printStackTrace();
		}
		return letterModelList;
	}

	private void fetchOracleDataForMITC(LetterReportModel letterModel) {
		logger.info("fetchOracleDataForMITC method starts");
		dynamicDataSourceService.switchToOracleDataSource();
		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;		
		try {
			connection = currentDataSource.getConnection();
			try {
				logger.info("Documentation charges starts");
				Date date = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
				String effectiveDate = formatter.format(date);
				//String effectiveDate = convertDateFormat((letterModel.getApplicationDate()));
				try(PreparedStatement preparedStatement24 = connection.prepareStatement("select flat_fee,MITC_CHG From Hfs_Doc_Fee_Master_Header A ,"
						+ "   Hfs_Doc_Fee_Master_Dtls B"
						+ "   Where A.Bucket_Key=B.Bucket_Key"
						+ "   And ? Between Start_Term And End_Term"
						+ "   And ? Between Start_Amount And End_Amount"
						+ "   And A.Bucket_Code="
						+ "   (Select Max(Bucket_Code)"
						+ "   From Hfs_Doc_Fee_Master_Header C ,"
						+ "   Hfs_Doc_Fee_Master_Branch_Dtls E"
						+ "   Where "
						+ "to_date(" + "'" + effectiveDate
						+ "	', 'dd-MM-yy')" 
						+" Between Start_Date And End_Date"
						+ "   And"
						+ "   E.Bucket_Key =C.Bucket_Key And E.Branch_Code=?)");){
					preparedStatement24.setInt(1,letterModel.getTerm());
					preparedStatement24.setInt(2, letterModel.getAmountFinanced());
					preparedStatement24.setString(3, letterModel.getBranchCode());
					int documentCharges = 0;
					try(ResultSet resultSet24 = preparedStatement24.executeQuery();){
						while (resultSet24.next()) {
//							letterModel.setFlatFee(resultSet24.getString(1));
//							letterModel.setFlatRate(resultSet24.getString(2));
							if(Objects.nonNull(resultSet24.getString(2))) {
								documentCharges = resultSet24.getInt(2);
							}
							logger.info("Documentation charges",documentCharges);
						}
						letterModel.setDocumentationCharges(getNilValues(documentCharges));
						//setDocumentChargesValue(letterModel);
					}catch (Exception e) {
						logger.info("Documentation charges faile",e);
						e.printStackTrace();
					}
				}catch (Exception e) {
					logger.info("Documentation charges faile",e);
					e.printStackTrace();
				}



			}catch (Exception e) {
				logger.info("Documentation charges faile",e);
				e.printStackTrace();
			}

			try {
				logger.info("Rate type detail starts");
				PreparedStatement preparedStatement25 = connection.prepareStatement("Select A.Division_Code,A.Product_Code,A.Scheme_Code,B.Rate_Type"
						+ "    From Cc_Contract_Master A,"
						+ "      Cc_Contract_Rate_Details B"
						+ "    Where A.Contract_Number = B.Contract_Number"
						+ "    And B.Occurance_Number  ="
						+ "      (Select Max(C.Occurance_Number)"
						+ "      From Cc_Contract_Rate_Details C"
						+ "      Where C.Contract_Number = A.Contract_Number)"
						+ "    And A.Contract_Number = ?");
				preparedStatement25.setString(1,letterModel.getApplicationNumber());
				ResultSet resultSet25 = preparedStatement25.executeQuery();
				while (resultSet25.next()) {
					letterModel.setDivisionCode(resultSet25.getString(1));
					letterModel.setProductCode(resultSet25.getString(2));
					letterModel.setSchemeCode(resultSet25.getString(3));
					letterModel.setRateType(resultSet25.getString(4));
					logger.info("Rate type detail ",letterModel);
				}

			}catch (Exception e) {
				logger.info("Rate type fails ",e);
				e.printStackTrace();
			}
			try {
				logger.info("getborrower starts");
				PreparedStatement preparedStatement26 = connection.prepareStatement("Select Decode(A.Product_Type,'I',1,'C',2)"
						+ " From Sa_Division_Master A"
						+ "  Where Product_Code = ?"
						+ "  And Division_Code  = ?");
				preparedStatement26.setString(1,letterModel.getProductCode());
				preparedStatement26.setString(2, letterModel.getDivisionCode());
				ResultSet resultSet26 = preparedStatement26.executeQuery();
				while (resultSet26.next()) {
					letterModel.setBorrower(resultSet26.getString(1));
					logger.info("getborrower",letterModel);
				}
			}catch (Exception exception) {
				logger.info("getborrower fails");
				exception.printStackTrace();
			}
			try {
				logger.info("getproductName starts");
				PreparedStatement preparedStatement30 = connection.prepareStatement("select DIVISION_DESCRIPTION from SA_DIVISION_MASTER WHERE DIVISION_CODE = ?");
				preparedStatement30.setString(1,letterModel.getDivisionCode());
				ResultSet resultSet30 = preparedStatement30.executeQuery();
				while (resultSet30.next()) {
					letterModel.setProduct(resultSet30.getString(1));
					logger.info("productCode",letterModel);
				}
			}catch (Exception exception) {
				logger.info("getproductName fails");
				exception.printStackTrace();
			}
			getProcessingFeeValue(letterModel);
			

			List<Integer> codeList = new ArrayList<>();
			try {
				logger.info("enduseOfloan starts");
				PreparedStatement preparedStatement27 = connection.prepareStatement("Select Distinct Usage_Of_Loan_Code"
						+ "  From Hfs_Tb_End_Of_Usage_Loan"
						+ "  Where File_Number = ?");
				preparedStatement27.setString(1,letterModel.getContractNumber());
				ResultSet resultSet27 = preparedStatement27.executeQuery();
				while (resultSet27.next()) {
					int code = resultSet27.getInt(1);
					codeList.add(code);
					logger.info("enduseOfloan ",codeList);
				}
				try {
					logger.info("enduseOfloan based on ref start");
					PreparedStatement preparedStatement28 = connection.prepareStatement("Select REFERENCE"
							+ " From Cc_Contract_Master"
							+ " Where CONTRACT_NUMBER = ?");
					preparedStatement28.setString(1,letterModel.getContractNumber());
					ResultSet resultSet28 = preparedStatement28.executeQuery();
					while (resultSet28.next()) {
						letterModel.setReference(resultSet28.getString(1));
						logger.info("reference value",letterModel);
					}
					if(Objects.nonNull(letterModel.getReference()) && letterModel.getReference().equals("T")&&Objects.isNull(letterModel.getEndUseOfLoanCode())) {
						try {
							PreparedStatement preparedStatement29 = connection.prepareStatement("Select Distinct Usage_Of_Loan_Code"
									+ "  From Hfs_Tb_End_Of_Usage_Loan"
									+ "  Where File_Number = (Select Acct_No From CC_Contract_Master Where Contract_number = ?)");
							preparedStatement29.setString(1,letterModel.getContractNumber());
							ResultSet resultSet29 = preparedStatement29.executeQuery();
							codeList.clear();
							while (resultSet29.next()) {
								int code = resultSet29.getInt(1);
								codeList.add(code);
							}
							logger.info("codelist based on filenumber strted");
						}catch (Exception e) {
							e.printStackTrace();
							logger.info("codelist based on filenumber failed",e);
						}
					}
				}catch (Exception e) {

					logger.info("enduseOfloan based on ref failed",e);
					e.printStackTrace();
				}
				logger.info("enduseOfloan ",letterModel);
			}catch (Exception e) {
				logger.info("enduseOfloan fails",e);
				e.printStackTrace();
			}
			if(codeList.size()>1) {
				letterModel.setEndUseOfLoanCode("1");
			}else {
				letterModel.setEndUseOfLoanCode(null);
			}


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
			logger.info("fetchOracleDataForMITC failed",e);
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

	private void getProcessingFeeValue(LetterReportModel letterModel) {
		dynamicDataSourceService.switchToOracleDataSource();
		int processingFromUpfront = 0;
		int processingFromFee =0;
		int processingFee = 0;
		int balancePyable = 0;
		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		try (Connection connection = currentDataSource.getConnection()){
			logger.info("processingFee starts");
			try(PreparedStatement preparedStatement31 = connection.prepareStatement("SELECT Upfront_Fee FROM Hfs_Upfront_Fee_Dtls"
					+ " WHERE Product_Code=?"
					+ " AND profile_code  =?");){
				preparedStatement31.setString(1,letterModel.getDivisionCode());
				preparedStatement31.setString(2,letterModel.getSchemeCode());
				try(ResultSet resultSet31 = preparedStatement31.executeQuery();){
					while (resultSet31.next()) {
						if(Objects.nonNull(resultSet31.getString(1))) {
						processingFromUpfront = Integer.parseInt(resultSet31.getString(1));
						}
						logger.info("balancePayable",processingFromUpfront);
					}
				}
			}
			
			logger.info("data in Processingfee started");
			try(PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT "
					+ "PF_RECEIVABLE FROM Cc_Contract_Fee_Details where contract_number=?");){
				preparedStatement2.setString(1, letterModel.getContractNumber());
				try(ResultSet resultSet2 = preparedStatement2.executeQuery();){
					while (resultSet2.next()) {
						if(Objects.nonNull(resultSet2.getString(1))) {
							processingFromFee = Integer.parseInt(resultSet2.getString(1));
						}
						logger.info("data in Processing fee",processingFromFee);
					}
				}catch (Exception e) {
					logger.info("data in Processing failed",e);
					e.printStackTrace();
				}
			}
			
			if(processingFromFee>processingFromUpfront) {
				processingFee = processingFromUpfront;
			}else if(processingFromFee<processingFromUpfront) {
				processingFee = processingFromFee;
			}else if(processingFromFee!=0 && (processingFromFee==processingFromUpfront)) {
				processingFee = processingFromFee;
			}else {
				processingFee=0;
			}
			
			balancePyable = processingFromFee-processingFromUpfront;
			letterModel.setBalancePayable(getNilValues(balancePyable));
//		if(processingFee==0) {
//			letterModel.setProcessingFee("NIL");
//		}else {
			letterModel.setProcessingFee(getNilValues(processingFee));
//		}
		}catch (Exception e) {
			logger.info("data in Processing failed",e);
			e.printStackTrace();
		}		
	}

	private void fetchOracleDataForMOTD(LetterReportModel letterModel, Map<String, String> prepareStatementList) {
		dynamicDataSourceService.switchToOracleDataSource();
		// Use the current datasource to fetch data
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		LinkedHashSet<PropertyNumberModel> propertyNumberModelList = new LinkedHashSet<PropertyNumberModel>();
		LinkedHashSet<TitleHolderDetail> titleHolderList = new LinkedHashSet<>();
		LinkedHashMap<String,LinkedHashSet<ScheduleA>> scheduleListMap = new LinkedHashMap<>();
		LinkedHashMap<String,ScheduleB> scheduleBListMap= new LinkedHashMap<>();
		LinkedHashMap<String,Boundries> boundriesListMap= new LinkedHashMap<>();
		LinkedHashMap<String,Measurement> measurementListMap= new LinkedHashMap<>();
		LinkedHashSet<LinkedSroDetails> linkedSroDetailsMap= new LinkedHashSet<>();
		LinkedSroDetails linkedSroDetails= new LinkedSroDetails();
		LinkedHashSet<String> sroList = new LinkedHashSet<>();
		PropertyDetailModel propertyDetailModel = new PropertyDetailModel();
		try {
			connection = currentDataSource.getConnection();
			if(Objects.nonNull(letterModel.getRateType())) {
				try(PreparedStatement preparedStatement1 = connection.prepareStatement("Select Rate_Type, Rate_Type_Desc From Sa_Rate_Type_Dir where Rate_Type=?");){
					preparedStatement1.setString(1, letterModel.getRateType());
					ResultSet resultSet1 = preparedStatement1.executeQuery();
					while (resultSet1.next()) {
						letterModel.setRateTypeString(resultSet1.getString(2));
					}
				}catch (Exception e) {
					logger.info("Documentation charges faile",e);
					e.printStackTrace();
				}
			}
			//property main

			try(PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT property_customer_code,property_number FROM cc_property_details"
					+ " where contract_number=? order by property_number");){
				preparedStatement2.setString(1, letterModel.getContractNumber());
				//preparedStatement2.setString(2, letterModel.getCustomerCode());
				try(ResultSet resultSet2 = preparedStatement2.executeQuery();){
					while (resultSet2.next()) {
						PropertyNumberModel propertyNumberModel = new PropertyNumberModel();
						propertyNumberModel.setPropertyCustomerCode(resultSet2.getString(1));
						propertyNumberModel.setPropertyNumber(resultSet2.getInt(2));
						propertyNumberModelList.add(propertyNumberModel);
					}
				}catch (Exception e) {
					logger.info("propertyDetail Number faile",e);
					e.printStackTrace();
				}
			}catch (Exception e) {
				logger.info("propertyDetail Number faile",e);
				e.printStackTrace();
			}
			propertyDetailModel.setPropertyNumberModelList(propertyNumberModelList);
			//share detail
		}catch (Exception e) {
			e.printStackTrace();
			logger.info("motd lettermodel set",e);
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
		if(propertyNumberModelList.isEmpty()) {
			getEmptyDataList(letterModel);
		}else {
			propertyNumberModelList.stream().forEach(propertyNumberModel->{
				try (Connection connection1 = currentDataSource.getConnection()){
					try(PreparedStatement preparedStatement3 = connection1.prepareStatement("Select share_customer_code ,Title_Holder_Name, Customer_Code,property_number"
							+ " From Sa_Customer_Property_Share where customer_code=? and property_number =? order by property_number");){
						preparedStatement3.setString(1, propertyNumberModel.getPropertyCustomerCode());
						preparedStatement3.setInt(2, propertyNumberModel.getPropertyNumber());
						try(ResultSet resultSet3 = preparedStatement3.executeQuery();){
							if(!resultSet3.isBeforeFirst()) {
								getEmptyDataList(letterModel);
								return;
							}
							while (resultSet3.next()) {
								TitleHolderDetail titleHolderDetail = new TitleHolderDetail();
								titleHolderDetail.setCustomerShareCode(resultSet3.getString(1));
								titleHolderDetail.setTitleHolderName(resultSet3.getString(2));
								titleHolderDetail.setCustomerCode(resultSet3.getString(3));
								titleHolderDetail.setPropertyNumber(resultSet3.getInt(4));
								if(Objects.nonNull(titleHolderDetail.getCustomerShareCode())) {
									//title
									getTitleInfo(titleHolderDetail);

									//aadhar
									getAadharForTitle(titleHolderDetail);
									//age
									getAgeForTitle(titleHolderDetail);

									//otdNumber
									getOtdNumber(titleHolderDetail,letterModel);

									//address
									getAddressForTitle(titleHolderDetail,prepareStatementList);

									//getScheduleA
									getScheduleADetail(letterModel,propertyDetailModel,titleHolderDetail,scheduleListMap);

									//schedule B &boundries&measurement
									getschedulBAndBoundriyMeasurment(letterModel,titleHolderDetail,boundriesListMap,measurementListMap,scheduleBListMap,sroList);
								}
								titleHolderList.add(titleHolderDetail);
							}
						}
					}
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			String titleHolderCustomerCode = null;
			Optional<TitleHolderDetail> titleHodlerOptional = titleHolderList.stream().findAny();
			if(titleHodlerOptional.isPresent()) {
				titleHolderCustomerCode = titleHodlerOptional.get().getCustomerCode();
			}
			if(Objects.nonNull(titleHolderCustomerCode)) {
				//linkdate and SRO
				getSroDetails(letterModel,linkedSroDetails);
				propertyDetailModel.setTitleHolderDetailList(titleHolderList);
				propertyDetailModel.setScheduleListMap(scheduleListMap);
				propertyDetailModel.setScheduleBListMap(scheduleBListMap);
				propertyDetailModel.setMeasurementListMap(measurementListMap);
				propertyDetailModel.setBoundriesListMap(boundriesListMap);
				letterModel.setPropertyDetailModel(propertyDetailModel);
				letterModel.setSroList(sroList);
				letterModel.setLinkedSroDetails(linkedSroDetails);
			}else {
				getEmptyDataList(letterModel);
			}
		}

	}

	private void getEmptyDataList(LetterReportModel letterModel) {
		PropertyDetailModel propertyDetailModel = new PropertyDetailModel();
		//titleholder
		LinkedHashSet<TitleHolderDetail> titleHolderList = new LinkedHashSet<TitleHolderDetail>();
		titleHolderList.add(new TitleHolderDetail()); 


		LinkedHashMap<String,LinkedHashSet<ScheduleA>> scheduleListMap = new LinkedHashMap<String,LinkedHashSet<ScheduleA>>();
		ScheduleA scheduleA = new ScheduleA();
		LinkedHashSet<ScheduleA> scheduleASet = new LinkedHashSet<>();
		scheduleASet.add(scheduleA);
		scheduleListMap.put("", scheduleASet);

		LinkedHashMap<String,ScheduleB> scheduleBListMap= new LinkedHashMap<String,ScheduleB>();
		ScheduleB scheduleB = new ScheduleB();
		PropertyAddress propertyAddress = new PropertyAddress();
		scheduleB.setPropertyAddress(propertyAddress);
		scheduleBListMap.put("", scheduleB);

		LinkedHashMap<String,Boundries> boundriesListMap= new LinkedHashMap<String,Boundries>();
		Boundries boundries = new Boundries();
		boundriesListMap.put("", boundries);

		LinkedHashMap<String,Measurement> measurementListMap= new LinkedHashMap<String,Measurement>();
		Measurement measurement = new Measurement();
		measurementListMap.put("", measurement);


		LinkedSroDetails linkedSroDetails= new LinkedSroDetails();
		LinkedHashSet<String> sroList = new LinkedHashSet<>();
		propertyDetailModel.setTitleHolderDetailList(titleHolderList);
		propertyDetailModel.setScheduleListMap(scheduleListMap);
		propertyDetailModel.setScheduleBListMap(scheduleBListMap);
		propertyDetailModel.setMeasurementListMap(measurementListMap);
		propertyDetailModel.setBoundriesListMap(boundriesListMap);
		letterModel.setPropertyDetailModel(propertyDetailModel);
		letterModel.setSroList(sroList);
		letterModel.setLinkedSroDetails(linkedSroDetails);
	}

	private void getOtdNumber(TitleHolderDetail titleHolderDetail, LetterReportModel letterModel) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		try {
			connection = currentDataSource.getConnection();
			try(PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT  A.DOCUMENT_NUMBER FROM DC_DOCUMENT_PROCESSING A,SA_DOCUMENT_TYPE_MASTER B"
					+ " WHERE A.CONTRACT_NUMBER=?"
					+ "  AND A.ENTITY_CODE=? AND A.ASSET_NUMBER=?"
					+ "   AND A.DOCUMENT_NUMBER IS NOT NULL"
					+ "   AND A.DOCUMENT_ID IN (1,9,33)"
					+ "   AND B.DTY_DOCUMENT_ID = A.DOCUMENT_ID"
					+ "   AND B.DTY_DOCUMENT_TYPE=A.DOCUMENT_TYPE  AND ROWNUM =1");){
				preparedStatement1.setString(1, letterModel.getContractNumber());
				preparedStatement1.setString(2, titleHolderDetail.getCustomerShareCode());
				preparedStatement1.setInt(3, titleHolderDetail.getPropertyNumber());
				try(ResultSet resultSet1 = preparedStatement1.executeQuery();){
					while (resultSet1.next()) {
						titleHolderDetail.setOtdNumber(resultSet1.getString(1));
					}
				}
			}
		}catch (Exception e) {
			logger.info("link sro fails" ,e);
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

		try {
			connection = currentDataSource.getConnection();
			String sql = "SELECT A.Contract_Number, A.Customer_Code, A.Sro, " +
					"(SELECT C.Glpd_Geo_Level_Desc FROM Sa_Geo_Level_Property_Details C " +
					"WHERE C.Glpd_Geo_Level_Number = 2 AND C.Glpd_Geo_Level_String || ':' || C.Glpd_Geo_Level_Code = Srodistrict) AS Srodistrict " +
					"FROM Cc_Property_Category_Details A " +
					"WHERE A.contract_Number = ? AND A.property_Number = ? AND A.customer_Code = ? " +
					"UNION " +
					"SELECT A.Contract_Number, A.Customer_Code, A.Sro, " +
					"(SELECT C.Glpd_Geo_Level_Desc FROM Sa_Geo_Level_Property_Details C " +
					"WHERE C.Glpd_Geo_Level_Number = 2 AND C.Glpd_Geo_Level_String || ':' || C.Glpd_Geo_Level_Code = Srodistrict) AS Srodistrict " +
					"FROM Cc_Property_Category_Details_H A " +
					"WHERE A.contract_Number = ? AND A.property_Number = ? AND A.customer_Code = ? " +
					"AND A.Txn_Id = (SELECT MAX(Txn_Id) FROM Cc_Property_Category_Details_H B " +
					"WHERE B.contract_Number = ? AND B.property_Number = ? AND B.customer_Code = ?) " +
					"AND A.contract_Number NOT IN (" +
					"SELECT C.contract_Number FROM Cc_Property_Category_Details C " +
					"WHERE C.contract_Number = ? AND C.property_Number = ? AND C.customer_Code = ?)";
			try(PreparedStatement preparedStatement1 = connection.prepareStatement(sql);){

				preparedStatement1.setString(1, letterModel.getContractNumber());
				preparedStatement1.setInt(2, titleHolderDetail.getPropertyNumber());
				preparedStatement1.setString(3, titleHolderDetail.getCustomerShareCode());
				preparedStatement1.setString(4, letterModel.getContractNumber());
				preparedStatement1.setInt(5, titleHolderDetail.getPropertyNumber());
				preparedStatement1.setString(6, titleHolderDetail.getCustomerShareCode());
				preparedStatement1.setString(7, letterModel.getContractNumber());
				preparedStatement1.setInt(8, titleHolderDetail.getPropertyNumber());
				preparedStatement1.setString(9, titleHolderDetail.getCustomerShareCode());
				preparedStatement1.setString(10, letterModel.getContractNumber());
				preparedStatement1.setInt(11, titleHolderDetail.getPropertyNumber());
				preparedStatement1.setString(12, titleHolderDetail.getCustomerShareCode());
				try(ResultSet resultSet1 = preparedStatement1.executeQuery();){
					while (resultSet1.next()) {
						titleHolderDetail.setSro(resultSet1.getString(3));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
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

	private void getSroDetails(LetterReportModel letterModel,LinkedSroDetails linkedSroDetails) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;

		try {
			connection = currentDataSource.getConnection();
			try {
				try(PreparedStatement preparedStatement = connection.prepareStatement("SELECT A.Document_Number,"
						+ "  A.Document_Date,A.ENTITY_CODE,A.ASSET_NUMBER,B.DTY_DOCUMENT_ID,DTY_DOCUMENT_TYPE"
						+ "  FROM Dc_Document_Processing a,Sa_Document_Type_Master b"
						+ " WHERE A.Contract_Number=?"
						+ "   AND A.DOCUMENT_NUMBER IS NOT NULL"
						+ "   AND DTY_DOCUMENT_ID =13 AND DTY_DOCUMENT_TYPE ='AL'"
						+ "   And B.Dty_Document_Id = A.Document_Id"
						+ "   AND VERIFICATION_DATE IS NOT NULL"
						+ "   And B.DTY_DOCUMENT_TYPE=A.Document_Type AND ROWNUM =1");){
					preparedStatement.setString(1, letterModel.getContractNumber());
					try(ResultSet resultSet = preparedStatement.executeQuery();){
						if(!resultSet.isBeforeFirst()) {
							//linkedSroDetails = null;
						}else {
							while (resultSet.next()) {
								linkedSroDetails.setLinkedDocumentNumber(resultSet.getString(1));
								linkedSroDetails.setLinkedDocumentDate(resultSet.getString(2));
								linkedSroDetails.setLinkedCustomerCode(resultSet.getString(3));
								linkedSroDetails.setLinkedPropertyNumber(resultSet.getInt(4));
							}
						}
					}catch (Exception e) {
						e.printStackTrace();
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}

			try {
				String sql = "SELECT A.Contract_Number, A.Customer_Code, A.Sro, " +
						"(SELECT C.Glpd_Geo_Level_Desc FROM Sa_Geo_Level_Property_Details C " +
						"WHERE C.Glpd_Geo_Level_Number = 2 AND C.Glpd_Geo_Level_String || ':' || C.Glpd_Geo_Level_Code = Srodistrict) AS Srodistrict " +
						"FROM Cc_Property_Category_Details A " +
						"WHERE A.contract_Number = ? AND A.property_Number = ? AND A.customer_Code = ? " +
						"UNION " +
						"SELECT A.Contract_Number, A.Customer_Code, A.Sro, " +
						"(SELECT C.Glpd_Geo_Level_Desc FROM Sa_Geo_Level_Property_Details C " +
						"WHERE C.Glpd_Geo_Level_Number = 2 AND C.Glpd_Geo_Level_String || ':' || C.Glpd_Geo_Level_Code = Srodistrict) AS Srodistrict " +
						"FROM Cc_Property_Category_Details_H A " +
						"WHERE A.contract_Number = ? AND A.property_Number = ? AND A.customer_Code = ? " +
						"AND A.Txn_Id = (SELECT MAX(Txn_Id) FROM Cc_Property_Category_Details_H B " +
						"WHERE B.contract_Number = ? AND B.property_Number = ? AND B.customer_Code = ?) " +
						"AND A.contract_Number NOT IN (" +
						"SELECT C.contract_Number FROM Cc_Property_Category_Details C " +
						"WHERE C.contract_Number = ? AND C.property_Number = ? AND C.customer_Code = ?)";
				try(PreparedStatement preparedStatement1 = connection.prepareStatement(sql);){
					preparedStatement1.setString(1, letterModel.getContractNumber());
					preparedStatement1.setInt(2, Objects.nonNull(linkedSroDetails)?(linkedSroDetails.getLinkedPropertyNumber()):0);
					preparedStatement1.setString(3, linkedSroDetails.getLinkedCustomerCode());
					preparedStatement1.setString(4, letterModel.getContractNumber());
					preparedStatement1.setInt(5, Objects.nonNull(linkedSroDetails)?(linkedSroDetails.getLinkedPropertyNumber()):0);
					preparedStatement1.setString(6, linkedSroDetails.getLinkedCustomerCode());
					preparedStatement1.setString(7, letterModel.getContractNumber());
					preparedStatement1.setInt(8, Objects.nonNull(linkedSroDetails)?(linkedSroDetails.getLinkedPropertyNumber()):0);
					preparedStatement1.setString(9, linkedSroDetails.getLinkedCustomerCode());
					preparedStatement1.setString(10, letterModel.getContractNumber());
					preparedStatement1.setInt(11, Objects.nonNull(linkedSroDetails)?(linkedSroDetails.getLinkedPropertyNumber()):0);
					preparedStatement1.setString(12, linkedSroDetails.getLinkedCustomerCode());
					try(ResultSet resultSet1 = preparedStatement1.executeQuery();){
						while (resultSet1.next()) {
							linkedSroDetails.setLinkedSro(resultSet1.getString(3));
							linkedSroDetails.setLinkedSroDistrict(resultSet1.getString(4));
						}
					}catch (Exception e) {
						e.printStackTrace();
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}

		}catch (Exception e) {
			logger.info("link sro fails" ,e);
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
	}

	private void getschedulBAndBoundriyMeasurment(LetterReportModel letterModel, TitleHolderDetail titleHolderDetail, LinkedHashMap<String, Boundries> boundriesListMap, LinkedHashMap<String, Measurement> measurementListMap, LinkedHashMap<String, ScheduleB> scheduleBListMap, LinkedHashSet<String> sroList) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		String combinationKey = titleHolderDetail.getPropertyNumber()+"-"+titleHolderDetail.getCustomerShareCode();
		Boundries boundries = new Boundries();
		Measurement measurement = new Measurement();
		ScheduleB scheduleB = new ScheduleB();
		PropertyAddress propertyAddress = new PropertyAddress();
		try {
			connection = currentDataSource.getConnection();
			Connection connection3 = currentDataSource.getConnection();
			try(PreparedStatement preparedStatement10 = connection3.prepareStatement("SELECT a.door_no, a.plot, a.survey,a.addl_survey,"
					+ " (SELECT glpd_geo_level_desc FROM sa_geo_level_property_details WHERE glpd_geo_level_string IS NULL"
					+ "  AND glpd_geo_level_code = TO_NUMBER (a.state_name)) State_Name,"
					+ "  (SELECT glpd_geo_level_desc  FROM sa_geo_level_property_details WHERE glpd_geo_level_string IS NOT NULL"
					+ " AND glpd_geo_level_string = TO_CHAR (a.state_name) AND glpd_geo_level_code ="
					+ " TO_NUMBER (SUBSTR (a.district, INSTR (a.district, ':', 1) +1))) District,"
					+ " (SELECT glpd_geo_level_desc FROM sa_geo_level_property_details WHERE glpd_geo_level_string IS NOT NULL"
					+ " AND glpd_geo_level_string = district AND glpd_geo_level_code = TO_NUMBER (SUBSTR (a.taluk_tehsil, INSTR (a.taluk_tehsil,':', 1,2)+ 1))) Taluk,"
					+ "  a.town,(SELECT glpd_geo_level_desc FROM sa_geo_level_property_details WHERE glpd_geo_level_string IS NOT NULL AND glpd_geo_level_string = taluk_tehsil"
					+ "  AND glpd_geo_level_code = TO_NUMBER (SUBSTR (a.village,INSTR (a.village, ':', 1, 3)+ 1))) Village,"
					+ " a.building_society_name, (SELECT glpd_geo_level_desc FROM sa_geo_level_property_details WHERE glpd_geo_level_string IS NOT NULL"
					+ " AND glpd_geo_level_string = TO_CHAR (a.state_name) AND glpd_geo_level_code =TO_NUMBER (SUBSTR (a.SRODISTRICT,INSTR (a.SRODISTRICT, ':', 1) + 1))) Sro_District"
					+",a.sro"
					+ " FROM cc_property_category_details a"
					+ " WHERE a.contract_number = ? and a.customer_code=? and a.property_number=?");){

				preparedStatement10.setString(1, letterModel.getContractNumber());
				preparedStatement10.setString(2, titleHolderDetail.getCustomerShareCode());
				preparedStatement10.setInt(3,titleHolderDetail.getPropertyNumber());
				try(ResultSet resultSet10 = preparedStatement10.executeQuery();){
					if(!resultSet10.isBeforeFirst()) {
						scheduleB = null;
					}else {
						while (resultSet10.next()) {
							//
							scheduleB.setDoorNo(resultSet10.getString(1));
							scheduleB.setPlotNo(resultSet10.getString(2));
							scheduleB.setSurveyNo(resultSet10.getString(3));
							scheduleB.setAddlSurveyNo(resultSet10.getString(4));
							scheduleB.setStateName(resultSet10.getString(5));
							scheduleB.setDistrict(resultSet10.getString(6));					
							scheduleB.setTaluk(resultSet10.getString(7));
							scheduleB.setTown(resultSet10.getString(8));
							scheduleB.setVillage(resultSet10.getString(9));
							scheduleB.setBuildingSocietyName(resultSet10.getString(10));
							scheduleB.setSroDistrict(resultSet10.getString(11));
							scheduleB.setSro(resultSet10.getString(12));
							sroList.add(resultSet10.getString(12));

							//propertyAddress
							try(PreparedStatement preparedStatement11 = connection3.prepareStatement("Select A.Property_Address.Street_L,A.Property_Address.Column1_L,"
									+ "	A.Property_Address.Column2_L,A.Property_Address.Column3_L,"
									+ "	A.Property_Address.Column4_L,A.Property_Address.Column5_L,"
									+ "	A.Property_Address.Column6_L,A.Property_Address.Column7_L,"
									+ "	A.Property_Address.Column8_L,A.Property_Address.Column9_L,"
									+ "	A.Property_Address.Column10_L,A.Property_Address.Pin_Zip_Code_L,"
									+ "	A.Property_Address.Office_Phone_No,A.Property_Address.Residence_Phone_No,"
									+ "	A.Property_Address.Office_Fax_No,A.Property_Address.Residence_Fax_No,"
									+ "	A.Property_Address.Mobile_No,A.Property_Address.Pager_No,"
									+ "	A.Property_Address.Email,A.Land_Area_Sq_Ft,A.flat_no,A.flat_floor_no,A.flat_remarks,A.project_code"
									+ "	From Sa_Customer_Property_Dtls A where customer_code=? and property_number=?");){

								preparedStatement11.setString(1, titleHolderDetail.getCustomerShareCode());
								preparedStatement11.setInt(2, titleHolderDetail.getPropertyNumber());
								try(ResultSet resultSet11 = preparedStatement11.executeQuery();){
									while (resultSet11.next()) {
										propertyAddress.setProjectCode(resultSet11.getString(24));
										if(Objects.nonNull(propertyAddress.getProjectCode())) {
											//propertyAddress
											try(PreparedStatement preparedStatement16 = connection3.prepareStatement("Select A.Project_Address.Street_L,A.Project_Address.Column1_L,"
													+ "	A.Project_Address.Column2_L,A.Project_Address.Column3_L,"
													+ "	A.Project_Address.Column4_L,A.Project_Address.Column5_L,"
													+ "	A.Project_Address.Column6_L,A.Project_Address.Column7_L,"
													+ "	A.Project_Address.Column8_L,A.Project_Address.Column9_L,"
													+ "	A.Project_Address.Column10_L,A.Project_Address.Pin_Zip_Code_L,"
													+ "	A.Project_Address.Office_Phone_No,A.Project_Address.Residence_Phone_No,"
													+ "	A.Project_Address.Office_Fax_No,A.Project_Address.Residence_Fax_No,"
													+ "	A.Project_Address.Mobile_No,A.Project_Address.Pager_No,"
													+ "	A.Project_Address.Email,A.remarks"
													+ "	From SA_PROJECT_MASTER_HDR A where project_code=?");){
												preparedStatement16.setString(1,propertyAddress.getProjectCode());
												try(ResultSet resultSet16 = preparedStatement16.executeQuery();){
													while(resultSet16.next()) {
														propertyAddress.setStreet(resultSet16.getString(1));
														propertyAddress.setAddress1(resultSet16.getString(2));
														propertyAddress.setAddress2(resultSet16.getString(3));
														propertyAddress.setAddress3(resultSet16.getString(4));
														propertyAddress.setAddress4(resultSet16.getString(5));
														propertyAddress.setAddress5(resultSet16.getString(6));
														propertyAddress.setAddress6(resultSet16.getString(7));
														propertyAddress.setAddress7(resultSet16.getString(8));
														propertyAddress.setAddress8(resultSet16.getString(9));
														propertyAddress.setAddress9(resultSet16.getString(10));
														propertyAddress.setAddress10(resultSet16.getString(11));
														propertyAddress.setPinCode(resultSet16.getString(12));
														propertyAddress.setOfficePhoneNo(resultSet16.getString(13));
														propertyAddress.setResidencePhoneNo(resultSet16.getString(14));
														propertyAddress.setOfficeFaxNo(resultSet16.getString(15));
														propertyAddress.setResidenceFaxNo(resultSet16.getString(16));
														propertyAddress.setMobileNo(resultSet16.getString(17));
														propertyAddress.setPagerNo(resultSet16.getString(18));
														propertyAddress.setEmail(resultSet16.getString(19));
														propertyAddress.setLandExtent(resultSet11.getString(20));
														propertyAddress.setFlatNo(resultSet11.getString(21));
														propertyAddress.setFloorNo(resultSet11.getString(22));
														propertyAddress.setBlock(resultSet11.getString(23));
													}
												}catch (Exception e) {
													e.printStackTrace();
												}
											}catch (Exception e) {
												e.printStackTrace();
											}
										}else {
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
											propertyAddress.setProjectCode(resultSet11.getString(24));
										}

										try(PreparedStatement preparedStatement12 = connection3.prepareStatement("Select City_Name"
												+ "   From Hfs_Vw_City"
												+ "   Where City_Code = ?"
												+ "   And State_Record_Id ="
												+ "   (Select Record_Id"
												+ "   From Hfs_Vw_State"
												+ "   Where State_Code = ?"
												+ "   And Country_Code = ?)");){

											preparedStatement12.setString(1, propertyAddress.getAddress4());
											preparedStatement12.setString(2, propertyAddress.getAddress3());
											preparedStatement12.setString(3, propertyAddress.getAddress2());
											try(ResultSet resultSet12 = preparedStatement12.executeQuery();){
												while (resultSet12.next()) {
													propertyAddress.setCityName(resultSet12.getString(1));
												}
											}catch (Exception e) {
												e.printStackTrace();
											}
										}catch (Exception e) {
											e.printStackTrace();
										}
										try(PreparedStatement preparedStatement13 = connection3.prepareStatement("Select Location_Name"
												+ "   From Hfs_Vw_Postal_Code"
												+ "   Where Location_Code =?"
												+ "   And City_Code = ?"
												+ "   And State_Code = ?"
												+ "   And Country_Code = ?");){
											preparedStatement13.setString(1, propertyAddress.getAddress5());
											preparedStatement13.setString(2, propertyAddress.getAddress4());
											preparedStatement13.setString(3, propertyAddress.getAddress3());
											preparedStatement13.setString(4, propertyAddress.getAddress2());
											try(ResultSet resultSet13 = preparedStatement13.executeQuery();){
												while (resultSet13.next()) {
													propertyAddress.setLocationName(resultSet13.getString(1));
												}
											}catch (Exception e) {
												e.printStackTrace();
											}

										}catch (Exception e) {
											e.printStackTrace();
										}
									}
								}catch (Exception e) {
									e.printStackTrace();
								}
							}catch (Exception e) {
								e.printStackTrace();
							}

						}
					}
					scheduleB.setPropertyAddress(propertyAddress);
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}


			try(PreparedStatement preparedStatement12 = connection3.prepareStatement("Select Contract_Number,Bounded_North,"
					+ "	 Bounded_South, Bounded_East, Bounded_West"
					+ "	 From cc_property_category_details where contract_number=?"
					+ "	 and customer_code=? and property_number=?");){
				preparedStatement12.setString(1, letterModel.getContractNumber());
				preparedStatement12.setString(2, titleHolderDetail.getCustomerShareCode());
				preparedStatement12.setInt(3, titleHolderDetail.getPropertyNumber());
				try(ResultSet resultSet12 = preparedStatement12.executeQuery();){
					if(!resultSet12.isBeforeFirst()) {
						boundries = null;
					}else {
						while (resultSet12.next()) {
							boundries.setNorthBoundry(resultSet12.getString(2));
							boundries.setSouthBoundry(resultSet12.getString(3));
							boundries.setEastBoundry(resultSet12.getString(4));
							boundries.setWestBoundry(resultSet12.getString(5));
						}
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}

			try(PreparedStatement preparedStatement13 = connection3.prepareStatement("SELECT A.PROJECT_NAME.NAME_3_L project_name,"
					+ "project_code  FROM sa_project_master_hdr A where project_code=?");){
				preparedStatement13.setString(1, propertyAddress.getProjectCode());
				try(ResultSet resultSet13 = preparedStatement13.executeQuery();){
					while (resultSet13.next()) {
						scheduleB.setProjectName(resultSet13.getString(1));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}

			}catch (Exception e) {
				e.printStackTrace();
			}

			try(PreparedStatement preparedStatement14 = connection3.prepareStatement("Select North_By,South_By,East_By,West_By,"
					+ "North_By_Measurements,South_By_Measurements,"
					+ "East_By_Measurements,West_By_Measurements "
					+ "From Cc_Technical_Valuation_Report where contract_number=? and property_number =? and property_cust_code=?");){

				preparedStatement14.setString(1, letterModel.getContractNumber());
				preparedStatement14.setInt(2, titleHolderDetail.getPropertyNumber());
				preparedStatement14.setString(3, titleHolderDetail.getCustomerShareCode());
				try(ResultSet resultSet14 = preparedStatement14.executeQuery();){
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
				}
			}
			if(Objects.nonNull(scheduleB)) {
				boundriesListMap.put(combinationKey,boundries);
				measurementListMap.put(combinationKey,measurement);
			}
			scheduleBListMap.put(combinationKey,scheduleB);
		}catch (Exception e) {
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
	}

	private void getScheduleADetail(LetterReportModel letterModel, PropertyDetailModel propertyDetailModel, TitleHolderDetail titleHolderDetail, LinkedHashMap<String, LinkedHashSet<ScheduleA>> scheduleListMap) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();

		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		ScheduleA scheduleA = null;
		LinkedHashSet<ScheduleA> scheduleAList = new LinkedHashSet<>();
		try {
			connection = currentDataSource.getConnection();
			String combinationKey = titleHolderDetail.getPropertyNumber()+"-"+titleHolderDetail.getCustomerShareCode();
			try(PreparedStatement preparedStatement8 = connection.prepareStatement(" SELECT  B.Dty_Document_Desc Document_Name,A.Document_Number, A.Document_Date,A.Title_Holder_Name,A.Document_Id"
					+ " FROM Dc_Document_Processing a,Sa_Document_Type_Master b "
					+ "  WHERE A.Contract_Number=? AND a.entity_code=?"
					+ "  AND a.ASSET_NUMBER=? And B.Dty_Document_Id = A.Document_Id And B.DTY_DOCUMENT_TYPE=A.Document_Type");){
				preparedStatement8.setString(1, letterModel.getContractNumber());
				preparedStatement8.setString(2, titleHolderDetail.getCustomerShareCode());
				preparedStatement8.setInt(3, titleHolderDetail.getPropertyNumber());
				try(ResultSet resultSet8 = preparedStatement8.executeQuery();){
					if(!resultSet8.isBeforeFirst()) {
						scheduleA = null;
					}else {
						while(resultSet8.next()) {
							scheduleA = new ScheduleA();
							scheduleA.setDocumentName(resultSet8.getString(1));
							scheduleA.setDocuemntNumber(resultSet8.getString(2));
							scheduleA.setDocumentDate(resultSet8.getString(3));
							scheduleA.setTitleHolderName(getStringFromObject(resultSet8.getString(4)));
							scheduleA.setDocumentId(resultSet8.getString(5));
							scheduleAList.add(scheduleA);
						}
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
			scheduleListMap.put(combinationKey, scheduleAList);
		}catch (Exception e) {
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
	}

	private void getAddressForTitle(TitleHolderDetail titleHolderDetail, Map<String, String> prepareStatementList) {
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		CustomerAddress customerAddress = new CustomerAddress();
		try (Connection connection = currentDataSource.getConnection()){
			String sql18 = prepareStatementList.get("preparedStatement18");
			try(PreparedStatement preparedStatement7 =connection.prepareStatement(sql18);){
				preparedStatement7.setString(1, titleHolderDetail.getCustomerShareCode());
				try(ResultSet resultSet7 = preparedStatement7.executeQuery();){
					while (resultSet7.next()) {
						customerAddress.setStreet(resultSet7.getString(1));
						customerAddress.setAddress1(resultSet7.getString(2));
						customerAddress.setAddress2(resultSet7.getString(3));
						customerAddress.setAddress3(resultSet7.getString(4));
						customerAddress.setAddress4(resultSet7.getString(5));
						customerAddress.setAddress5(resultSet7.getString(6));
						customerAddress.setAddress7(resultSet7.getString(7));
						customerAddress.setZipCode(resultSet7.getString(8));
					}
					setOtherAddress(customerAddress,prepareStatementList,titleHolderDetail);
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
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
			try(PreparedStatement preparedStatement6 = connection.prepareStatement("Select Hcoi_Dob Dob From Hfs_Customer_Other_Info where hcoi_customer_code =?");){
				preparedStatement6.setString(1, titleHolderDetail.getCustomerShareCode());
				try(ResultSet resultSet6 = preparedStatement6.executeQuery();){
					while (resultSet6.next()) {
						titleHolderDetail.setDateOfBirth(resultSet6.getString(1));
						int age =getAgeFromDate(titleHolderDetail);
						titleHolderDetail.setAge(age);
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
		}catch (Exception e) {
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
	}

	private void getAadharForTitle(TitleHolderDetail titleHolderDetail) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		try {
			connection = currentDataSource.getConnection();
			try(PreparedStatement preparedStatement5 = connection.prepareStatement("SELECT CUM_AADHAR_NO FROM sa_customer_master where cum_customer_code=?");){
				preparedStatement5.setString(1, titleHolderDetail.getCustomerShareCode());
				try(ResultSet resultSet5 = preparedStatement5.executeQuery();){
					while (resultSet5.next()) {
						titleHolderDetail.setTitleAadharNo(resultSet5.getString(1));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
		}catch (Exception e) {
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
	}

	private void getTitleInfo(TitleHolderDetail titleHolderDetail) {
		// Use the current datasource to fetch data
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		try {
			connection = currentDataSource.getConnection();
			try(PreparedStatement preparedStatement4 = connection.prepareStatement("SELECT A.CUM_NAME_INFO.NAME_1_L"
					+ " FROM Sa_Customer_Master A Where CUM_Customer_Code = ?");){
				preparedStatement4.setString(1, titleHolderDetail.getCustomerShareCode());
				try(ResultSet resultSet4 = preparedStatement4.executeQuery();){
					while (resultSet4.next()) {
						titleHolderDetail.setTitle(resultSet4.getString(1));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
		}catch (Exception e) {
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
	}

	private int getAgeFromDate(TitleHolderDetail titleHolderDetail) {
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDate inputDate = LocalDate.parse(titleHolderDetail.getDateOfBirth(),format);
		LocalDate currentDate = LocalDate.now();
		int age =  Period.between(inputDate, currentDate).getYears();
		return age;

	}

	private void setOtherAddress(CustomerAddress customerAddress,  Map<String, String> prepareStatementList, TitleHolderDetail titleHolderDetail) {

		String sql19 = prepareStatementList.get("preparedStatement19");
		String sql20 = prepareStatementList.get("preparedStatement20");
		String sql21 = prepareStatementList.get("preparedStatement21");
		String sql22 = prepareStatementList.get("preparedStatement22");
		dynamicDataSourceService.switchToOracleDataSource();
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		try(Connection connection =currentDataSource.getConnection()) {
			try(PreparedStatement preparedStatement19 =  connection.prepareStatement(sql19);){
				preparedStatement19.setString(1, customerAddress.getAddress5());
				preparedStatement19.setString(2, customerAddress.getAddress2());
				preparedStatement19.setString(3, customerAddress.getAddress3());
				preparedStatement19.setString(4, customerAddress.getAddress4());
				try(ResultSet resultSet19 = preparedStatement19.executeQuery();){
					while (resultSet19.next()) {
						customerAddress.setLocation(resultSet19.getString(1));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}

			try(PreparedStatement preparedStatement20 =connection.prepareStatement(sql20);){
				preparedStatement20.setString(1, customerAddress.getAddress4());
				preparedStatement20.setString(2, customerAddress.getAddress3());
				preparedStatement20.setString(3, customerAddress.getAddress2());
				try(ResultSet resultSet20 = preparedStatement20.executeQuery();){
					while (resultSet20.next()) {
						customerAddress.setCity(resultSet20.getString(1));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
			try(PreparedStatement preparedStatement21 = connection.prepareStatement(sql21)){
				preparedStatement21.setString(1, customerAddress.getAddress3());
				preparedStatement21.setString(2, customerAddress.getAddress2());
				try(ResultSet resultSet21 = preparedStatement21.executeQuery();){
					while (resultSet21.next()) {
						customerAddress.setState(resultSet21.getString(1));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
			try(PreparedStatement preparedStatement22 = connection.prepareStatement(sql22)){
				preparedStatement22.setString(1, customerAddress.getAddress2());
				try(ResultSet resultSet22 = preparedStatement22.executeQuery();){
					while (resultSet22.next()) {
						customerAddress.setCountry(resultSet22.getString(1));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
			appendTitleHolderAddress(customerAddress,titleHolderDetail);
		}catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void appendTitleHolderAddress(CustomerAddress customerAddress, TitleHolderDetail titleHolderDetail) {
		String customerAddressString=getStringFromObject(customerAddress.getStreet())+","+getStringFromObject(customerAddress.getAddress1())+","+getStringFromObject(customerAddress.getAddress7())+","+getStringFromObject(customerAddress.getLocation())+
				","+getStringFromObject(customerAddress.getCity())+"-"+getStringFromObject(customerAddress.getZipCode())+","+getStringFromObject(customerAddress.getState())+","+getStringFromObject(customerAddress.getCountry());
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
		String documentationCharges = "0";
		if(Objects.nonNull(letterModel.getFlatFee())&&
				Integer.parseInt(letterModel.getFlatFee())>0) {
			documentationCharges = letterModel.getFlatFee();
			logger.info("Documentation charges flatfee",letterModel);
		}else if(Objects.nonNull(letterModel.getFlatRate())&&
				Integer.parseInt(letterModel.getFlatRate())>0) {
			int processingFee =  Integer.parseInt(letterModel.getFlatRate())*(letterModel.getAmountFinanced());
			documentationCharges = String.valueOf(processingFee);
			logger.info("Documentation charges flatrate",letterModel);
		}
		letterModel.setDocumentationCharges(documentationCharges);

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
		} if(Objects.nonNull(branchAddress.getAddress7())) {
			brnachAddressString = brnachAddressString+","+" "+branchAddress.getAddress7();
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
		//String query1="SELECT CONTRACT_NUMBER FROM CC_CONTRACT_MASTER WHERE CONTRACT_STATUS=1 AND CONTRACT_NUMBER IS NOT NULL ";
		String query1="SELECT DISTINCT(A.CONTRACT_NUMBER) from cc_contract_stage_details A,CC_CONTRACT_MASTER B  where A.CONTRACT_NUMBER is not null AND A.STATUS =1 AND A.CONTRACT_NUMBER = B.CONTRACT_NUMBER and B.CONTRACT_STATUS=1";
		DataSource currentDataSource = dynamicDataSourceService.getCurrentDataSource();
		Connection connection = null;
		try  {
			connection = currentDataSource.getConnection();
			try(PreparedStatement preparedStatement = connection.prepareStatement(query1);){
				try(ResultSet resultSet = preparedStatement.executeQuery();){
					while (resultSet.next()) {
						applicationNumberList.add(resultSet.getString(1));
					}
				}catch (SQLException e) {
					e.printStackTrace();
				}
			}catch (SQLException e) {
				e.printStackTrace();
			}

		} catch (SQLException e) {
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
				letterModel.setApplicant(getStringFromObject(returnResponse.get("customerName")));
				letterModel.setCoApplicant1(getStringFromObject(returnResponse.get("coApplicantName")));
				letterModel.setCurrentDate(formatter.format(date));
				letterModel.setBranchCode(getStringFromObject(returnResponse.get("branchCode")));
				letterModel.setAmountFinanced(convertRoundedValue(getStringFromObject(returnResponse.get("sanctionAmt"))));
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
				//get processingfee & documentation charges&life_insurance
				getFeeDataForLetterGeneration(dataMap,letterModel);

				//				try {
				//					logger.info("balancePayable method started");
				//					// Amort Calculation for Balance Payable
				//					Calendar calendar = Calendar.getInstance();
				//					Date currentDate = getDate(calendar.getTime());
				//					calendar.set(Calendar.DATE, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
				//					DateFormat dateFormatforReqDate = new SimpleDateFormat("YYYY/MM/DD");
				//					Date dates = new Date();
				//					String dateValue = dateFormatforReqDate.format(dates);
				//					
				//					Date dueStartDate = getDate(calendar.getTime());
				//					dataMap.put("requestedDate", dateValue);
				//					//getbalancePayable(dataMap,letterModel);
				//					 int balancePayable = letterModel.getAmountFinanced()-Integer.parseInt(letterModel.getProcessingFee());
				//					 letterModel.setProcessingFee(String.valueOf(balancePayable));
				//				}catch (Exception e) {
				//					logger.info("balancePayable method faile",e);
				//					e.printStackTrace();
				//				}

				//Cash Handling Charges Calculation
				try {
					logger.info("cashHandlingResponse loop started");
					ResponseEntity<List<CashHandlingChargesModel>> cashHandlingResponse = webClient.get()
							.uri(stlapServerUrl + "/cashHandlingCharges/findByMaxEffectiveDate")
							.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).retrieve()
							.toEntityList(CashHandlingChargesModel.class).block();
					logger.info("CashHandlingChargesModel loop fetched"+cashHandlingResponse);
					List<CashHandlingChargesModel> cashHandlingChargesList = cashHandlingResponse.getBody();
					logger.info("cashHandlingChargesList loop fetched"+cashHandlingChargesList);
					letterModel.setCashHandlingCharges(cashHandlingChargesList);
					logger.info("cashHandlingResponse"+letterModel);
				}catch (Exception e) {
					logger.info("cashHandlingResponse loop failed",e);
					e.printStackTrace();
				}

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





	private void getbalancePayable(Map<String, Object> dataMap, LetterReportModel letterModel) {
		Double balancePayable = 0.0;
		String query = "SELECT CLOSING_BALANCE FROM ST_TB_LMS_DISB_REPAY_SCHEDULE WHERE APPLICATION_NUM =?"
				+ "	AND CONVERT(DATE, due_start_date) <= CONVERT(DATE, ?)"
				+ "	AND CONVERT(DATE, due_end_date) >= CONVERT(DATE, ?)"
				+"Order by due_start_date asc ";
		try(Connection connection = dataSource.getConnection();){
			logger.info("getbalancePayable method started");
			try (PreparedStatement statement = connection.prepareStatement(query)) {
				statement.setString(1, letterModel.getApplicationNumber());
				statement.setString(2, String.valueOf(dataMap.get("requestedDate")));
				statement.setString(3, String.valueOf(dataMap.get("requestedDate")));
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						balancePayable = resultSet.getDouble(1);
					}
					letterModel.setBalancePayable(balancePayable.toString());
					logger.info("getbalancePayable method completed");
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

	private void getAccountNo(LetterReportModel letterModel) {
		String query = "select bank_account_num from st_tb_los_bank_dtl where application_number=? and internal_customer_id=?";
		try(Connection connection = dataSource.getConnection();){
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
		List<Map<String, Object>> returnResponseList = new ArrayList<>();
		String sql = "";
		String value = "";
		Connection connection = null;
		ResultSet resultSet = null;
		try {
			connection = dataSource.getConnection();
			if(Objects.nonNull(model.getApplicationNumber()) && !(model.getApplicationNumber().isEmpty())) {
				sql = "SELECT application_num,customer_id,customer_name,branch_code,loan_amt,sanction_amt,tenure,rate_of_interest,co_applicant_name FROM ST_TB_LOS_CUSTOMER WHERE application_num = ?";
				value = model.getApplicationNumber();
				PreparedStatement statement = connection.prepareStatement(sql);
				statement.setString(1, value);
				resultSet = statement.executeQuery();
			}else if(model.getSanctionDate()!=null){
				sql = "SELECT application_num,customer_id,customer_name,branch_code,loan_amt,sanction_amt,tenure,rate_of_interest,co_applicant_name FROM ST_TB_LOS_CUSTOMER "
						+ "WHERE FORMAT(effective_date,'dd/MM/yyyy')='" + String.valueOf(model.getSanctionDate()) + "'";
				PreparedStatement statement = connection.prepareStatement(sql);
				resultSet = statement.executeQuery();
			}else {
				return returnResponseList;
			}
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
				responseMap.put("coApplicantName", resultSet.getString(8));
				returnResponseList.add(responseMap);
				logger.info("getcustomerDataFromLos query method ended"+returnResponseList);
			}
		}catch (Exception e) {
			logger.error(e.getMessage());
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
			try {
				if(resultSet!=null) {
					resultSet.close();
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
		return " " + Rupees + paise;
	}

	public void insertProductData() {
		List<String> productList = new ArrayList<>();
		productList.add("HOMEFIN");
		productList.add("STLAP");
		List<LetterProduct> entityList = letterProductRepo.findByProductCodeIn(productList);
		if(entityList.isEmpty()) {
			LetterProduct entity1 = new LetterProduct(1,"HOMEFIN",null,"ORACLE",null);
			LetterProduct entity3 = new LetterProduct(2,"STLAP",null,"MSSQL",null);
			entityList.add(entity3);
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
