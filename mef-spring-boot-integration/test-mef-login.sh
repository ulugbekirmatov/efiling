#!/bin/bash

# Test script for IRS MeF Login with new PKCS12 certificate
# Prerequisites: Java 17+, Maven 3.6+, Spring Boot application built

echo "=========================================="
echo "IRS MeF Login Test Script"
echo "=========================================="
echo ""

# Check Java version
echo "Checking Java version..."
java -version 2>&1 | head -n 1
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d '.' -f 1)

if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ ERROR: Java 17 or higher is required"
    echo "Current Java version is too old"
    echo "Please install JDK 17: brew install openjdk@17"
    exit 1
fi

echo "✓ Java version OK"
echo ""

# Navigate to project directory
cd "/Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration" || exit 1

# Check if .env file has been configured
if grep -q "YOUR_PKCS12_PASSWORD_HERE" .env; then
    echo "❌ ERROR: Please update the PKCS12 password in .env file"
    echo "Edit: /Users/ulugbekirmatov/Documents/MeF test/mef-spring-boot-integration/.env"
    exit 1
fi

echo "✓ .env file configured"
echo ""

# Build the application
echo "Building Spring Boot application..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✓ Build successful"
echo ""

# Start the application in background
echo "Starting Spring Boot application..."
mvn spring-boot:run &
APP_PID=$!

echo "Waiting for application to start..."
sleep 15

# Test 1: Certificate loading
echo ""
echo "=========================================="
echo "Test 1: Certificate Loading"
echo "=========================================="
curl -s http://localhost:8080/api/mef/auth/test-certificate | jq '.'

# Test 2: IRS Login
echo ""
echo "=========================================="
echo "Test 2: IRS Login (ETIN: 97661, ASID: 23868900)"
echo "=========================================="
curl -s -X POST http://localhost:8080/api/mef/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "etin": "97661",
    "productionMode": false
  }' | jq '.'

# Test 3: Session Status
echo ""
echo "=========================================="
echo "Test 3: Session Status"
echo "=========================================="
curl -s http://localhost:8080/api/mef/auth/status | jq '.'

echo ""
echo "=========================================="
echo "Tests Complete"
echo "=========================================="
echo ""
echo "Press Enter to stop the application..."
read

# Stop the application
kill $APP_PID
echo "Application stopped"
