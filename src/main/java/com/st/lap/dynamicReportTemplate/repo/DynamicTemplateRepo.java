package com.st.lap.dynamicReportTemplate.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.st.lap.dynamicReportTemplate.model.DynamicTemplate;

@Repository
public interface  DynamicTemplateRepo extends JpaRepository<DynamicTemplate, Integer>{

	Optional<DynamicTemplate> findByTemplateNameAndTemplateKey(String templateName,String templateKey);
	
	List<DynamicTemplate> findByTemplateName(String templateName);
	
	List<DynamicTemplate> findByTemplateNameAndActive(String templateName,Boolean active);
}
