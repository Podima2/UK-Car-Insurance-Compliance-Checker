#!/bin/bash
set -e

echo "=== Checking Metagraph State ==="
echo ""

echo "1. L0 Latest Snapshot:"
ORDINAL=$(curl -s http://localhost:9200/snapshots/latest | jq -r '.ordinal // "none"')
echo "   Ordinal: $ORDINAL"
echo ""

echo "2. Calculated State Proof:"
curl -s http://localhost:9200/snapshots/latest | jq -r '.dataApplication.calculatedStateProof // "none"'
echo ""

echo "3. Available Routes Test:"
echo "   /providers:"
curl -s http://localhost:9200/providers 2>&1 | head -1
echo "   /insurance/providers:"
curl -s http://localhost:9200/insurance/providers 2>&1 | head -1
echo ""

echo "4. Upload New Template Test:"
cd /tmp && node test-insurance-tx.js 2>&1 | tail -5
echo ""

echo "5. Wait 10s for snapshot..."
sleep 10

echo "6. Check Routes Again:"
curl -s http://localhost:9200/providers 2>&1
echo ""
curl -s http://localhost:9200/insurance/providers 2>&1
