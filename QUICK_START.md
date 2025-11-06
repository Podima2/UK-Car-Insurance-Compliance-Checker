# 🚀 Quick Start - Stargazer Integration Testing

## Prerequisites
- ✅ Docker running
- ✅ Stargazer wallet installed from [stargazer.io](https://stargazer.io/)

## Step 1: Build & Start Backend (5-10 minutes)
```bash
cd /Users/agustinschiariti/Desktop/euclid-development-environment

# Build metagraph with updated decoder
./scripts/hydra build

# Start from genesis
./scripts/hydra start-genesis
```

Wait for: `data-l1 started successfully`

## Step 2: Start Frontend
```bash
cd source/project/nft/sample-ui
npm install  # if needed
npm start
```

Open: **http://localhost:6542/admin/upload**

## Step 3: Upload Template
1. Click "Connect Stargazer Wallet"
2. Approve in Stargazer popup
3. Select `test-template.json`
4. Click "Sign & Upload"
5. Approve signature in Stargazer
6. Wait for success message

## Step 4: Verify
```bash
curl http://localhost:9200/data-application/providers
# Should return: ["SafeDrive Insurance"]
```

## 🎉 Success!
Navigate to **http://localhost:6542/validate** to test risk validation.

---

## 📁 Files Created/Modified

### Backend
- `modules/data_l1/src/main/scala/.../InsuranceDecoders.scala` - Updated for Stargazer format

### Frontend
- `src/hooks/useStargazerWallet.ts` - NEW: Wallet integration
- `src/views/AdminUpload.tsx` - NEW: Upload page
- `src/styles/AdminUpload.css` - NEW: Styling
- `src/routes.tsx` - Added `/admin/upload` route
- `.env` - Added `REACT_APP_DATA_L1_URL`

---

## 🔍 Debug Commands
```bash
# Check if metagraph is running
docker ps

# Check Data L1 logs
docker logs metagraph-node-1 | tail -50

# Check providers
curl http://localhost:9200/data-application/providers

# Check node info
curl http://localhost:9200/node/info
```

---

**Full Documentation**: See `STARGAZER_INTEGRATION_COMPLETE.md`
