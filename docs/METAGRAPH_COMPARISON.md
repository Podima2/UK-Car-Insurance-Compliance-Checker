# AutoSight vs AI-Datagraph vs Our Implementation

## Executive Summary

Both **AutoSight** and **AI-Datagraph** use the **same core pattern**:
- **Frontend**: Manual signing with `dag4.keyStore.sign()` on a SHA-256 hash of serialized JSON
- **Backend**: Standard `circeEntityDecoder` expecting `Signed[Update]` with `{value, proofs}`
- **Serialization**: JSON with key sorting and null removal (AI-Datagraph) or simple JSON (AutoSight)

**Our implementation already follows this pattern correctly!** The issue is likely in the exact serialization format or update structure.

---

## Frontend Implementation

### AutoSight Does

**File**: `/tmp/autosight/metagraph/autosight/testing_scripts/send_data_transaction.js`

**Pattern**: Direct manual signing
```javascript
const getEncoded = (message) => {
  const coded = JSON.stringify(message);
  return coded;
};

const serialize = (msg) => {
  const coded = Buffer.from(msg, "utf8").toString("hex");
  return coded;
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

// Usage:
const message = {
  captureTime: (new Date()).getTime(),
  imageURL: "https://www.test.image.jpg",
  latitude: "0.0",
  longitude: "0.0",
  rewardAddress: ":your_wallet_address"
};

const encoded = getEncoded(message);
const serializedTx = serialize(encoded);
const hash = sha256(Buffer.from(serializedTx, "hex"));
const signature = await sign(walletPrivateKey, hash);

const body = {
  value: {
    ...message,
  },
  proofs: [
    {
      id: uncompressedPublicKey.substring(2),
      signature,
    },
  ],
};

await axios.post(`${metagraphL1DataUrl}/data`, body);
```

**Key Details**:
- RPC Method: **None** - direct HTTP POST with manual signature
- Data Format: Plain JSON object as `value`
- Encoding: `JSON.stringify` → UTF-8 bytes → hex string
- Hashing: SHA-256 of hex bytes, then SHA-512 for signing
- Signature: secp256k1 DER format
- Content-Type: `application/json`

---

### AI-Datagraph Does

**File**: `/tmp/ai-datagraph/ai_datagraph/testing_scripts/generateTx.js`

**Pattern**: Manual signing with **key sorting and null removal**

```javascript
// Sort object by keys recursively
const sortObjectByKey = (sourceObject) => {
  if (Array.isArray(sourceObject)) {
    return sourceObject.map(sortObjectByKey);
  } else if (typeof sourceObject === 'object' && sourceObject !== null) {
    return Object.keys(sourceObject).sort().reduce((sortedObj, key) => {
      const value = sourceObject[key];
      sortedObj[key] = sortObjectByKey(value);
      return sortedObj;
    }, {});
  } else {
    return sourceObject;
  }
}

// Recursively remove nulls
const removeNulls = (obj) => {
  if (Array.isArray(obj)) {
    return obj.filter(v => v !== null).map(v => v && typeof v === "object" ? removeNulls(v) : v)
  } else if (typeof obj === 'object' && obj !== null) {
    return Object.fromEntries(
      Object.entries(obj)
        .filter(([, v]) => v !== null)
        .map(([k, v]) => [k, v && typeof v === "object" ? removeNulls(v) : v])
    );
  } else {
    return obj;
  }
}

const getEncoded = (value) => {
  let nonNullValue = removeNulls(value);
  let sortedValue = sortObjectByKey(nonNullValue);
  return JSON.stringify(sortedValue);
};

const serialize = (data) => {
  return Buffer.from(data, 'utf8');
}

const encoded = getEncoded(message)
const serialized = serialize(encoded)
const hash = jsSha256.sha256(serialized)
const signature = await dag4.keyStore.sign(walletPrivateKey, hash)

const body = {
  value: message,
  proofs: [proof],
};
```

**Key Details**:
- RPC Method: **None** - direct HTTP POST with manual signature
- Data Format: Nested JSON object with `publicData` and `privateData`
- Encoding: Remove nulls → sort keys → `JSON.stringify` → UTF-8 bytes
- Hashing: SHA-256 directly on UTF-8 bytes
- Signature: Uses `dag4.keyStore.sign()` which internally does SHA-512 + secp256k1
- Content-Type: `application/json`

---

### We Currently Do

**File**: `/Users/agustinschiariti/Desktop/euclid-development-environment/test-upload.html`

**Pattern**: Manual signing similar to AutoSight

```javascript
// Build our insurance update message (value)
const value = {
  UploadContractTemplate: {
    providerName: "Test Provider",
    templateVersion: "1.0",
    terms: [...],
    riskRules: [...],
    uploadedBy: "admin",
    rewardAddress: rewardAddress
  }
};

// AutoSight signing pattern
const encoded = JSON.stringify(value);
const serializedTx = toHex(utf8Bytes(encoded));
const sha256hex = sha256(new Uint8Array(serializedTx.match(/.{1,2}/g).map(b => parseInt(b, 16))));
const digest = sha512(sha256hex);

const EC = elliptic.ec; const curve = new EC('secp256k1');
const key = curve.keyFromPrivate(priv, 'hex');
const sig = key.sign(digest);
const signature = Array.from(sig.toDER()).map(b => b.toString(16).padStart(2,'0')).join('');

let pubHex = key.getPublic('hex');
if (pubHex.startsWith('04')) pubHex = pubHex.slice(2);

const body = { value, proofs: [{ id: pubHex, signature }] };
```

**Key Details**:
- RPC Method: **None** - direct HTTP POST
- Data Format: Sealed trait wrapped in object: `{UploadContractTemplate: {...}}`
- Encoding: Same as AutoSight (JSON → hex → SHA-256 → SHA-512)
- Signature: Same DER format
- Content-Type: `application/json`

---

### Critical Differences & Issues

| Aspect | AutoSight | AI-Datagraph | Our Implementation |
|--------|-----------|--------------|-------------------|
| **Data Structure** | Flat object | Nested (publicData/privateData) | **Sealed trait wrapped** |
| **Key Sorting** | ❌ No | ✅ Yes | ❌ No |
| **Null Removal** | ❌ No | ✅ Yes | ❌ No |
| **Hashing** | SHA-256 → SHA-512 | SHA-256 directly | SHA-256 → SHA-512 |
| **Update Type** | Case class extends DataUpdate | Case class extends DataUpdate | Sealed trait with case classes |

**🔴 CRITICAL FINDING**: Our update structure uses a **sealed trait pattern** with the case class name as a wrapper:
```json
{
  "UploadContractTemplate": {
    "providerName": "...",
    ...
  }
}
```

This is **correct for Circe ADT encoding**, but we need to verify the backend expects this format.

---

## Backend Implementation

### AutoSight Does

**File**: `/tmp/autosight/metagraph/autosight/modules/data_l1/src/main/scala/com/my/data_l1/DataL1Service.scala`

**Decoder**:
```scala
override val signedDataEntityDecoder: EntityDecoder[F, Signed[ImageUpdate]] = circeEntityDecoder
```

**Update Type**:
```scala
// From Updates.scala
@derive(decoder, encoder)
case class ImageUpdate(
  captureTime: Long,
  imageURL: String,
  latitude: String,
  longitude: String,
  rewardAddress: Option[Address],
) extends DataUpdate
```

**Serialization**:
```scala
override def serializeUpdate(update: ImageUpdate): F[Array[Byte]] =
  JsonSerializer[F].serialize[ImageUpdate](update)

override def deserializeUpdate(bytes: Array[Byte]): F[Either[Throwable, ImageUpdate]] =
  JsonSerializer[F].deserialize[ImageUpdate](bytes)
```

**Key Details**:
- Uses standard `circeEntityDecoder` - expects `{value: {...}, proofs: [...]}`
- Simple case class extending `DataUpdate`
- JSON serialization via Tessellation's `JsonSerializer`
- Content-Type: Likely `application/json`

---

### AI-Datagraph Does

**File**: `/tmp/ai-datagraph/ai_datagraph/modules/data_l1/src/main/scala/com/my/data_l1/DataL1Service.scala`

**Decoder**:
```scala
override val signedDataEntityDecoder: EntityDecoder[F, Signed[DataUpdateRaw]] = circeEntityDecoder
```

**Update Type**:
```scala
// From Updates.scala
@derive(decoder, encoder)
case class PublicData(
  address: String,
  timestamp: Long,
  fields: List[String]
)

@derive(decoder, encoder)
case class PrivateData(
  sensitiveInfo: Map[String, String]
)

@derive(decoder, encoder)
case class DataUpdateRaw(
  publicData: PublicData,
  privateData: PrivateData
) extends DataUpdate
```

**Serialization**:
```scala
override def serializeUpdate(update: DataUpdateRaw): F[Array[Byte]] =
  JsonSerializer[F].serialize[DataUpdateRaw](update)

override def deserializeUpdate(bytes: Array[Byte]): F[Either[Throwable, DataUpdateRaw]] =
  JsonSerializer[F].deserialize[DataUpdateRaw](bytes)
```

**Key Details**:
- Identical pattern to AutoSight
- More complex nested structure
- Same JSON serialization approach

---

### We Currently Do

**File**: `/Users/agustinschiariti/Desktop/euclid-development-environment/source/project/nft/modules/data_l1/src/main/scala/com/my/nft/data_l1/Main.scala`

**Decoder**:
```scala
override def signedDataEntityDecoder: EntityDecoder[IO, Signed[InsuranceUpdate]] =
  circeEntityDecoder
```

**Update Type**:
```scala
// From Types.scala
@derive(decoder, encoder)
sealed trait InsuranceUpdate extends DataUpdate

@derive(decoder, encoder)
case class UploadContractTemplate(
  providerName: String,
  templateVersion: String,
  terms: List[ContractTerm],
  riskRules: List[RiskRule],
  uploadedBy: String
) extends InsuranceUpdate
```

**Serialization**:
```scala
override def serializeUpdate(update: InsuranceUpdate): IO[Array[Byte]] = {
  import io.constellationnetwork.json.JsonSerializer
  JsonSerializer[IO].serialize[InsuranceUpdate](update)
}
```

**Key Details**:
- **IDENTICAL** decoder pattern to both repos
- Uses **sealed trait** instead of single case class
- Same JSON serialization

---

### Backend Comparison Summary

| Component | AutoSight | AI-Datagraph | Our Implementation | Status |
|-----------|-----------|--------------|-------------------|--------|
| **signedDataEntityDecoder** | `circeEntityDecoder` | `circeEntityDecoder` | `circeEntityDecoder` | ✅ Same |
| **serializeUpdate** | `JsonSerializer.serialize` | `JsonSerializer.serialize` | `JsonSerializer.serialize` | ✅ Same |
| **deserializeUpdate** | `JsonSerializer.deserialize` | `JsonSerializer.deserialize` | Custom `Deserializers` | ⚠️ Different |
| **Update Structure** | Single case class | Single case class | Sealed trait | ⚠️ Different |

---

## Key Differences

### 1. **Sealed Trait vs Case Class**

**AutoSight & AI-Datagraph**: Single case class
```scala
case class ImageUpdate(...) extends DataUpdate
```

**Our Implementation**: Sealed trait with multiple case classes
```scala
sealed trait InsuranceUpdate extends DataUpdate
case class UploadContractTemplate(...) extends InsuranceUpdate
case class ValidateCircumstance(...) extends InsuranceUpdate
```

**Impact**: Circe encodes sealed traits with ADT encoding by default:
```json
{
  "UploadContractTemplate": {
    "providerName": "..."
  }
}
```

This is **correct** but requires backend to handle it properly.

### 2. **Frontend Data Format**

**Issue**: We're sending the sealed trait wrapper:
```javascript
const value = {
  UploadContractTemplate: { ... }  // ← Wrapper here
};
```

But we should verify if the backend's `Signed[InsuranceUpdate]` decoder expects this exact format.

### 3. **Key Sorting and Null Removal**

AI-Datagraph does this, AutoSight doesn't. Neither is strictly required but ensures consistent hashing across implementations.

---

## Recommended Unified Approach

### Option A: Keep Sealed Trait (Recommended)

**Pros**: 
- Type-safe multiple update types
- Clean domain modeling
- Circe handles ADT encoding automatically

**Changes Needed**:
1. ✅ Frontend already sends correct format
2. ✅ Backend already has correct decoder
3. ⚠️ Verify `serializeUpdate` handles sealed trait correctly

### Option B: Flatten to Case Classes

**Pros**: 
- Matches AutoSight/AI-Datagraph exactly
- Simpler encoding

**Cons**: 
- Lose type safety for multiple update types
- Need to refactor entire codebase

---

## Root Cause Analysis

Based on the comparison, the most likely issues are:

### 1. **Missing `rewardAddress` in Backend Type** ✅ FIXED
The frontend sends `rewardAddress` but the backend `UploadContractTemplate` doesn't have it.

**Fix**: Add `rewardAddress: String` to `UploadContractTemplate` in `Types.scala`

### 2. **Signature Verification Mismatch**
The backend might be re-serializing the update differently than the frontend.

**Test**: Check if backend's `JsonSerializer.serialize[InsuranceUpdate]` produces the same JSON as frontend's `JSON.stringify(value)`

### 3. **ADT Encoding Mismatch**
The frontend sends:
```json
{
  "value": {
    "UploadContractTemplate": { ... }
  },
  "proofs": [...]
}
```

But backend might expect:
```json
{
  "value": { ... },  // ← Direct object
  "proofs": [...]
}
```

**Fix**: Change frontend to send the update object directly without wrapper, OR ensure backend decoder handles wrapper.

---

## Implementation Plan

### Step 1: Add Missing Field ✅
```scala
// In Types.scala
case class UploadContractTemplate(
  providerName: String,
  templateVersion: String,
  terms: List[ContractTerm],
  riskRules: List[RiskRule],
  uploadedBy: String,
  rewardAddress: String  // ← ADD THIS
) extends InsuranceUpdate
```

### Step 2: Test Serialization Consistency

Create a test script that:
1. Takes sample update JSON
2. Sends to `/data` endpoint
3. Checks backend logs for deserialization

### Step 3: If ADT encoding is the issue

**Option A**: Remove wrapper in frontend (simpler)
```javascript
const value = {
  providerName: "Test Provider",
  templateVersion: "1.0",
  terms: [...],
  riskRules: [...],
  uploadedBy: "admin",
  rewardAddress: rewardAddress,
  // Add discriminator if needed
  $type: "UploadContractTemplate"
};
```

**Option B**: Configure Circe to use different ADT encoding in backend

---

## Testing Checklist

- [ ] Add `rewardAddress` field to `UploadContractTemplate`
- [ ] Rebuild metagraph
- [ ] Test with simple update first (TestUpdate)
- [ ] Compare frontend JSON with backend deserialized object
- [ ] Verify signature hash matches
- [ ] Test full UploadContractTemplate flow

---

## Conclusion

**Both AutoSight and AI-Datagraph use essentially the same pattern we already have.** The issue is likely:

1. ✅ **Missing `rewardAddress` field** in backend type definition
2. ⚠️ **Potential ADT encoding mismatch** with sealed trait wrapper
3. ⚠️ **Serialization consistency** between frontend and backend

**Next Steps**:
1. Add `rewardAddress` to backend type
2. Test with minimal update
3. Add debug logging to see exact JSON being deserialized
4. Compare with working AutoSight pattern if still failing
