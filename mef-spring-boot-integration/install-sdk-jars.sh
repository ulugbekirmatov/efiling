#!/bin/bash
# Install the five jars the pom needs that are not on Maven Central (MeF Client SDK 17.0 and
# Metro webservices 4.0.4) into ~/.m2, from the SDK zip tracked in this repo.
# Run once per machine; CI runs it on every build.
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ZIP="$MODULE_DIR/../Version_17/A2A_Toolkit_Version17.0/MeF_Client_SDK/Java/dist/mef_client_sdk.zip"
SDK_VERSION=17.0
METRO_VERSION=4.0.4

[ -f "$SDK_ZIP" ] || { echo "SDK zip not found: $SDK_ZIP" >&2; exit 1; }

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
unzip -q -o "$SDK_ZIP" -d "$WORK_DIR"
LIB="$WORK_DIR/mef_client_sdk/lib"

mvn -q -B install:install-file -Dfile="$LIB/mef_client_sdk.jar" \
  -DgroupId=gov.irs.mef -DartifactId=mef-client-sdk -Dversion="$SDK_VERSION" -Dpackaging=jar
for part in api rt extra tools; do
  mvn -q -B install:install-file -Dfile="$LIB/webservices-$part-$METRO_VERSION.jar" \
    -DgroupId=com.sun.xml.ws -DartifactId="webservices-$part" -Dversion="$METRO_VERSION" -Dpackaging=jar
done
echo "Installed mef-client-sdk $SDK_VERSION and webservices-{api,rt,extra,tools} $METRO_VERSION into ~/.m2"
