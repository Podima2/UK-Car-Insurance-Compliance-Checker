# DOR Metagraph Pattern Analysis - Complete Implementation Guide

## Executive Summary

**Key Discovery**: DOR's signature validation works because:
1. Client sends **CBOR-encoded data** with embedded signature
2. Decoder extracts signature and data from CBOR
3. `serializeUpdate` returns **only the hash field bytes** (NOT the entire update!)
4. Framework validates signature against the hash bytes
5. This matches what the client originally signed

## DOR's Data Flow (Step by Step)

### 1. Client-Side (Device)
```typescript
// Device creates check-in data
const checkInData = {
  ac: [timestamps],
  dts: currentTimestamp,
  e: [events]
};

// STEP 1: Encode to CBOR
const cborBytes = encodeToCBOR(checkInData);
const cborHex = bytesToHex(cborBytes);

// STEP 2: Hash the CBOR
const hash = sha256(cborBytes);

// STEP 3: Sign the HASH (this is what gets validated!)
const signature = privateKey.sign(hash);

// STEP 4: Build envelope with CBOR + signature
const envelope = {
  cbor: cborHex,
  hash: hashToHex(hash),
  id: publicKeyHex,
  sig: signatureHex
};

// STEP 5: Encode entire envelope to CBOR
const finalCBOR = encodeToCBOR(envelope);

// STEP 6: Send as hex string with Content-Type: text/plain
const hexString = bytesToHex(finalCBOR);
await fetch('/data', {
  method: 'POST',
  headers: { 'Content-Type': 'text/plain' },
  body: hexString
});
```

### 2. Backend Decoder (Decoders.scala)

```scala
def signedDataEntityDecoder[F[_]: Async: Env]: EntityDecoder[F, Signed[CheckInUpdate]] = {
  EntityDecoder.decodeBy(MediaType.text.plain) { msg =>
    val rawText = msg.as[String]  // Get hex string
    val signed = rawText.flatMap { text =>
      val bodyAsBytes = getByteArrayFromRequestBody(text)  // Hex → bytes
      buildSignedUpdate(bodyAsBytes)  // CBOR decode → Signed[Update]
    }
    DecodeResult.success(signed)
  }
}

private def buildSignedUpdate[F[_]: Async: Env](cborData: Array[Byte]): F[Signed[CheckInUpdate]] = {
  // Decode CBOR envelope
  val decoded = Cbor.decode(cborData).to[DeviceCheckInWithSignature].value
  
  // Extract signature
  val signatureProof = SignatureProof(Id(Hex(decoded.id)), Signature(Hex(decoded.sig)))
  val proofs = NonEmptySet.fromSetUnsafe(SortedSet(signatureProof))
  
  // Decode inner CBOR data
  checkInInfo <- getDeviceCheckInInfo(decoded.cbor)
  
  // Build CheckInUpdate
  checkInUpdate = CheckInUpdate(
    decoded.id,
    decoded.sig,
    checkInInfo.dts,
    decoded.hash,  // ⭐ This is what gets validated!
    maybeD or APIResponse
  )
  
  yield Signed(checkInUpdate, proofs)
}
```

### 3. Signature Validation (Framework)

```scala
// When framework validates, it calls:
override def serializeUpdate(update: CheckInUpdate): IO[Array[Byte]] =
  IO(Serializers.serializeUpdate(update))

// Serializers.scala - THE KEY!
def serializeUpdate(update: CheckInUpdate): Array[Byte] =
  update.dtmCheckInHash.getBytes(StandardCharsets.UTF_8)  // ⭐ ONLY THE HASH!
```

**Framework logic**:
1. Get bytes from `serializeUpdate` → hash bytes
2. Verify signature against those bytes
3. ✅ Matches because client signed the hash!

## Why DOR Works and Ours Doesn't

### DOR Pattern
```
Client: Sign(hash) → Signature
Server: serializeUpdate → hash bytes
Framework: Verify(hash bytes, signature) → ✅ MATCH
```

### Our Current Pattern
```
Client: Sign(base64(JSON)) → Signature  
Server: serializeUpdate → JSON bytes
Framework: Verify(JSON bytes, signature) → ❌ MISMATCH
```

**The bytes don't match!**

## Implementation for Insurance Metagraph

### Option A: Follow DOR's CBOR Pattern (RECOMMENDED)

This requires significant changes but follows Constellation's reference pattern:

#### 1. Add CBOR Support to Backend

**File**: `modules/shared_data/src/main/scala/com/my/nft/shared_data/types/Codecs.scala`
```scala
package com.my.nft.shared_data.types

import io.bullet.borer.derivation.MapBasedCodecs.deriveCodec
import io.bullet.borer.{Codec, Decoder, Encoder}
import Types._

object Codecs {
  implicit val uploadTemplateCodec: Codec[UploadContractTemplate] = deriveCodec
  implicit val validateCircumstanceCodec: Codec[ValidateCircumstance] = deriveCodec
  
  // Envelope for signed data
  case class InsuranceUpdateWithSignature(
    data: String,  // CBOR hex
    hash: String,  // SHA-256 of data
    id: String,    // Public key
    sig: String    // Signature
  )
  
  implicit val insuranceWithSignatureCodec: Codec[InsuranceUpdateWithSignature] = deriveCodec
}
```

#### 2. Update Decoder

**File**: `modules/data_l1/src/main/scala/com/my/nft/data_l1/decoders/InsuranceDecoders.scala`
```scala
def signedDataEntityDecoder[F[_]: Async]: EntityDecoder[F, Signed[InsuranceUpdate]] = {
  EntityDecoder.decodeBy(MediaType.text.plain) { msg =>
    val rawText = msg.as[String]
    val signed = rawText.flatMap { text =>
      val bodyAsBytes = hexStringToBytes(text)
      buildSignedUpdate(bodyAsBytes)
    }
    DecodeResult.success(signed)
  }
}

private def buildSignedUpdate[F[_]: Async](cborData: Array[Byte]): F[Signed[InsuranceUpdate]] = {
  val envelope = Cbor.decode(cborData).to[InsuranceUpdateWithSignature].value
  
  val signatureProof = SignatureProof(Id(Hex(envelope.id)), Signature(Hex(envelope.sig)))
  val proofs = NonEmptySet.fromSetUnsafe(SortedSet(signatureProof))
  
  for {
    dataBytes <- hexStringToBytes(envelope.data)
    update <- Async[F].fromEither(Cbor.decode(dataBytes).to[InsuranceUpdate].value)
  } yield Signed(update, proofs)
}
```

#### 3. Critical: Update serializeUpdate

**File**: `modules/data_l1/src/main/scala/com/my/nft/data_l1/Main.scala`
```scala
override def serializeUpdate(update: InsuranceUpdate): IO[Array[Byte]] = {
  // Return the hash that was signed!
  // We need to store the hash in the update or recompute it
  IO(update.signedHash.getBytes(StandardCharsets.UTF_8))
}
```

#### 4. Frontend Client

**File**: `sample-ui/src/utils/constellationSigning.ts`
```typescript
import cbor from 'cbor';
import { createHash } from 'crypto-browserify';

export async function signInsuranceUpdate(
  update: any,
  provider: any,
  account: string
): Promise<string> {
  
  // STEP 1: Encode update to CBOR
  const updateCBOR = cbor.encode(update);
  const updateHex = Buffer.from(updateCBOR).toString('hex');
  
  // STEP 2: Hash the CBOR
  const hash = createHash('sha256').update(updateCBOR).digest();
  const hashHex = hash.toString('hex');
  
  // STEP 3: Sign the HASH using Stargazer
  // Note: We need to sign the hash, not base64-encoded JSON!
  const hashBase64 = hash.toString('base64');
  const signature = await provider.request({
    method: 'dag_signData',
    params: [account, hashBase64]
  });
  
  // STEP 4: Get public key
  const publicKey = await provider.request({
    method: 'dag_getPublicKey',
    params: [account]
  });
  
  // STEP 5: Build envelope
  const envelope = {
    data: updateHex,
    hash: hashHex,
    id: publicKey,
    sig: signature
  };
  
  // STEP 6: Encode envelope to CBOR
  const envelopeCBOR = cbor.encode(envelope);
  const envelopeHex = Buffer.from(envelopeCBOR).toString('hex');
  
  return envelopeHex;
}
```

### Option B: Simpler Approach (Hash-Only Signing)

Store the hash in the update and only validate against that:

#### Backend Change
```scala
// In Types.scala - add hash field to updates
case class UploadContractTemplate(
  providerName: String,
  // ... other fields
  signedHash: String  // ⭐ ADD THIS
) extends InsuranceUpdate

// In Serializers.scala
def serializeUpdate(update: InsuranceUpdate): Array[Byte] = {
  update match {
    case u: UploadContractTemplate => u.signedHash.getBytes(StandardCharsets.UTF_8)
    case v: ValidateCircumstance => v.signedHash.getBytes(StandardCharsets.UTF_8)
  }
}
```

#### Frontend
```typescript
// Compute hash of the update (minus the hash field)
const updateWithoutHash = { ...update };
delete updateWithoutHash.signedHash;

const dataString = JSON.stringify(updateWithoutHash);
const hash = sha256(dataString);

// Sign the hash
const signature = await signData(hash);

// Add hash to update
const finalUpdate = {
  ...updateWithoutHash,
  signedHash: hash
};

// Submit with signature
const envelope = {
  value: finalUpdate,
  proofs: [{ id: publicKey, signature }]
};
```

## Recommendation

**Use Option B (Hash-Only Signing)** because:
1. Simpler - no CBOR dependencies in frontend
2. Keeps JSON format
3. Follows DOR's core principle: validate against hash, not full data
4. Less code changes required

## Next Steps

1. Add `signedHash` field to InsuranceUpdate types
2. Update `serializeUpdate` to return only hash bytes
3. Update frontend to compute hash and sign it
4. Test end-to-end

This will make signature validation work!
