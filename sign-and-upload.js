#!/usr/bin/env node

const crypto = require('crypto');
const https = require('https');
const http = require('http');
const fs = require('fs');

// Configuration
const PRIVATE_KEY = '70224168fb7627aa65c3a2855edd1f7c2179225bae6eb4c76597b6592fcff984';
const DATA_L1_URL = 'http://localhost:9400/data';

// Read the template file
const templatePath = process.argv[2] || './test-template.json';
const templateData = JSON.parse(fs.readFileSync(templatePath, 'utf8'));

console.log('Template data:', JSON.stringify(templateData, null, 2));

// Simple ECDSA signing using secp256k1
function signMessage(message, privateKeyHex) {
  const msgHash = crypto.createHash('sha256').update(JSON.stringify(message)).digest();
  
  const sign = crypto.createSign('SHA256');
  sign.update(JSON.stringify(message));
  sign.end();
  
  // Create EC key from private key hex
  const keyObject = crypto.createPrivateKey({
    key: Buffer.from('308184020100301006072a8648ce3d020106052b8104000a046d306b0201010420' + privateKeyHex + 'a144034200', 'hex'),
    format: 'der',
    type: 'sec1'
  });
  
  const signature = crypto.sign(null, msgHash, {
    key: keyObject,
    dsaEncoding: 'der'
  });
  
  return signature.toString('hex');
}

// Get public key ID from private key
function getPublicKeyId(privateKeyHex) {
  const keyObject = crypto.createPrivateKey({
    key: Buffer.from('308184020100301006072a8648ce3d020106052b8104000a046d306b0201010420' + privateKeyHex + 'a144034200', 'hex'),
    format: 'der',
    type: 'sec1'
  });
  
  const publicKey = crypto.createPublicKey(keyObject);
  const publicKeyDer = publicKey.export({ type: 'spki', format: 'der' });
  const publicKeyHash = crypto.createHash('sha256').update(publicKeyDer).digest('hex');
  
  return publicKeyHash;
}

try {
  const publicKeyId = getPublicKeyId(PRIVATE_KEY);
  const signature = signMessage(templateData, PRIVATE_KEY);
  
  const signedEnvelope = {
    value: templateData,
    proofs: [{
      id: publicKeyId,
      signature: signature
    }]
  };
  
  console.log('\nSigned envelope:', JSON.stringify(signedEnvelope, null, 2));
  console.log('\nUploading to', DATA_L1_URL, '...');
  
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
      console.log('\nResponse status:', res.statusCode);
      console.log('Response:', data);
    });
  });
  
  req.on('error', (e) => {
    console.error('Error:', e.message);
  });
  
  req.write(postData);
  req.end();
  
} catch (error) {
  console.error('Error:', error.message);
  process.exit(1);
}
