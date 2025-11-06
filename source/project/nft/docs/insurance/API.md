# Insurance Validator API

Base path: /insurance

All endpoints return JSON. Examples below are illustrative.

## List Providers
GET /insurance/providers

Response 200:
[
  "SafeDrive Insurance",
  "HomeGuard Insurance"
]

## Provider Details
GET /insurance/providers/:name

Response 200:
{
  "name": "SafeDrive Insurance",
  "version": "1.0",
  "categories": ["Vehicle Changes", "Driving Record", "Address"],
  "totalRules": 8
}

404 if provider not found.

## Validation Result by ID
GET /insurance/validations/:id

Response 200:
{
  "id": "SafeDrive Insurance-12345-abcd1234",
  "provider": "SafeDrive Insurance",
  "riskLevel": "High",
  "justification": "Risk factors detected: Serious driving violations may void your policy",
  "matchedCategories": ["Driving Record"],
  "canNotify": true
}

404 if not found.

## Provider Validation History (Paginated)
GET /insurance/providers/:name/validations?limit=20&offset=0

Response 200:
{
  "results": [
    {
      "id": "SafeDrive Insurance-12345-abcd1234",
      "riskLevel": "High",
      "timestamp": 1730660000000,
      "notificationSent": true
    }
  ],
  "total": 42,
  "limit": 20,
  "offset": 0
}

## Data Ingestion Examples (Frontend/Client)
POST /data (handled by your Data L1 service)

UploadContractTemplate payload:
{
  "UploadContractTemplate": {
    "providerName": "SafeDrive Insurance",
    "templateVersion": "1.0",
    "terms": [...],
    "riskRules": [...],
    "uploadedBy": "admin"
  }
}

ValidateCircumstance payload:
{
  "ValidateCircumstance": {
    "providerName": "SafeDrive Insurance",
    "circumstanceChange": "I got a DUI last week",
    "userRequestId": "client-uuid-1234"
  }
}

RequestNotification payload:
{
  "RequestNotification": {
    "validationId": "SafeDrive Insurance-12345-abcd1234",
    "userInfo": {
      "policyNumber": "POL-12345",
      "email": "user@example.com",
      "fullName": "Jane Doe"
    }
  }
}

## Curl Examples
List providers:
$ curl -s http://localhost:9000/insurance/providers | jq

Get provider details:
$ curl -s http://localhost:9000/insurance/providers/SafeDrive%20Insurance | jq

Get validation result:
$ curl -s http://localhost:9000/insurance/validations/SafeDrive%20Insurance-12345-abcd1234 | jq

Get provider validations (first page):
$ curl -s "http://localhost:9000/insurance/providers/SafeDrive%20Insurance/validations?limit=20&offset=0" | jq
