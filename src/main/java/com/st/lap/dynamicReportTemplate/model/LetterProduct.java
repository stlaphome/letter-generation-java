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
	@Column(name = "product_id")
	private int productId;
	
	@Column(name = "product_code")
	private String productCode;
	
	@Column(name = "letter_name")
	private Blob letterName;
	
	@Column(name = "data_base")
	private Blob dataBase;
	
	@Column(name = "product_data")
	private Blob productData;


}
