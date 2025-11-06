#!/bin/bash
# Test Metagraph Pattern - Verify signed data ingestion works
# Based on AutoSight and AI-Datagraph working patterns

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Metagraph Pattern Verification${NC}"
echo -e "${BLUE}========================================${NC}"

# Configuration
DATA_L1_URL="${DATA_L1_URL:-http://localhost:9400}"
GLOBAL_L0_URL="${GLOBAL_L0_URL:-http://localhost:9000}"

echo -e "\n${YELLOW}Configuration:${NC}"
echo "  Data L1 URL: $DATA_L1_URL"
echo "  Global L0 URL: $GLOBAL_L0_URL"

# Check if Data L1 is running
echo -e "\n${YELLOW}Step 1: Checking Data L1 availability...${NC}"
if curl -s -f "$DATA_L1_URL/cluster/info" > /dev/null 2>&1; then
  echo -e "${GREEN}✓${NC} Data L1 is responding"
else
  echo -e "${RED}✗${NC} Data L1 is not responding at $DATA_L1_URL"
  echo "  Please start the metagraph first:"
  echo "  cd source/project/nft && ./scripts/hydra start-genesis"
  exit 1
fi

# Check Node.js for test script
echo -e "\n${YELLOW}Step 2: Checking Node.js installation...${NC}"
if command -v node &> /dev/null; then
  NODE_VERSION=$(node --version)
  echo -e "${GREEN}✓${NC} Node.js $NODE_VERSION is installed"
else
  echo -e "${RED}✗${NC} Node.js is not installed"
  echo "  Install Node.js from https://nodejs.org/"
  exit 1
fi

# Create test script using AutoSight pattern
echo -e "\n${YELLOW}Step 3: Creating test transaction script...${NC}"

cat > /tmp/test-insurance-tx.js << 'EOF'
const { dag4 } = require("@stardust-collective/dag4");
const jsSha256 = require("js-sha256");
const jsSha512 = require("js-sha512");
const EC = require("elliptic");
const axios = require("axios");

const curve = new EC.ec("secp256k1");

const getEncoded = (message) => {
  return JSON.stringify(message);
};

const serialize = (msg) => {
  return Buffer.from(msg, "utf8").toString("hex");
};

const sha256 = (hash) => {
  return jsSha256.sha256(hash);
};

const sha512 = (hash) => {
  return jsSha512.sha512(hash);
};

const sign = async (privateKey, msg) => {
  const sha512Hash = sha512(msg);
  const ecSig = curve.sign(sha512Hash, Buffer.from(privateKey, "hex"));
  return Buffer.from(ecSig.toDER()).toString("hex");
};

const sendTestTransaction = async (dataL1Url) => {
  console.log("\n📤 Generating test transaction...\n");

  // Generate wallet
  const walletPrivateKey = dag4.keyStore.generatePrivateKey();
  const account1 = dag4.createAccount();
  account1.loginPrivateKey(walletPrivateKey);

  console.log("Generated wallet:");
  console.log("  Address:", account1.address);
  console.log("  Public Key:", account1.publicKey);

  // Test message - using sealed trait ADT encoding
  const message = {
    UploadContractTemplate: {
      providerName: "Test Provider",
      templateVersion: "1.0",
      terms: [{
        category: "Test Category",
        description: "Minimal test term",
        voidingConditions: ["none"],
        notificationRequired: false
      }],
      riskRules: [{
        keywords: ["test"],
        category: "Test Category",
        riskWeight: 1,
        requiresNotification: false,
        explanation: "minimal"
      }],
      uploadedBy: "admin",
      rewardAddress: account1.address
    }
  };

  console.log("\n📝 Message (value):");
  console.log(JSON.stringify(message, null, 2));

  // AutoSight signing pattern
  const encoded = getEncoded(message);
  const serializedTx = serialize(encoded);
  const hash = sha256(Buffer.from(serializedTx, "hex"));
  const signature = await sign(walletPrivateKey, hash);

  const publicKey = account1.publicKey;
  const uncompressedPublicKey = publicKey.length === 128 ? "04" + publicKey : publicKey;

  const body = {
    value: message,
    proofs: [
      {
        id: uncompressedPublicKey.substring(2),
        signature,
      },
    ],
  };

  console.log("\n🔐 Signature:");
  console.log("  Hash (SHA-256):", hash);
  console.log("  Signature:", signature.substring(0, 32) + "...");
  console.log("  Public Key ID:", uncompressedPublicKey.substring(2, 18) + "...");

  try {
    console.log(`\n🚀 POSTing to ${dataL1Url}/data`);
    const response = await axios.post(`${dataL1Url}/data`, body, {
      headers: { 'Content-Type': 'application/json' }
    });
    console.log("\n✅ SUCCESS!");
    console.log("Response:", JSON.stringify(response.data, null, 2));
    return true;
  } catch (e) {
    console.log("\n❌ FAILED!");
    if (e.response) {
      console.log("HTTP Status:", e.response.status);
      console.log("Response:", JSON.stringify(e.response.data, null, 2));
    } else {
      console.log("Error:", e.message);
    }
    return false;
  }
};

const dataL1Url = process.env.DATA_L1_URL || "http://localhost:9400";
sendTestTransaction(dataL1Url).then(success => {
  process.exit(success ? 0 : 1);
});
EOF

echo -e "${GREEN}✓${NC} Test script created at /tmp/test-insurance-tx.js"

# Install dependencies if needed
echo -e "\n${YELLOW}Step 4: Installing Node.js dependencies...${NC}"
cd /tmp
if [ ! -d "node_modules" ]; then
  npm install --silent @stardust-collective/dag4 js-sha256 js-sha512 elliptic axios 2>&1 | grep -v "npm WARN"
fi
echo -e "${GREEN}✓${NC} Dependencies ready"

# Run the test
echo -e "\n${YELLOW}Step 5: Sending test transaction...${NC}"
echo "========================================"

if DATA_L1_URL=$DATA_L1_URL node /tmp/test-insurance-tx.js; then
  echo ""
  echo -e "${GREEN}========================================${NC}"
  echo -e "${GREEN}✓ TEST PASSED${NC}"
  echo -e "${GREEN}========================================${NC}"
  echo ""
  echo "The metagraph successfully accepted the signed data!"
  echo ""
  echo "Next steps:"
  echo "  1. Check metagraph logs for processing"
  echo "  2. Query the state to see the uploaded template"
  echo "  3. Try uploading from test-upload.html"
  exit 0
else
  echo ""
  echo -e "${RED}========================================${NC}"
  echo -e "${RED}✗ TEST FAILED${NC}"
  echo -e "${RED}========================================${NC}"
  echo ""
  echo "Common issues:"
  echo "  1. Metagraph not running - start with: cd source/project/nft && ./scripts/hydra start-genesis"
  echo "  2. Type mismatch - check if UploadContractTemplate has rewardAddress field"
  echo "  3. Signature verification failed - check serialization consistency"
  echo ""
  echo "Debug steps:"
  echo "  1. Check metagraph logs: docker logs -f nft-container-data-l1-1"
  echo "  2. Review comparison doc: docs/METAGRAPH_COMPARISON.md"
  echo "  3. Verify Types.scala has all required fields"
  exit 1
fi
