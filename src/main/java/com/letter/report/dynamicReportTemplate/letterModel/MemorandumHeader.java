package com.letter.report.dynamicReportTemplate.letterModel;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemorandumHeader {
	private String memoCode;
	private String txnIndicator;
	private int txnAmt;
}
