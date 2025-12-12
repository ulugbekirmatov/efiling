#!/bin/bash

# Script to verify PKCS12 keystore

cd "/Users/ulugbekirmatov/Documents/MeF test/"

echo "========================================"
echo "PKCS12 Keystore Verification"
echo "========================================"
echo ""

# Check file exists
if [ ! -f "irs-mef-test.p12" ]; then
    echo "✗ ERROR: irs-mef-test.p12 not found!"
    exit 1
fi

echo "✓ File exists: irs-mef-test.p12"
echo "✓ File size: $(ls -lh irs-mef-test.p12 | awk '{print $5}')"
echo ""

echo "========================================"
echo "Method 1: Java keytool verification"
echo "========================================"
echo "You will be prompted for the PKCS12 password:"
echo ""
keytool -list -v -keystore irs-mef-test.p12 -storetype PKCS12

echo ""
echo "========================================"
echo "Method 2: OpenSSL verification"
echo "========================================"
echo "You will be prompted for the PKCS12 password:"
echo ""
openssl pkcs12 -info -in irs-mef-test.p12 -nokeys

echo ""
echo "========================================"
echo "Certificate Chain Structure"
echo "========================================"
echo "You will be prompted for the PKCS12 password:"
echo ""
openssl pkcs12 -in irs-mef-test.p12 -nodes -nokeys | openssl crl2pkcs7 -nocrl -certfile /dev/stdin | openssl pkcs7 -print_certs -noout

echo ""
echo "========================================"
echo "Summary"
echo "========================================"
echo "Expected contents:"
echo "  ✓ 1 Private Key Entry"
echo "  ✓ 3 Certificates:"
echo "    - c2s.pahealthmanagement.org (Onewell LLC)"
echo "    - IGC Device CA 2 (Intermediate)"
echo "    - IdenTrust Global Common Root CA 1 (Root)"
echo ""
