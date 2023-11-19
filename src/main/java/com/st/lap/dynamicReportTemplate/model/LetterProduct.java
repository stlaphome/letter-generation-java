package com.st.lap.dynamicReportTemplate.model;

import java.sql.Blob;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "ST_TB_LMS_LETTER_PRODUCT")
public class LetterProduct {
	
	@Id
	@Column(name = "dynamic_report_container_hdr_key")
	private int ProductId;
	
	@Column(name = "report_file_name")
	private String productCode;
	
	@Column(name = "report_file")
	private Blob letterName;
	
	@Column(name = "report_file")
	private Blob dataBase;
	
	@Column(name = "report_file")
	private Blob productData;


}
