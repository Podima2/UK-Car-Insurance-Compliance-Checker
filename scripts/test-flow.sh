#!/usr/bin/env bash
set -euo pipefail

API_BASE="http://localhost:9000"

# Step 1: Uploading template (SafeDrive Insurance)
echo "Uploading template..."
curl -sS -X POST "$API_BASE/data" \
  -H "Content-Type: application/json" \
  -d '{
    "UploadContractTemplate": {
      "providerName": "SafeDrive Insurance",
      "templateVersion": "1.0",
      "terms": [
        {
          "category": "Vehicle Changes",
          "description": "Material changes to the insured vehicle",
          "voidingConditions": ["Performance modifications"],
          "notificationRequired": true
        }
      ],
      "riskRules": [
        {
          "keywords": ["exhaust", "turbo", "supercharger", "nitrous", "nos", "performance upgrade", "custom exhaust"],
          "category": "Vehicle Modifications",
          "riskWeight": 75,
          "requiresNotification": true,
          "explanation": "Performance modifications may void your policy and require disclosure"
        }
      ],
      "uploadedBy": "admin"
    }
  }'

echo "Waiting for snapshot..."
sleep 3

# Step 2: Getting providers
echo "Providers:"
curl -sS "$API_BASE/providers" | jq . || curl -sS "$API_BASE/providers"

# Step 3: Validating circumstance
REQ_ID="test-$(date +%s)"
BODY='{
  "ValidateCircumstance": {
    "providerName": "SafeDrive Insurance",
    "circumstanceChange": "Im custom upgrading my exhaust",
    "userRequestId": "'$REQ_ID'"
  }
}'

echo "Submitting validation..."
curl -sS -X POST "$API_BASE/data" -H "Content-Type: application/json" -d "$BODY"

echo "Waiting for snapshot..."
sleep 3

# Step 4: Getting validation result (best-effort using recent validations listing if supported)
# If your backend exposes an index endpoint, replace the following with a proper lookup.
# Here we attempt to fetch by a guessed ID pattern as per demo assumptions.
VAL_ID="SafeDrive Insurance-$(date +%s)"
echo "Fetching validation result for $VAL_ID (may require adjusting ID lookup in real env)..."
curl -sS "$API_BASE/validations/$VAL_ID" | jq . || curl -sS "$API_BASE/validations/$VAL_ID"
