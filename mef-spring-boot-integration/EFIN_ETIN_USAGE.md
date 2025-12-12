# EFIN vs ETIN Usage Guide

**Date**: 2025-12-09
**Status**: Configured with Real Credentials

---

## 📋 Understanding EFIN vs ETIN

### **ETIN (Electronic Transmitter Identification Number)**
- **Value**: `97661`
- **Purpose**: Identifies the **transmitter** (the entity sending returns to the IRS)
- **Used For**:
  - Authentication/Login
  - Manifest EFIN field (transmitter)
  - Session management

### **EFIN (Electronic Filing Identification Number)**
- **Value**: `238689`
- **Purpose**: Identifies the **originator/preparer** (the entity that prepared the return)
- **Used For**:
  - Submission ID (first 6 digits)
  - XML OriginatorGrp element
  - Identifying who prepared the return

---

## ✅ Current Configuration (Updated)

### **1. Submission ID**
```
Format: [EFIN][Date][Suffix]
Example: 238689 + 2025331 + test941 = 2386892025331test941

EFIN Used: 238689 (Originator EFIN)
```

**File**: `Form941SubmissionTest.java:65`
```java
String efin = "238689";  // Real EFIN - Electronic Filing Identification Number
```

---

### **2. XML File - OriginatorGrp**
```xml
<OriginatorGrp>
  <EFIN>238689</EFIN>
  <OriginatorTypeCd>OnlineFiler</OriginatorTypeCd>
</OriginatorGrp>
```

**File**: `Return941-Scenario1.xml:47`

EFIN Used: `238689` (Originator EFIN)

---

### **3. Submission Manifest**
```xml
<IRSSubmissionManifest xmlns="http://www.irs.gov/efile">
  <SubmissionId>2386892025331test941</SubmissionId>
  <EFIN>97661</EFIN>
  <GovernmentCd>IRS</GovernmentCd>
  ...
</IRSSubmissionManifest>
```

**File**: `Form941SubmissionTest.java:164`
```java
.efin("97661")  // Transmitter ETIN (who is submitting)
```

EFIN Used: `97661` (Transmitter ETIN - the account submitting)

---

### **4. Authentication (Login)**
```bash
export MEF_ETIN=97661
export MEF_ASID=23868900
```

ETIN Used: `97661` (Transmitter account)

---

## 🎯 Summary Table

| Component | EFIN/ETIN Value | Purpose | Type |
|-----------|-----------------|---------|------|
| **Login Authentication** | 97661 | Transmitter account | ETIN |
| **Manifest EFIN** | 97661 | Who is transmitting | ETIN |
| **Submission ID** | 238689 | Originator identifier | EFIN |
| **XML OriginatorGrp** | 238689 | Who prepared return | EFIN |

---

## 🔑 Key Points

1. **ETIN 97661** = Your transmitter account with the IRS
   - Used for authentication
   - Used in manifest to identify the transmitter

2. **EFIN 238689** = Your originator/preparer identifier
   - Used in submission ID
   - Used in the return XML to identify who prepared it

3. **This is the correct configuration**
   - Transmitter (97661) sends returns on behalf of originator (238689)
   - Common in professional tax preparation scenarios

---

## 📝 Why Two Different Numbers?

In the IRS MeF system:
- A **Transmitter** (ETIN 97661) can submit returns for multiple **Originators** (EFIN 238689)
- This allows tax preparation firms to submit on behalf of clients
- Each has a different identification number for tracking and authorization

---

**Last Updated**: 2025-12-09
**Configuration Status**: ✅ Consistent and Correct
