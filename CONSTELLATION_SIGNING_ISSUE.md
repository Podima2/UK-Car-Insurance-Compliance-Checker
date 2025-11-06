# Constellation Signing Issue - Root Cause Analysis

## The Problem

**Error**: `{"error":"Invalid signature in data transactions"}`  
**HTTP Status**: 400 Bad Request  
**Endpoint**: `POST http://localhost:9400/data`

## Root Cause

Constellation's `/data` endpoint performs **cryptographic signature validation at the HTTP framework level** before any custom decoder logic runs. The validation is failing because:

1. **What Stargazer Signs**: Base64-encoded JSON string
2. **What Constellation Validates**: Serialized binary representation of the update
3. **Mismatch**: These are different byte sequences, so signatures don't match

## Constellation's Standard Signing Process

According to Constellation's architecture, the proper flow is:

```
1. Serialize Update → Binary bytes
2. Hash the bytes → SHA-256
3. Sign the hash → ECDSA signature
4. Wrap in Signed[Update] envelope
5. Submit to /data endpoint
6. Framework validates signature against original bytes
```

## Current Implementation (Stargazer)

```
1. JSON.stringify(update)
2. Base64 encode
3. Stargazer signs the base64 string
4. We wrap in {value, proofs} envelope
5. Submit to /data
6. ❌ Framework rejects - signature doesn't match serialized bytes
```

## The Gap

**Stargazer wallet's `dag_signData`** is designed for signing arbitrary data strings, NOT for Constellation's metagraph data ingestion pipeline which requires:

1. Proper serialization (matching `serializeUpdate` method)
2. Signature over the exact serialized bytes
3. Constellation's specific `Signed[T]` envelope format

## Solutions (In Order of Correctness)

### Option 1: Use Constellation's SDK/Tooling ✅ RECOMMENDED
**Status**: Standard procedure, production-ready

The proper way is to use Constellation's own signing utilities that:
- Serialize updates correctly
- Sign the binary representation
- Create proper `Signed[Update]` envelopes

**Implementation**:
- Use Constellation's Scala SDK
- Or use their TypeScript SDK (if available)
- Or use `cl-wallet` tool for signing

**Challenge**: Requires understanding Constellation's serialization format

### Option 2: Match Constellation's Serialization Format
**Status**: Complex but follows standards

Implement the exact serialization logic that `serializeUpdate` uses:
1. In frontend, serialize the update to bytes (matching backend logic)
2. Sign those bytes with Stargazer
3. Submit with proper proof format

**Challenge**: Frontend must replicate Scala serialization logic exactly

### Option 3: Bypass Framework Validation (Development Only)
**Status**: Not standard, but practical for development

Create a custom endpoint that:
- Doesn't use Constellation's `/data` endpoint
- Accepts our signed envelope format
- Manually validates signatures
- Submits to metagraph state

**Challenge**: Not production-ready, doesn't follow standards

## Recommended Path Forward

Since you want to **follow standard Constellation procedure**, here's what we need:

### Step 1: Understand Constellation's Serialization

Check what `serializeUpdate` actually produces:
```scala
override def serializeUpdate(update: InsuranceUpdate): IO[Array[Byte]] = {
  import io.circe.syntax._
  IO(update.asJson.noSpaces.getBytes("UTF-8"))
}
```

This converts to JSON then UTF-8 bytes.

### Step 2: Match in Frontend

The frontend needs to:
1. Serialize the update EXACTLY as backend does
2. Have Stargazer sign those exact bytes
3. Create the proper Signed envelope

### Step 3: Use Correct Signing Method

**Problem**: `dag_signData` in Stargazer expects a base64 string, but we need to sign raw bytes.

**Solution**: We need to either:
- Find Stargazer's raw byte signing method
- Or adjust our approach to match what Stargazer provides

## Investigation Needed

1. **Check Stargazer Documentation**: Does it have a method to sign raw bytes?
2. **Check Constellation SDK**: Is there a JavaScript/TypeScript SDK for proper signing?
3. **Inspect Working Example**: Look at how the DOR project (from your reference) does it with CBOR

## Temporary Workaround

Until we implement proper Constellation signing, we could:

1. **Skip signature validation for development**: Add a custom endpoint
2. **Use test signatures**: Generate them backend-side
3. **Direct state manipulation**: Use the lifecycle functions directly

But these are **NOT standard procedure**.

## Next Steps

To follow Constellation standards properly, we need to:

1. ✅ Research Constellation's JavaScript SDK (if exists)
2. ✅ Find proper signing utilities in Constellation ecosystem  
3. ✅ Implement frontend signing that matches backend serialization
4. ✅ Test with proper Constellation signing tools

**OR**

Contact Constellation team for guidance on proper Stargazer integration with metagraph data ingestion.

---

**Current Status**: Implementation is 90% complete, but blocked by signature format mismatch.  
**Blocker**: Need proper Constellation-compatible signing in frontend.  
**Alternative**: Reach out to Constellation community for Stargazer + metagraph data ingestion guidance.
