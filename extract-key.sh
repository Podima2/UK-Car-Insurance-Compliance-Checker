#!/bin/bash
# Extract private key from p12 file

P12_FILE="${1:-./source/p12-files/token-key.p12}"
PASSWORD="${2:-password}"
ALIAS="${3:-token-key}"

echo "Extracting private key from $P12_FILE..."

# Extract private key in PEM format
openssl pkcs12 -in "$P12_FILE" -nocerts -nodes -passin "pass:$PASSWORD" -out /tmp/private-key.pem 2>/dev/null

if [ -f /tmp/private-key.pem ]; then
    echo "Private key extracted to /tmp/private-key.pem"
    echo ""
    echo "Private key (hex):"
    # Extract just the key part and convert to hex
    openssl ec -in /tmp/private-key.pem -text -noout 2>/dev/null | grep priv -A 3 | tail -n +2 | tr -d ' \n:' 
    echo ""
else
    echo "Failed to extract private key"
    exit 1
fi
