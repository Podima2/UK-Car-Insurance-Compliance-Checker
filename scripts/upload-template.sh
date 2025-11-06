#!/bin/bash
set -euo pipefail

TEMPLATE_FILE="${1:-}"
METAGRAPH_NODE="metagraph-node-1"

if [[ -z "${TEMPLATE_FILE}" || ! -f "${TEMPLATE_FILE}" ]]; then
  echo "Usage: $0 <template.json>" >&2
  exit 1
fi

echo "📤 Uploading template from ${TEMPLATE_FILE}"

# Read template
TEMPLATE=$(cat "${TEMPLATE_FILE}")

docker exec -i "${METAGRAPH_NODE}" bash -c '
  set -e
  cd /code || exit 1
  echo "🔎 Locating wallet and keystore..."
  WALLET=$(ls -1 */cl-wallet.jar 2>/dev/null | head -1)
  [[ -z "$WALLET" ]] && { echo "cl-wallet.jar not found" >&2; exit 1; }

  # Prefer metagraph-l0 keystore
  KEYSTORE=/code/metagraph-l0/token-key.p12
  [[ ! -f "$KEYSTORE" ]] && KEYSTORE=/code/token-key.p12
  [[ ! -f "$KEYSTORE" ]] && { echo "token-key.p12 not found" >&2; exit 1; }

  echo "🔑 Exporting key..."
  PRIVKEY=$(java -jar "$WALLET" export --keystore "$KEYSTORE" --alias token-key --storepass password)
  PUBKEY=$(java -jar "$WALLET" show-public-key --keystore "$KEYSTORE" --alias token-key --storepass password)

  echo "🧾 Building payload..."
  echo "$TEMPLATE" > /tmp/template.json
  ENCODED=$(base64 -w0 /tmp/template.json 2>/dev/null || base64 /tmp/template.json)
  SIGNATURE=$(java -jar "$WALLET" sign-data --private-key "$PRIVKEY" "$ENCODED")

  PAYLOAD=$(jq -n --arg val "$ENCODED" --arg pk "$PUBKEY" --arg sig "$SIGNATURE" '{
    value: $val,
    proofs: [{id: $pk, signature: $sig}]
  }')

  echo "🚀 Submitting to Data L1..."
  curl -s -i -X POST http://localhost:9400/data \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD"
'