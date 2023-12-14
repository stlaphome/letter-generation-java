package com.st.lap.dynamicReportTemplate.model;

import java.sql.Blob;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "ST_TB_LMS_LETTER_PRODUCT")
@NoArgsConstructor
@AllArgsConstructor
public class LetterProduct {
	
	@Id
	@Column(name = "product_id")
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private int productId;
	
	@Column(name = "product_code")
	private String productCode;
	
	@Column(name = "template_id")
	private String templateId;
	@Column(name = "template_type")
	private String templateType;
	
	@Column(name = "letter_name")
	private String letterName;
	
	@Column(name = "data_base")
	private String dataBase;
	
	@Lob
	@Column(name = "product_data")
	private String productData;


}
