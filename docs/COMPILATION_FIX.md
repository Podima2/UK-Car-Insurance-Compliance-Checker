# Compilation Fix - JsonSerializer Implicit Error

## Problem

**Error**: `could not find implicit value for evidence parameter of type io.constellationnetwork.json.JsonSerializer[cats.effect.IO]`

**Location**: `Main.scala:54` in `serializeUpdate` method

## Root Cause

The code was trying to use `JsonSerializer[IO]` locally:

```scala
override def serializeUpdate(update: InsuranceUpdate): IO[Array[Byte]] = {
  import io.constellationnetwork.json.JsonSerializer
  JsonSerializer[IO].serialize[InsuranceUpdate](update)  // ❌ No implicit in scope
}
```

### Why This Failed

In AutoSight and AI-Datagraph, `JsonSerializer[IO]` is created as an **implicit parameter** in the Resource chain in `Main.scala`:

```scala
// AutoSight pattern
override def dataApplication: Option[Resource[IO, BaseDataApplicationL1Service[IO]]] = (for {
  implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource  // ← Created here
  l1Service <- DataL1Service.make[IO].asResource  // ← Used here
} yield l1Service).some
```

Your code structure is different - you create the service inline in `Main.scala`, so the implicit isn't available.

## Solution

Use your existing `Serializers` object which already handles JSON serialization consistently across all methods.

### Changes Applied

#### 1. Updated Main.scala

**Before**:
```scala
override def serializeUpdate(update: InsuranceUpdate): IO[Array[Byte]] = {
  import io.constellationnetwork.json.JsonSerializer
  JsonSerializer[IO].serialize[InsuranceUpdate](update)
}
```

**After**:
```scala
override def serializeUpdate(update: InsuranceUpdate): IO[Array[Byte]] =
  IO(Serializers.serializeUpdate(update))
```

#### 2. Added serializeUpdate to Serializers.scala

**Added**:
```scala
def serializeUpdate(
  update: InsuranceUpdate
): Array[Byte] =
  serialize[InsuranceUpdate](update)
```

This matches the pattern already used for:
- `serializeState`
- `serializeBlock`
- `serializeCalculatedState`

## Why This is Better

### Consistency
All serialization now goes through the same `Serializers` object:
- ✅ `serializeState` → `Serializers.serializeState`
- ✅ `serializeUpdate` → `Serializers.serializeUpdate`
- ✅ `serializeBlock` → `Serializers.serializeBlock`
- ✅ `serializeCalculatedState` → `Serializers.serializeCalculatedState`

### Correct Serialization
The `Serializers.serialize` method uses:
```scala
serializableData.asJson.deepDropNullValues.noSpaces.getBytes(StandardCharsets.UTF_8)
```

This ensures:
1. JSON encoding via Circe (respects sealed trait ADT encoding)
2. Null values dropped (matches AI-Datagraph pattern)
3. Compact format (`.noSpaces`)
4. UTF-8 encoding

### Pattern Match
This now matches how AutoSight/AI-Datagraph serialize updates - via Circe JSON encoding to UTF-8 bytes.

## Files Modified

1. **`source/project/nft/modules/data_l1/src/main/scala/com/my/nft/data_l1/Main.scala`**
   - Line 52-53: Changed `serializeUpdate` to use `Serializers`

2. **`source/project/nft/modules/shared_data/src/main/scala/com/my/nft/shared_data/serializers/Serializers.scala`**
   - Lines 24-27: Added `serializeUpdate` method

3. **`source/project/nft/modules/shared_data/src/main/scala/com/my/nft/shared_data/types/Types.scala`**
   - Line 134: Added `rewardAddress: String` to `UploadContractTemplate`

## Next Steps

```bash
# Rebuild with fixes
cd ~/Desktop/euclid-development-environment/source/project/nft
./scripts/hydra build

# Should compile successfully now
```

## Verification

After rebuild, run the test:
```bash
cd ~/Desktop/euclid-development-environment
./scripts/test-metagraph-pattern.sh
```

This will verify:
1. ✅ Compilation succeeds
2. ✅ Metagraph starts correctly
3. ✅ Signed data is accepted
4. ✅ Serialization/deserialization works end-to-end

---

**Status**: Ready to build
**Last Updated**: 2025-11-06
