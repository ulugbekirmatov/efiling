#!/bin/bash
# Verify the Pyramos PKCS12 keystore built by create-pkcs12.sh.
# Prompts for the keystore password (once per tool). Read-only.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
P12="${1:-$REPO_ROOT/Pyramos Software Certificates/pyramos-a2a.p12}"
EXPECTED_ORG="Pyramos Software LLC"

[ -f "$P12" ] || { echo "not found: $P12" >&2; exit 1; }
echo "Keystore: $P12 ($(du -h "$P12" | cut -f1))"
echo

echo "== keytool (Java view; this is what the SDK sees) =="
KEYTOOL="$(command -v keytool || echo /opt/homebrew/opt/openjdk@17/bin/keytool)"
"$KEYTOOL" -list -v -keystore "$P12" -storetype PKCS12 \
  | grep -E "Alias name|Entry type|Owner|Issuer|Valid from|Certificate chain length|Certificate\[" || true
echo

echo "== openssl (chain order; asks for the password again) =="
openssl pkcs12 -in "$P12" -nokeys \
  | openssl crl2pkcs7 -nocrl -certfile /dev/stdin \
  | openssl pkcs7 -print_certs -noout
echo

echo "Expected:"
echo "  1 PrivateKeyEntry, alias pyramos-a2a"
echo "  chain length 3: CN=c2s.pahealthmanagement.org O=$EXPECTED_ORG -> IGC Device CA 2 -> IdenTrust Global Common Root CA 1"
echo "  Owner must say O=$EXPECTED_ORG, never Onewell"
