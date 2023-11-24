package com.st.lap.dynamicReportTemplate.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.st.lap.dynamicReportTemplate.model.CcContractMaster;

public interface CcContractMasterRepo extends JpaRepository<CcContractMaster, String>{
	@Query(nativeQuery = true,value = "SELECT AMOUNT_FINANCED,Purpose_Of_Loan FROM cc_contract_master where Purpose_Of_Loan is not null")
	public List<CcContractMaster> getLoanAmount();
}
