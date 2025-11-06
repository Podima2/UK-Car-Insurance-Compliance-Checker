#!/usr/bin/env node

const { dag4 } = require('@stardust-collective/dag4');
const http = require('http');
const fs = require('fs');

// Configuration
const PRIVATE_KEY = '70224168fb7627aa65c3a2855edd1f7c2179225bae6eb4c76597b6592fcff984';
const DATA_L1_URL = 'http://localhost:9400/data';

// Read template file
const templatePath = process.argv[2] || './test-template.json';
const templateData = JSON.parse(fs.readFileSync(templatePath, 'utf8'));

console.log('📄 Template data loaded:', JSON.stringify(templateData, null, 2));

// Create account from private key using dag4
dag4.di.useFetchHttpClient();
const account = dag4.account.createFromPrivateKey(PRIVATE_KEY);

console.log('\n🔑 Account info:');
console.log('  Address:', account.address);
console.log('  Public Key:', account.publicKey);

// Sign the message
const messageToSign = JSON.stringify(templateData);
const signedData = account.signData(messageToSign);

console.log('\n✍️  Signature info:');
console.log('  ', JSON.stringify(signedData, null, 2).substring(0, 200) + '...');

// Create signed envelope in the format expected by InsuranceDecoders
const signedEnvelope = {
  value: templateData,
  proofs: [{
    id: account.publicKey,
    signature: signedData.signature
  }]
};

console.log('\n📦 Signed envelope created');
console.log('  Proof ID:', account.publicKey);
console.log('  Signature length:', signedData.signature.length);

// Upload to Data L1
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

console.log('\n🚀 Uploading to', DATA_L1_URL, '...');

const req = http.request(options, (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    console.log('\n📥 Response:');
    console.log('  Status:', res.statusCode);
    console.log('  Body:', data || '(empty)');
    
    if (res.statusCode === 200 || res.statusCode === 201) {
      console.log('\n✅ Upload successful!');
      console.log('\n🔍 Check providers at: http://localhost:9200/data-application/providers');
    } else {
      console.log('\n❌ Upload failed');
    }
  });
});

req.on('error', (e) => {
  console.error('\n❌ Error:', e.message);
  process.exit(1);
});

req.write(postData);
req.end();
