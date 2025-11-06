# Import Genesis Wallet to Stargazer

If Stargazer shows "Metagraph id not found" when sending metagraph data transactions, register the token or use a genesis wallet that already has metagraph tokens.

## Step 1: Extract Genesis Private Key

Run:

```bash
docker exec metagraph-node-1 bash -c "\
  cd /code/metagraph-l0 && \
  java -jar cl-keytool.jar export \
    --keystore token-key.p12 \
    --alias token-key \
    --storepass password"
```

This prints a hex private key.

## Step 2: Import in Stargazer

1. Open Stargazer
2. Profile → Import Wallet → Private Key
3. Paste the private key
4. Name it "Genesis Wallet"

## Step 3: Switch to Genesis Account

Select the imported account in Stargazer.

## Step 4: Verify Balance (optional)

```bash
# Replace with the DAG address shown in Stargazer after import
curl "http://localhost:9200/currency/l1/balance/DAG_ADDRESS"
```

You should see a large balance if genesis was applied.
