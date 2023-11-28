package com.st.lap.dynamicReportTemplate.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.st.lap.dynamicReportTemplate.model.LetterProduct;

@Repository
public interface LetterProductRepo extends JpaRepository<LetterProduct, Integer> {

	List<LetterProduct> findByProductCode(String productType);

	LetterProduct findByProductCodeAndLetterName(String productCode, String templateName);

}
