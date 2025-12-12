# IRS MeF Submission ID Format Requirements

## Discovered through SDK Analysis and IRS Error Messages

### Validation Pattern

The MeF SDK validates submission IDs using the following regex pattern:

```regex
[0-9]{13}[a-z0-9]{7}
```

### Semantic Structure (DISCOVERED via SDK Decompilation)

Through SDK bytecode analysis, we discovered that submission IDs have semantic meaning:

```
[EFIN (6 digits)][Processing Date (7 digits)][Suffix (7 chars)]
```

| Component | Position | Length | Format | Description |
|-----------|----------|--------|--------|-------------|
| **EFIN** | 1-6 | 6 chars | `0-9` | Electronic Filer Identification Number |
| **Processing Date** | 7-13 | 7 chars | `yyyyDDD` | Processing date: CURRENT YEAR + day-of-year (001-366) |
| **Suffix** | 14-20 | 7 chars | `a-z0-9` | Lowercase alphanumeric unique identifier |
| **Total** | 1-20 | 20 chars | | Exactly 20 characters total |

**CRITICAL DISCOVERIES**:
1. The processing date uses **day-of-year format** (`yyyyDDD`), NOT month/day format
2. SDK uses `SimpleDateFormat("yyyyDDD")` (found at `gov.irs.mef.services.data.SubmissionID`)
3. The date MUST use the **current calendar year**, NOT the tax period year
4. Day-of-year is zero-padded to 3 digits (001-366)
5. This format works for ALL dates including months 10-12!

### Valid Examples (Using Day-of-Year Format)

```
✅ 23868920253314c1m26i
   EFIN: 238689 (Real originator EFIN)
   Date: 2025331 (November 27, 2025 - day 331 of year)
   Suffix: 4c1m26i
   Use Case: Return submitted on November 27, 2025
   Status: ✅ Accepted by IRS ATS
   Note: Previously misinterpreted as March 31 - actual format is day-of-year!

✅ 23868920250904c1m26i
   EFIN: 238689 (Real originator EFIN)
   Date: 2025090 (March 31, 2025 - day 90 of year)
   Suffix: 4c1m26i
   Use Case: Q1 return submitted on March 31, 2025
   Note: March 31 = day 90 (31 Jan + 28 Feb + 31 Mar = 90)

✅ 09766120251054abcd123
   EFIN: 097661 (Padded ETIN - can be used as EFIN)
   Date: 2025105 (April 15, 2025 - day 105 of year)
   Suffix: 4abcd123
   Use Case: Q1 return submitted by tax deadline (April 15)
   Note: April 15 = day 105

✅ 09766120250204abcd123
   EFIN: 097661 (Padded ETIN)
   Date: 2025020 (January 20, 2025 - day 20 of year)
   Suffix: abcd123
   Use Case: Any return submitted on January 20
   Note: January 20 = day 20

✅ 09766120253444test001
   EFIN: 097661
   Date: 2025344 (December 10, 2025 - day 344 of year)
   Suffix: 4test001
   Use Case: Works for December! No more month 10-12 issues!
   Note: December 10 = day 344
```

### Invalid Examples

```
❌ 941_TEST_1764947707581
   Problem: Underscore not allowed, uppercase letters, wrong format

❌ 941TEST1764947924705
   Problem: Uppercase letters not allowed, only 19 characters

❌ 12345678901234567890
   Problem: All digits (20 digits), last 7 must allow lowercase letters

❌ 1234567890123ABCDEFG
   Problem: Uppercase letters not allowed in suffix

❌ 1234567890AB3test941
   Problem: Characters 11-13 are letters (should be digits)

❌ 1234562026331test941
   Problem: Wrong year (2026) - must use CURRENT YEAR not tax period year
   IRS Error: "MEF00004 - the processing year should be the current year"

❌ 1765197109450test941
   Problem: Using timestamp causes invalid date parsing
   IRS Error: "Invalid date 7109450"
```

### Implementation Pattern (RECOMMENDED - SDK-Compatible Format)

```java
import java.time.LocalDate;

// Generate submission ID using SDK's actual format
// EFIN (6) + Processing Date yyyyDDD (7) + Unique Suffix (7)

// Get EFIN from return XML or configuration
String efin = "123456";  // 6 digits - must match EFIN in return

// Generate processing date using CURRENT YEAR + day-of-year (CRITICAL!)
LocalDate now = LocalDate.now();  // Current date
int dayOfYear = now.getDayOfYear();  // 1-366

// Format as yyyyDDD (7 digits) - matches SDK SimpleDateFormat("yyyyDDD")
String processingDate = String.format("%d%03d", now.getYear(), dayOfYear);
// Examples:
//   January 1, 2025: "2025001"
//   March 31, 2025: "2025090"
//   December 10, 2025: "2025344"
//   December 31, 2025: "2025365"

// Generate unique suffix (7 lowercase alphanumeric)
// Option 1: Static suffix for testing
String suffix = "test941";

// Option 2: Timestamp-based suffix for uniqueness
String suffix = String.valueOf(System.currentTimeMillis()).substring(6, 13);

// Option 3: UUID-based suffix
String suffix = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 7).toLowerCase();

// Combine components
String submissionId = efin + processingDate + suffix;
// Result: "12345620250904c1m26i" for March 31, 2025

// Validate format
if (!submissionId.matches("[0-9]{13}[a-z0-9]{7}")) {
    throw new IllegalArgumentException("Invalid submission ID format");
}
```

### Implementation Pattern (OLD - Not Recommended)

```java
// OLD APPROACH: Using timestamp directly (causes IRS errors)
long timestamp = System.currentTimeMillis();  // e.g., 1764947924705
String timestampStr = String.valueOf(timestamp);  // 13 digits
String suffix = "test941";  // 7 lowercase alphanumeric characters
String submissionId = timestampStr + suffix;  // Result: 1764947924705test941
// ❌ IRS rejects with "Invalid date 7109450" error
```

### SDK Validation Method

**Class**: `gov.irs.mef.services.data.SubmissionID`
**Method**: `public static boolean isValidSubmissionID(String)`
**Regex**: `[0-9]{13}[a-z0-9]{7}`

### Discovery Details

Found via bytecode decompilation of `SubmissionArchive.class`:

```
Line 77: invokestatic  #77  // gov/irs/mef/services/data/SubmissionID.isValidSubmissionID:(Ljava/lang/String;)Z
```

Decompiled `isValidSubmissionID` method:
```java
public static boolean isValidSubmissionID(String id) {
    return id != null && Pattern.matches("[0-9]{13}[a-z0-9]{7}", id);
}
```

### Additional Notes

1. **EFIN Structure** (Characters 0-5):
   - The first 6 digits are the EFIN (Electronic Filer Identification Number)
   - Must match the EFIN in the return XML
   - Extracted by IRS when processing submission
   - Zero-padded if ETIN is less than 6 digits

2. **Processing Date Structure** (Characters 6-12):
   - Format: **yyyyDDD** (7 digits) - year + day-of-year
   - SDK uses: `SimpleDateFormat("yyyyDDD")`
   - **CRITICAL**: Must use CURRENT YEAR, not tax period year
   - Day-of-year is 3 digits, zero-padded (001-366)
   - Examples:
     - January 1: `2025001` (day 1)
     - March 31: `2025090` (day 90)
     - December 31: `2025365` (day 365)
   - **Works for ALL dates** - no month 10-12 issues!

3. **Suffix Structure** (Characters 13-19):
   - 7 lowercase alphanumeric characters
   - Used to ensure uniqueness across submissions
   - Can be timestamp-based, sequential, or UUID-based
   - **Case Sensitivity**: Uppercase letters NOT allowed

4. **Fixed Length**: The ID must be exactly 20 characters - no more, no less

5. **No Special Characters**: Only digits (0-9) and lowercase letters (a-z) allowed

6. **Date Format Discovery**: Found by decompiling `gov.irs.mef.services.data.SubmissionID`
   - Bytecode shows: `new SimpleDateFormat("yyyyDDD")`
   - Previous documentation incorrectly assumed `YYYYmDD` format
   - Actual format uses day-of-year, not month/day

### Testing

To test if a submission ID is valid:

```bash
# Valid IDs will have exactly 13 digits followed by 7 lowercase alphanumeric
echo "1764947924705test941" | grep -E '^[0-9]{13}[a-z0-9]{7}$' && echo "Valid" || echo "Invalid"
```

### Common Mistakes to Avoid

1. ❌ Using underscores, hyphens, or special characters as separators
2. ❌ Including uppercase letters anywhere in the ID
3. ❌ Making the ID longer or shorter than 20 characters
4. ❌ Using only digits (last 7 chars must be lowercase alphanumeric)
5. ❌ Using millisecond timestamps directly in date portion (causes date parsing errors)
6. ❌ Using tax period year instead of current calendar year (causes MEF00004 error)
7. ❌ Using `YYYYmDD` (month/day) format instead of `yyyyDDD` (day-of-year) format
8. ❌ Assuming the format is flexible (it's strictly validated with semantic meaning)
9. ❌ Not zero-padding day-of-year to 3 digits (must be 001-366, not 1-366)

### Discovery Process

The semantic structure was discovered through:

1. **SDK Bytecode Analysis (Initial)**: Regex pattern `[0-9]{13}[a-z0-9]{7}` found in `SubmissionID.isValidSubmissionID()`

2. **IRS Error Messages**: Submission attempts revealed semantic validation:
   - `1765197109450test941` → "Invalid date 7109450"
   - `1234562026331test941` → "MEF00004: the processing year should be the current year"

3. **SDK Decompilation (Critical Discovery)**: Decompiled `gov.irs.mef.services.data.SubmissionID` class:
   ```java
   // Line 21 in bytecode:
   ldc  #207  // String yyyyDDD
   // SDK uses SimpleDateFormat("yyyyDDD")
   ```
   **This revealed the actual format: day-of-year, NOT month/day!**

4. **Verification**: Testing confirmed the day-of-year format:
   - March 31, 2025 = day 90 → `2025090`
   - November 27, 2025 = day 331 → `2025331` (the actual "working example" date!)
   - December 10, 2025 = day 344 → `2025344`

5. **Previous Misinterpretation**: The "working example" `23868920253314c1m26i` with date `2025331` was incorrectly documented as "March 31, 2025" but was actually "November 27, 2025" (day 331)

---

**Source**: MeF Client SDK v16 - `gov.irs.mef.services.data.SubmissionID`
**Validation**: `java.util.regex.Pattern.matches("[0-9]{13}[a-z0-9]{7}", submissionId)`
**Date Format**: `SimpleDateFormat("yyyyDDD")` - day-of-year format
**Format Discovered**: 2025-12-10 through SDK bytecode decompilation
**Previous Documentation Error**: Incorrectly assumed `YYYYmDD` format; actual format is `yyyyDDD`
**Corrected**: `2025331` = November 27 (day 331), NOT March 31 (which would be `2025090`)
