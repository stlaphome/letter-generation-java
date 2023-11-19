package com.st.lap.dynamicReportTemplate.repo;

import org.springframework.stereotype.Repository;

import com.st.lap.dynamicReportTemplate.model.DynamicVariables;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface  DynamicVariablesRepo extends JpaRepository<DynamicVariables, Integer>{
	
	DynamicVariables findByApplicationNumber(String applicationNumber);
}
