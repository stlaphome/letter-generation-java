package com.letter.report.dynamicReportTemplate.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.letter.report.dynamicReportTemplate.model.DynamicTemplate;

@Repository
public interface  DynamicTemplateRepo extends JpaRepository<DynamicTemplate, Integer>{

	Optional<DynamicTemplate> findByTemplateNameAndTemplateKey(String templateName,String templateKey);
	
	List<DynamicTemplate> findByTemplateName(String templateName);
	
	List<DynamicTemplate> findByTemplateNameAndActive(String templateName,Boolean active);

	List<DynamicTemplate> findByProductCodeAndTemplateName(String string, String string2, Sort sort);

	List<DynamicTemplate> findByProductCodeAndTemplateNameAndActive(String productCode, String templateName, boolean b);

	Optional<DynamicTemplate> findByProductCodeAndTemplateNameAndTemplateKey(String productCode, String templateName,
			String templateKey);
}
