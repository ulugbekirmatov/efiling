# IRS MeF PKCS12 Configuration Complete

## Summary

Your MEF Spring Boot application has been configured to use the new IdenTrust PKCS12 certificate for IRS authentication.

## Configuration Changes Made

### 1. Updated `.env` File
**Location**: `/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration/.env`

```properties
# Certificate Configuration
MEF_KEYSTORE_PATH=/Users/ulugbekirmatov/Documents/MeF test/irs-mef-test.p12
MEF_KEYSTORE_TYPE=PKCS12
MEF_KEY_ALIAS=irs mef test certificate

# IRS Credentials
MEF_ETIN=97661
MEF_ASID=23868900
```

**ACTION REQUIRED**: Replace `YOUR_PKCS12_PASSWORD_HERE` with your actual PKCS12 password in lines 6 and 9.

### 2. Updated `MefClientService.java`
**Location**: `/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration/src/main/java/com/irs/mef/service/MefClientService.java`

Changes:
- Line 90-99: Added ASID retrieval from configuration and passing to ServiceContext
- Line 467-474: Added ASID validation before authentication test

### 3. Certificate Details

**PKCS12 File**: `/Users/ulugbekirmatov/Documents/MeF test/irs-mef-test.p12`
- **Certificate Alias**: "irs mef test certificate"
- **Subject**: c2s.pahealthmanagement.org (Onewell LLC)
- **Issuer**: IGC Device CA 2 (IdenTrust)
- **Valid**: November 25, 2025 - November 25, 2026
- **Chain**: Root CA → Intermediate CA → End Entity (3 certificates)

### 4. IRS Configuration

- **Environment**: ATS (Acceptance Testing System)
- **ETIN**: 97661 (valid IRS test ETIN)
- **ASID**: 23868900 (your A2A Application System ID)
- **Authentication**: Certificate-only (no username/password)
- **Endpoint**: https://la.alt.www4.irs.gov

---

## Prerequisites for Testing

### Java 17 Required

❌ **Current Issue**: Your system needs Java 17 or higher to build and run the Spring Boot 3.2.0 application.

**Current Java Versions**:
- System: Java 8 (too old)
- Maven: Java 25 (incompatible)

**Install Java 17**:
```bash
# Install OpenJDK 17 via Homebrew
brew install openjdk@17

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc

# Verify installation
java -version  # Should show version 17.x.x
```

---

## Testing Steps

### Step 1: Update PKCS12 Password

Edit `/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration/.env`:

```bash
# Replace YOUR_PKCS12_PASSWORD_HERE with your actual password
nano .env
```

### Step 2: Build the Application

```bash
cd "/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration"
mvn clean package -DskipTests
```

### Step 3: Run Automated Tests

```bash
# Use the provided test script
./test-mef-login.sh
```

Or test manually:

### Step 4: Start the Application

```bash
mvn spring-boot:run
```

Wait for the application to start (look for "Started MefSpringBootApplication" in logs).

### Step 5: Test Certificate Loading

Open a new terminal and run:

```bash
curl http://localhost:8080/api/mef/auth/test-certificate
```

**Expected Response**:
```json
{
  "success": true,
  "keystoreLoadResult": {
    "success": true,
    "keystoreType": "PKCS12",
    "message": "Keystore loaded successfully"
  },
  "certificateDetailsResult": {
    "success": true,
    "certificateCount": 1,
    "aliases": ["irs mef test certificate"],
    "certificates": [...]
  }
}
```

### Step 6: Test IRS Login

```bash
curl -X POST http://localhost:8080/api/mef/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "etin": "97661",
    "productionMode": false
  }'
```

**Expected Response** (if successful):
```json
{
  "success": true,
  "samlAssertion": "...(SAML token)...",
  "sessionId": "SESSION_...",
  "message": "Login successful",
  "timestamp": "2025-11-28T..."
}
```

**Possible Errors**:
- **Certificate not registered**: Your IdenTrust certificate may need to be registered with IRS for ETIN 97661
- **ASID mismatch**: Verify ASID 23868900 is correct for your IRS account
- **Network/firewall**: Ensure connectivity to la.alt.www4.irs.gov

### Step 7: Check Session Status

```bash
curl http://localhost:8080/api/mef/auth/status
```

---

## Troubleshooting

### Build Fails with Java Version Error

**Solution**: Install Java 17 (see Prerequisites section above)

### "Keystore not found" Error

**Solution**: Verify the PKCS12 file path in `.env`:
```bash
ls -la "/Users/ulugbekirmatov/Documents/MeF test/irs-mef-test.p12"
```

### "Invalid keystore password" Error

**Solution**: Verify you entered the correct PKCS12 password in `.env`

### "ASID not configured" Error

**Solution**: Ensure `.env` has `MEF_ASID=23868900`

### Certificate Not Trusted by IRS

**Possible causes**:
1. Certificate not yet registered with IRS for ETIN 97661
2. Certificate Common Name doesn't match expected value
3. Using wrong environment (ATS vs PRD)

**Solution**: Contact IRS e-Services Help Desk (1-866-255-0654) to verify:
- Certificate is registered for ETIN 97661
- ASID 23868900 is active and linked to your ETIN
- Certificate Common Name matches IRS records

### Connection Timeout

**Solution**: Check network connectivity:
```bash
ping la.alt.www4.irs.gov
curl -v https://la.alt.www4.irs.gov/a2a/mef/Login
```

---

## Next Steps After Successful Login

1. **Test other MeF operations**:
   - Submit test tax return
   - Check submission status
   - Retrieve acknowledgements

2. **Implement error handling** for production use

3. **Configure logging** for audit trail

4. **Set up monitoring** for session expiration

5. **Document** your MEF integration workflow

---

## Important Notes

### Security
- ✓ PKCS12 file contains your private key - keep it secure
- ✓ Never commit `.env` file to version control
- ✓ Use strong password for PKCS12 keystore
- ✓ Restrict file permissions: `chmod 600 irs-mef-test.p12`

### Certificate Validity
- **Expires**: November 25, 2026
- **Renewal**: Plan to renew certificate before expiration
- **Monitoring**: Set up alerts for certificate expiration

### IRS ATS vs Production
- **Current setup**: ATS (test environment)
- **For production**:
  - Change `mef.sdk.environment` to `PRD` in `application.yml`
  - Verify certificate is registered for production
  - Update `.env` with production ETIN/ASID if different

---

## Support Contacts

### IRS e-Services Help Desk
- **Phone**: 1-866-255-0654
- **Hours**: Monday-Friday, 7 AM - 7 PM ET
- **Topics**: MeF enrollment, ASID, ETIN, certificate registration

### IdenTrust Support
- **Website**: https://www.identrust.com/support
- **Topics**: Certificate issues, renewal, revocation

---

## Files Modified/Created

### Modified
1. `/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration/.env`
2. `/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration/src/main/java/com/irs/mef/service/MefClientService.java`

### Created
1. `/Users/ulugbekirmatov/Documents/MeF test/irs-mef-test.p12` (PKCS12 keystore)
2. `/Users/ulugbekirmatov/Documents/MeF test/irs-cert-chain.pem` (certificate chain)
3. `/Users/ulugbekirmatov/Documents/MeF test/create-pkcs12.sh` (keystore creation script)
4. `/Users/ulugbekirmatov/Documents/MeF test/SETUP-INSTRUCTIONS.md` (PKCS12 setup guide)
5. `/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration/test-mef-login.sh` (test script)
6. `/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration/SETUP-COMPLETE.md` (this file)

---

**Configuration Date**: November 28, 2025
**Certificate Expiration**: November 25, 2026
**Environment**: IRS ATS (Acceptance Testing System)
