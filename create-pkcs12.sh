#!/bin/bash
# Build the Pyramos PKCS12 keystore the IRS MeF Java SDK logs in with.
#
# Inputs (all in "Pyramos Software Certificates/"):
#   private-irs.key       unencrypted PKCS#8 private key (matches the CSR)
#   your-issued-cert.pem  IdenTrust IGC device certificate, O=Pyramos Software LLC
#   SubCA1.txt            IGC Device CA 2 (intermediate)
#   Root.txt              IdenTrust Global Common Root CA 1
# Output:
#   Pyramos Software Certificates/pyramos-a2a.p12   (gitignored via *.p12)
#
# openssl prompts for the keystore password twice. Java's PKCS12 reader uses the
# same password for the store and the key, so MEF_KEYSTORE_PASSWORD and
# MEF_KEY_PASSWORD in .env must both be set to what you type here.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="$REPO_ROOT/Pyramos Software Certificates"
KEY="$CERT_DIR/private-irs.key"
LEAF="$CERT_DIR/your-issued-cert.pem"
SUB_CA="$CERT_DIR/SubCA1.txt"
ROOT_CA="$CERT_DIR/Root.txt"
OUT="$CERT_DIR/pyramos-a2a.p12"
ALIAS="pyramos-a2a"
EXPECTED_ORG="Pyramos Software LLC"

for f in "$KEY" "$LEAF" "$SUB_CA" "$ROOT_CA"; do
  [ -f "$f" ] || { echo "missing input: $f" >&2; exit 1; }
done

# Refuse to package the OneWell cert by mistake: both certs share the CN, only O= differs.
if ! openssl x509 -in "$LEAF" -noout -subject | grep -q "O=$EXPECTED_ORG"; then
  echo "leaf certificate is not O=$EXPECTED_ORG:" >&2
  openssl x509 -in "$LEAF" -noout -subject >&2
  exit 1
fi

# The key must belong to the cert, or the IRS signature check fails with an opaque fault.
if [ "$(openssl x509 -in "$LEAF" -noout -modulus)" != "$(openssl rsa -in "$KEY" -noout -modulus)" ]; then
  echo "private-irs.key does not match your-issued-cert.pem" >&2
  exit 1
fi

if [ -f "$OUT" ]; then
  echo "$OUT already exists. Delete it first if you want to rebuild." >&2
  exit 1
fi

CHAIN="$(mktemp)"
trap 'rm -f "$CHAIN"' EXIT
# The IdenTrust .txt files end without a newline; plain cat would glue the two PEM blocks together.
{ cat "$SUB_CA"; echo; cat "$ROOT_CA"; echo; } > "$CHAIN"
openssl crl2pkcs7 -nocrl -certfile "$CHAIN" | openssl pkcs7 -print_certs -noout >/dev/null \
  || { echo "chain file failed to parse" >&2; exit 1; }

# Interactive by default. Set PKCS12_PASSWORD only when no terminal is attached (CI); it
# lands in shell history, so prefer the prompt on a workstation.
if [ -n "${PKCS12_PASSWORD:-}" ]; then
  PASS_ARGS=(-passout env:PKCS12_PASSWORD)
  echo "Building $OUT (alias: $ALIAS) with the password from PKCS12_PASSWORD."
elif [ -t 0 ]; then
  PASS_ARGS=()
  echo "Building $OUT (alias: $ALIAS). You will be asked for the keystore password twice."
else
  echo "No terminal attached and PKCS12_PASSWORD is unset. Run this from an interactive shell." >&2
  exit 1
fi
openssl pkcs12 -export \
  -in "$LEAF" \
  -inkey "$KEY" \
  -certfile "$CHAIN" \
  -name "$ALIAS" \
  -out "$OUT" \
  ${PASS_ARGS[@]+"${PASS_ARGS[@]}"}   # bash 3.2 + set -u: an empty array is "unbound" without this guard
chmod 600 "$OUT"

echo
echo "Created: $OUT"
echo "Subject: $(openssl x509 -in "$LEAF" -noout -subject | sed 's/^subject=//')"
echo "Expires: $(openssl x509 -in "$LEAF" -noout -enddate | sed 's/^notAfter=//')"
echo
echo "Now set in mef-spring-boot-integration/.env:"
echo "  MEF_KEYSTORE_PATH=$OUT"
echo "  MEF_KEY_ALIAS=$ALIAS"
echo "  MEF_KEYSTORE_PASSWORD=<the password you just typed>"
echo "  MEF_KEY_PASSWORD=<the same password>"
echo
echo "Verify with: ./verify-pkcs12.sh"
