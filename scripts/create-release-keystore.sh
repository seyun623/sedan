#!/usr/bin/env bash
# Creates the app signing key and prints the values to store as GitHub Secrets.
# Run this on YOUR computer (JDK required). Never commit the .jks file or its passwords.
# BACK UP the .jks file and password (e.g. password manager). If the key is lost, installed
# phones cannot be updated anymore: the app would have to be uninstalled (losing its data,
# so export CSV first) and reinstalled.
set -euo pipefail

OUT="${1:-moneyflow-release.jks}"
ALIAS="${2:-upload}"

if [ -e "$OUT" ]; then echo "$OUT already exists; refusing to overwrite." >&2; exit 1; fi

read -r -s -p "Keystore password (min 6 chars): " STOREPASS; echo
read -r -s -p "Repeat password: " STOREPASS2; echo
[ "$STOREPASS" = "$STOREPASS2" ] || { echo "Passwords do not match" >&2; exit 1; }
read -r -p "Your name or company (certificate CN): " CN

keytool -genkeypair -v \
  -keystore "$OUT" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$STOREPASS" -keypass "$STOREPASS" \
  -dname "CN=${CN}, C=KR"

echo
echo "Created $OUT. Add these repository secrets on GitHub:"
echo "  ANDROID_KEYSTORE_BASE64   = (output of: base64 -w0 $OUT   | on macOS: base64 -i $OUT)"
echo "  ANDROID_KEYSTORE_PASSWORD = (the password you just entered)"
echo "  ANDROID_KEY_ALIAS         = $ALIAS"
echo "  ANDROID_KEY_PASSWORD      = (the same password; PKCS12 uses one password)"
