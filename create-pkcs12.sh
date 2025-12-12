#!/bin/bash

# Script to create PKCS12 keystore for IRS MEF Java SDK
# This script will create irs-mef-test.p12 file

cd "/Users/ulugbekirmatov/Documents/MeF test/"

echo "========================================"
echo "Creating PKCS12 Keystore for IRS MEF"
echo "========================================"
echo ""
echo "You will be prompted for:"
echo "1. Password for your private key (private-irs.key)"
echo "2. New password for the PKCS12 file (enter twice)"
echo ""
echo "Press Enter to continue..."
read

# Create PKCS12 file
openssl pkcs12 -export \
  -in irs-cert-chain.pem \
  -inkey private-irs.key \
  -out irs-mef-test.p12 \
  -name "IRS MEF Test Certificate" \
  -certfile irs-cert-chain.pem

# Check if successful
if [ $? -eq 0 ]; then
    echo ""
    echo "✓ SUCCESS! PKCS12 file created: irs-mef-test.p12"
    echo ""
    echo "Verifying the keystore..."
    echo ""
    openssl pkcs12 -info -in irs-mef-test.p12 -noout -nodes

    if [ $? -eq 0 ]; then
        echo ""
        echo "✓ Keystore verification successful!"
        echo ""
        echo "File location: /Users/ulugbekirmatov/Documents/MeF test/irs-mef-test.p12"
        echo ""
        echo "Next steps:"
        echo "1. Remember the PKCS12 password you just set"
        echo "2. Configure your MEF Java SDK to use this keystore"
        echo "3. Use the password when configuring the SDK"
    fi
else
    echo ""
    echo "✗ ERROR: Failed to create PKCS12 file"
    echo "Please check:"
    echo "- You entered the correct private key password"
    echo "- All certificate files exist in the directory"
fi
