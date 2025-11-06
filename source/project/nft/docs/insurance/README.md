# Insurance Contract Validation Metagraph

This module implements an Insurance Contract Change-of-Circumstance Validator. It stores provider contract templates, validates user-submitted circumstance changes against those templates, scores the risk (Low/Medium/High), and queues notifications to insurers for high-risk cases.

## Overview
- OnChainState (public):
  - contractTemplateHashes: Map[provider -> hash]
  - validationRecords: Map[validationId -> ValidationSummary]
  - providerStats: per-provider counters
- CalculatedState (private):
  - contractTemplates: full provider templates
  - validationHistory: detailed validation results
  - pendingNotifications: queue for email daemon
  - notificationHistory: historical email send records

## Update Types
- UploadContractTemplate (admin)
- ValidateCircumstance (user)
- RequestNotification (user, only for High risk)

Updates are processed in order: Upload > Validate > RequestNotification.

## Implementation Map
- Types & States: source/project/nft/modules/shared_data/src/main/scala/com/my/nft/shared_data/types/Types.scala
- Utils (hashing, ordering): source/project/nft/modules/shared_data/src/main/scala/com/my/nft/shared_data/Utils.scala
- Validators: source/project/nft/modules/shared_data/src/main/scala/com/my/nft/shared_data/validations/Validations.scala
- State Combiners: source/project/nft/modules/shared_data/src/main/scala/com/my/nft/shared_data/combiners/Combiners.scala
- HTTP Routes (L0): source/project/nft/modules/l0/src/main/scala/com/my/nft/l0/custom_routes/CustomRoutes.scala
- Email Daemon: source/project/nft/modules/l0/src/main/scala/com/my/nft/l0/daemon/NotificationDaemon.scala
- Seed Data: source/project/nft/scripts/seed-insurance-templates.scala

## Risk Assessment
- Deterministic keyword-matching per provider RiskRule.
- Weighted sum normalized to 0-100, mapped to RiskLevel via thresholds.
- Returns matched categories and a human-readable justification.

## API Endpoints
See API.md for full details and examples.

- GET /insurance/providers
- GET /insurance/providers/:name
- GET /insurance/validations/:id
- GET /insurance/providers/:name/validations?limit&offset
- (Provider stats endpoint is reserved)

## Email Notification Daemon
- Non-deterministic side effect handled by background daemon.
- Reads pendingNotifications from CalculatedState and attempts SMTP delivery.
- On success, expected to move item to notificationHistory (see TODO note below).

Configuration: NotificationDaemon.defaultConfig (override with your SMTP settings).

## Seeding Templates
- File: source/project/nft/scripts/seed-insurance-templates.scala
- Contains example templates for Auto, Home, Health, and Life insurance plus test scenarios.

## TODOs / Notes
- Wire route access to OnChain providerStats if/when an OnChain state accessor is exposed to routes.
- Persist daemon side-effects back into CalculatedState (remove from pending, add to history) using your platform’s state-update mechanism.
- Add POST /data ingestion route if not already present in your Data L1 service.

## Testing
- Unit tests should cover assessRisk and validations.
- Integration test: upload template -> validate -> request notification -> verify queue.
