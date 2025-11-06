# AutoSight vs Our Implementation

## Frontend

### AutoSight Does:
- Does not use wallet RPC (no `dag_sendMetagraphDataTransaction`, `dag_signData`, or `dag_signMessage`).
- Manually constructs a Signed envelope: `{ value: <ImageUpdate>, proofs: [{ id, signature }] }`.
- Encodes the update as a JSON string; computes `sha256(utf8_bytes(json))` → hex string; then computes `sha512(hex_string)` and signs with secp256k1; `id` is the uncompressed public key without the `0x04` prefix.
- Sends HTTP POST `application/json` to `<Data L1>/data` with the envelope body. No `wallet_watchAsset`.

Code reference: `metagraph/autosight/testing_scripts/send_data_transaction.js`

```js
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

const sign = (priv, msg) => {
  const digest = sha512(msg); // sha512(hex(sha256(bytes(json))))
  const ecSig = curve.sign(digest, Buffer.from(priv, "hex"));
  return Buffer.from(ecSig.toDER()).toString("hex");
};

// body = { value: message, proofs: [{ id: uncompressedPubKeyNo04, signature }] }
// POST to: <DATA_L1_URL>/data
```

### We Currently Do:
- [Fill in based on our current frontend/template upload flow]
- [Paste short snippet here]

### Changes Needed:
- Build the exact `{ value, proofs }` envelope client-side.
- Use the same hashing/signing steps: utf8(JSON) → hex → sha256 → hex → sha512 → secp256k1 sign (DER → hex).
- Set `proofs[0].id` to the uncompressed public key without the leading `04`.
- POST JSON to `<Data L1>/data`.

## Backend

### AutoSight Does:
- Decoder: uses http4s Circe entity decoder for `Signed[ImageUpdate]` (expects `application/json`).
- Serialization: `serializeUpdate` uses `JsonSerializer.serialize[ImageUpdate]` (Tessellation JsonBinaryCodec), symmetric for de/serialization across L0 and Data L1.

Key parts:

`metagraph/autosight/modules/data_l1/src/main/scala/com/my/data_l1/DataL1Service.scala`

```scala
override val signedDataEntityDecoder: EntityDecoder[F, Signed[ImageUpdate]] = circeEntityDecoder
override def serializeUpdate(update: ImageUpdate): F[Array[Byte]] = JsonSerializer[F].serialize[ImageUpdate](update)
```

`metagraph/autosight/modules/l0/src/main/scala/com/my/metagraph_l0/ML0Service.scala`

```scala
override val signedDataEntityDecoder: EntityDecoder[F, Signed[ImageUpdate]] = circeEntityDecoder
override def serializeUpdate(update: ImageUpdate): F[Array[Byte]] = JsonSerializer[F].serialize[ImageUpdate](update)
```

Expected payload structure:

```json
{
  "value": {
    "captureTime": 1730840000000,
    "imageURL": "https://www.test.image.jpg",
    "latitude": "0.0",
    "longitude": "0.0",
    "rewardAddress": "DAG..."
  },
  "proofs": [
    { "id": "<uncompressed_pubkey_without_04>", "signature": "<der_hex_sig>" }
  ]
}
```

### We Currently Do:
- [Describe our current http4s decoder and serialization for insurance upload]
- [Paste short snippet]

### Changes Needed:
- Ensure `signedDataEntityDecoder` is `circeEntityDecoder` for `Signed[OurUpdate]` and Content-Type `application/json`.
- Ensure `serializeUpdate` uses `JsonSerializer.serialize[OurUpdate]` and that the client signs exactly the same bytes.
- Accept `{ value, proofs }` exactly.

## Key Differences:
1. AutoSight signs client-side and posts directly to `/data` with a `{ value, proofs }` JSON envelope; no wallet RPC and no metagraph transaction RPC.
2. AutoSight’s decoder expects a Circe-decoded `Signed[T]` JSON body; serialization uses Tessellation’s `JsonSerializer` for signature verification consistency.
3. Their `id` is the uncompressed public key minus the `04` prefix; signature is DER hex.
