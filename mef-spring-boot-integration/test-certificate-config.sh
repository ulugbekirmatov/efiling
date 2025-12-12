#!/bin/bash

# Test Certificate Configuration Script
# This script verifies that the certificate is properly configured and readable

echo "========================================="
echo "Testing MeF Certificate Configuration"
echo "========================================="
echo ""

# Load environment variables from .env file
if [ -f ".env" ]; then
    echo "Loading environment variables from .env file..."
    export $(cat .env | grep -v '^#' | xargs)
    echo "✓ Environment variables loaded"
else
    echo "✗ .env file not found!"
    exit 1
fi

echo ""
echo "Configuration:"
echo "-----------------------------------------"
echo "Keystore Path: $MEF_KEYSTORE_PATH"
echo "Keystore Type: $MEF_KEYSTORE_TYPE"
echo "Key Alias: $MEF_KEY_ALIAS"
echo ""

# Check if keystore file exists
if [ -f "$MEF_KEYSTORE_PATH" ]; then
    echo "✓ Keystore file exists"
    echo "  File size: $(ls -lh $MEF_KEYSTORE_PATH | awk '{print $5}')"
    echo "  Permissions: $(ls -l $MEF_KEYSTORE_PATH | awk '{print $1}')"
else
    echo "✗ Keystore file not found at: $MEF_KEYSTORE_PATH"
    exit 1
fi

echo ""
echo "Keystore Contents:"
echo "-----------------------------------------"

# Use keytool to list keystore contents
if keytool -list -keystore "$MEF_KEYSTORE_PATH" -storepass "$MEF_KEYSTORE_PASSWORD" -storetype "$MEF_KEYSTORE_TYPE" > /dev/null 2>&1; then
    keytool -list -keystore "$MEF_KEYSTORE_PATH" -storepass "$MEF_KEYSTORE_PASSWORD" -storetype "$MEF_KEYSTORE_TYPE"
    echo ""
    echo "✓ Certificate successfully loaded from keystore"
else
    echo "✗ Failed to load certificate from keystore"
    exit 1
fi

echo ""
echo "========================================="
echo "Certificate Configuration Test: PASSED"
echo "========================================="
echo ""
echo "Your application is ready to use this certificate."
echo "When you receive the IdenTrust-signed certificate,"
echo "simply replace the test certificate with:"
echo ""
echo "  openssl pkcs12 -export \\"
echo "    -in <identrust_signed_cert>.crt \\"
echo "    -inkey irs_cert/PRIVATEKEY_IRS.key \\"
echo "    -out irs_cert/production_keystore.p12 \\"
echo "    -name \"irs_production_cert\" \\"
echo "    -passin pass:'eP3qcTQL@3' \\"
echo "    -passout pass:'YOUR_PRODUCTION_PASSWORD' \\"
echo "    -legacy"
echo ""
echo "Then update .env with the new keystore path and password."
echo ""
