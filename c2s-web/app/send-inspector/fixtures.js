/**
 * Sample SendSubmissions snapshots so the inspector runs without IRS.
 * Packed Return XML is ATS scenario 1 (Orchid). SOAP is a MIME envelope
 * with credentials already omitted — no SAML assertion.
 */

export const SAMPLE_SENDS = [
  {
    submissionId: "2386892026105orchid1",
    capturedAt: "2026-04-15T18:04:12-04:00",
    environment: "ATS",
    einMasked: "*****0004",
    formCode: "941",
    periodLabel: "2026-Q1",
    soapRequestCaptured: true,
    returnXml:
      '<?xml version="1.0" encoding="UTF-8"?><Return xmlns="http://www.irs.gov/efile" returnVersion="2026Q1v4.0"><ReturnHeader binaryAttachmentCnt="0"><ReturnTypeCd>941</ReturnTypeCd><ReturnTs>2025-12-03T19:00:00-05:00</ReturnTs><SoftwareId>12345678</SoftwareId><QuarterEndingDt>2026-03</QuarterEndingDt><Filer><EIN>003000004</EIN><BusinessName><BusinessNameLine1Txt>Orchid Incorporated</BusinessNameLine1Txt></BusinessName><USAddress><AddressLine1Txt>1st Test Street</AddressLine1Txt><CityNm>Willow Grove</CityNm><StateAbbreviationCd>PA</StateAbbreviationCd><ZIPCd>19090</ZIPCd></USAddress></Filer><OriginatorGrp><EFIN>238689</EFIN><OriginatorTypeCd>OnlineFiler</OriginatorTypeCd></OriginatorGrp></ReturnHeader><ReturnData documentCnt="1"><IRS941 documentId="IRS941"><EmployeeCnt>3</EmployeeCnt><WagesAmt>1000.00</WagesAmt><FederalIncomeTaxWithheldAmt>100.00</FederalIncomeTaxWithheldAmt><SocialSecurityWageAndTaxGrp><SocialSecurityTaxCashWagesAmt>1000.00</SocialSecurityTaxCashWagesAmt><SocialSecurityTaxAmt>124.00</SocialSecurityTaxAmt></SocialSecurityWageAndTaxGrp><MedicareWageTipsAndTaxGrp><TaxableMedicareWagesTipsAmt>1000.00</TaxableMedicareWagesTipsAmt><TaxOnMedicareWagesTipsAmt>29.00</TaxOnMedicareWagesTipsAmt></MedicareWageTipsAndTaxGrp><TotalSSMdcrTaxAmt>153.00</TotalSSMdcrTaxAmt><TotalTaxAmt>253.00</TotalTaxAmt><TotalTaxDepositAmt>253.00</TotalTaxDepositAmt><BalanceDueAmt>0.00</BalanceDueAmt></IRS941></ReturnData></Return>',
    manifestXml:
      '<?xml version="1.0" encoding="UTF-8"?><IRSSubmissionManifest xmlns="http://www.irs.gov/efile"><SubmissionId>2386892026105orchid1</SubmissionId><EFIN>238689</EFIN><GovernmentCd>IRS</GovernmentCd><FederalSubmissionTypeCd>941</FederalSubmissionTypeCd><TaxPeriodBeginDt>2026-01-01</TaxPeriodBeginDt><TaxPeriodEndDt>2026-03-31</TaxPeriodEndDt><TIN>003000004</TIN></IRSSubmissionManifest>',
    mime: {
      contentType: 'multipart/related; type="text/xml"; boundary="----=_Part_0_sample"',
      soapPart:
        '<?xml version="1.0" encoding="UTF-8"?><soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:mef="http://www.irs.gov/a2a/mef/MeFTransmitterService.xsd" xmlns:mefhdr="http://www.irs.gov/a2a/mef/MeFHeader.xsd"><soapenv:Header><wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"><!-- credential omitted --><wsu:Timestamp wsu:Id="Timestamp-sample"><wsu:Created>2026-04-15T22:04:00Z</wsu:Created><wsu:Expires>2026-04-15T22:09:00Z</wsu:Expires></wsu:Timestamp></wsse:Security><mefhdr:MeFHeader><mefhdr:MessageID>238689000001aaa00001E</mefhdr:MessageID><mefhdr:Action>SendSubmissions</mefhdr:Action><mefhdr:SessionIndicator>Y</mefhdr:SessionIndicator><mefhdr:TestIndicator>T</mefhdr:TestIndicator><mefhdr:AppSysID>23868900</mefhdr:AppSysID><mefhdr:WSDLVersionNum>10.9</mefhdr:WSDLVersionNum></mefhdr:MeFHeader></soapenv:Header><soapenv:Body><mef:SendSubmissionsRequest><mef:SubmissionDataList><mef:Cnt>1</mef:Cnt></mef:SubmissionDataList></mef:SendSubmissionsRequest></soapenv:Body></soapenv:Envelope>',
      attachments: [
        {
          contentId: "SubmissionsAttBin",
          contentType: "application/octet-stream",
          byteLength: 18432,
          sha256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        },
      ],
    },
    soapResponse:
      '<?xml version="1.0" encoding="UTF-8"?><soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:mef="http://www.irs.gov/a2a/mef/MeFTransmitterService.xsd"><soapenv:Header><!-- credential omitted --></soapenv:Header><soapenv:Body><mef:SendSubmissionsResponse><mef:DepositId>DEP-ATS-ORCHID-0001</mef:DepositId></mef:SendSubmissionsResponse></soapenv:Body></soapenv:Envelope>',
  },
  {
    submissionId: "2386892026105willow1",
    capturedAt: "2026-04-15T17:51:03-04:00",
    environment: "ATS",
    einMasked: "*****8891",
    formCode: "941",
    periodLabel: "2026-Q1",
    soapRequestCaptured: false,
    returnXml:
      '<?xml version="1.0" encoding="UTF-8"?><Return xmlns="http://www.irs.gov/efile" returnVersion="2026Q1v4.0"><ReturnHeader binaryAttachmentCnt="0"><ReturnTypeCd>941</ReturnTypeCd><QuarterEndingDt>2026-03</QuarterEndingDt><Filer><EIN>001112891</EIN><BusinessName><BusinessNameLine1Txt>Willow Grove Sample LLC</BusinessNameLine1Txt></BusinessName></Filer></ReturnHeader><ReturnData documentCnt="1"><IRS941 documentId="IRS941"><EmployeeCnt>1</EmployeeCnt><WagesAmt>500.00</WagesAmt><TotalTaxAmt>76.50</TotalTaxAmt></IRS941></ReturnData></Return>',
    manifestXml:
      '<?xml version="1.0" encoding="UTF-8"?><IRSSubmissionManifest xmlns="http://www.irs.gov/efile"><SubmissionId>2386892026105willow1</SubmissionId><EFIN>238689</EFIN><GovernmentCd>IRS</GovernmentCd><FederalSubmissionTypeCd>941</FederalSubmissionTypeCd><TaxPeriodBeginDt>2026-01-01</TaxPeriodBeginDt><TaxPeriodEndDt>2026-03-31</TaxPeriodEndDt><TIN>001112891</TIN></IRSSubmissionManifest>',
    mime: {
      contentType: "",
      soapPart: null,
      missingReason: "SOAP handler was not attached for this sample. Return XML was packed in-process.",
      attachments: [],
    },
    soapResponse: null,
  },
];

export function listSends() {
  return SAMPLE_SENDS.map((send) => ({
    submissionId: send.submissionId,
    capturedAt: send.capturedAt,
    einMasked: send.einMasked,
    formCode: send.formCode,
    periodLabel: send.periodLabel,
    soapRequestCaptured: send.soapRequestCaptured,
  }));
}

export function getSend(submissionId) {
  return SAMPLE_SENDS.find((send) => send.submissionId === submissionId) || null;
}
