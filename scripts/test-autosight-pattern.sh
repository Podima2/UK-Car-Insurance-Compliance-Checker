#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GLOBAL_L0_URL:-}" || -z "${DATA_L1_URL:-}" || -z "${REWARD_ADDRESS:-}" ]]; then
  echo "Usage: GLOBAL_L0_URL=http://localhost:9000 DATA_L1_URL=http://localhost:9400 REWARD_ADDRESS=DAG... $0"
  exit 1
fi

TMP_DIR=$(mktemp -d)
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

pushd "$TMP_DIR" >/dev/null
npm init -y >/dev/null 2>&1
npm i @stardust-collective/dag4 js-sha256 js-sha512 elliptic axios >/dev/null 2>&1

cat > send_test.js <<'JS'
const { dag4 } = require("@stardust-collective/dag4");
const jsSha256 = require("js-sha256");
const jsSha512 = require("js-sha512");
const EC = require("elliptic");
const axios = require("axios");

const curve = new EC.ec("secp256k1");

const getEncoded = (message) => JSON.stringify(message);
const serialize = (msg) => Buffer.from(msg, "utf8").toString("hex");
const sha256 = (bytesHex) => jsSha256.sha256(Buffer.from(bytesHex, "hex"));
const sha512 = (data) => jsSha512.sha512(data);

const sign = (privateKey, msg) => {
  const digest = sha512(msg);
  const ecSig = curve.sign(digest, Buffer.from(privateKey, "hex"));
  return Buffer.from(ecSig.toDER()).toString("hex");
};

(async () => {
  const globalL0Url = process.argv[2];
  const dataL1Url = process.argv[3];
  const rewardAddress = process.argv[4];

  const walletPrivateKey = dag4.keyStore.generatePrivateKey();
  const account = dag4.createAccount();
  account.loginPrivateKey(walletPrivateKey);
  account.connect({ networkVersion: "2.0", l0Url: globalL0Url, testnet: true });

  const message = {
    captureTime: Date.now(),
    imageURL: "https://www.test.image.jpg",
    latitude: "0.0",
    longitude: "0.0",
    rewardAddress: rewardAddress,
  };

  const encoded = getEncoded(message);
  const serializedTx = serialize(encoded);
  const hash = sha256(serializedTx);
  const signature = sign(walletPrivateKey, hash);

  const publicKey = account.publicKey;
  const uncompressedPublicKey = publicKey.length === 128 ? "04" + publicKey : publicKey;

  const body = {
    value: { ...message },
    proofs: [ { id: uncompressedPublicKey.substring(2), signature } ],
  };

  try {
    const resp = await axios.post(`${dataL1Url}/data`, body, { headers: { 'Content-Type': 'application/json' } });
    console.log(JSON.stringify(resp.data));
  } catch (e) {
    if (e.response) {
      console.error("HTTP", e.response.status, e.response.data);
    } else {
      console.error(e.message);
    }
    process.exit(1);
  }
})();
JS

node send_test.js "$GLOBAL_L0_URL" "$DATA_L1_URL" "$REWARD_ADDRESS"
popd >/dev/null
