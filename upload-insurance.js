#!/usr/bin/env node

const EC = require('elliptic').ec;
const crypto = require('crypto');
const http = require('http');
const fs = require('fs');

// Configuration
const PRIVATE_KEY = '70224168fb7627aa65c3a2855edd1f7c2179225bae6eb4c76597b6592fcff984';
const DATA_L1_URL = 'http://localhost:9400/data';

// Read template
const templatePath = process.argv[2] || './test-template.json';
const templateData = JSON.parse(fs.readFileSync(templatePath, 'utf8'));

console.log('📄 Template:', templateData.UploadContractTemplate.providerName);

// Create EC instance for secp256k1 (used by Constellation)
const ec = new EC('secp256k1');
const keyPair = ec.keyFromPrivate(PRIVATE_KEY, 'hex');
const publicKey = keyPair.getPublic('hex');

console.log('🔑 Public Key:', publicKey);

// Sign the JSON message
const messageStr = JSON.stringify(templateData);
const messageHash = crypto.createHash('sha256').update(messageStr).digest();
const signature = keyPair.sign(messageHash);
const signatureHex = signature.toDER('hex');

console.log('✍️  Signature:', signatureHex);

// Create signed envelope
const signedEnvelope = {
  value: templateData,
  proofs: [{
    id: publicKey,
    signature: signatureHex
  }]
};

console.log('📦 Uploading to', DATA_L1_URL, '...');

// Upload
const postData = JSON.stringify(signedEnvelope);
const url = new URL(DATA_L1_URL);

const options = {
  hostname: url.hostname,
  port: url.port,
  path: url.pathname,
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(postData)
  }
};

const req = http.request(options, (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    console.log('\n📥 Response [', res.statusCode, ']:');
    console.log(data || '(empty)');
    
    if (res.statusCode >= 200 && res.statusCode < 300) {
      console.log('\n✅ Success! Wait a few seconds, then check:');
      console.log('   http://localhost:9200/data-application/providers');
    }
  });
});

req.on('error', (e) => {
  console.error('\n❌ Error:', e.message);
  process.exit(1);
});

req.write(postData);
req.end();
