package com.my.nft.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.constellationnetwork.currency.dataApplication.{DataCalculatedState, DataOnChainState, DataUpdate}

object Types {
  // Risk Level ADT
  @derive(decoder, encoder)
  sealed trait RiskLevel
  
  object RiskLevel {
    @derive(decoder, encoder)
    case object Low extends RiskLevel
    @derive(decoder, encoder)
    case object Medium extends RiskLevel
    @derive(decoder, encoder)
    case object High extends RiskLevel
    
    def fromScore(score: Int): RiskLevel = score match {
      case s if s < 30 => Low
      case s if s < 70 => Medium
      case _ => High
    }
  }
  
  // Contract Term Definition
  @derive(decoder, encoder)
  case class ContractTerm(
    category: String,
    description: String,
    voidingConditions: List[String],
    notificationRequired: Boolean
  )
  
  // Risk Assessment Rule
  @derive(decoder, encoder)
  case class RiskRule(
    keywords: List[String],
    category: String,
    riskWeight: Int,
    requiresNotification: Boolean,
    explanation: String
  )
  
  // Insurance Template (stored in CalculatedState)
  @derive(decoder, encoder)
  case class InsuranceTemplate(
    providerName: String,
    templateVersion: String,
    uploadedAtOrdinal: Long,
    terms: List[ContractTerm],
    riskRules: List[RiskRule]
  )
  
  // User Contact Information
  @derive(decoder, encoder)
  case class UserContactInfo(
    policyNumber: String,
    email: String,
    fullName: String
  )
  
  // Validation Summary (stored in OnChainState)
  @derive(decoder, encoder)
  case class ValidationSummary(
    id: String,
    providerName: String,
    riskScore: RiskLevel,
    circumstanceHash: String,
    validatedAtOrdinal: Long,
    notificationSent: Boolean
  )
  
  // Provider Statistics (stored in OnChainState)
  @derive(decoder, encoder)
  case class ProviderStats(
    totalValidations: Long,
    highRiskCount: Long,
    notificationsSent: Long
  )
  
  // Detailed Validation (stored in CalculatedState)
  @derive(decoder, encoder)
  case class DetailedValidation(
    id: String,
    providerName: String,
    circumstanceChange: String,
    matchedTerms: List[String],
    riskScore: RiskLevel,
    riskJustification: String,
    userInfo: Option[UserContactInfo],
    timestamp: Long,
    validatedAtOrdinal: Long,
    userRequestId: String
  )
  
  // Notification Request (stored in CalculatedState)
  @derive(decoder, encoder)
  case class NotificationRequest(
    validationId: String,
    userInfo: UserContactInfo,
    providerEmail: String,
    circumstanceDetails: String,
    queuedAtOrdinal: Long
  )
  
  // Notification Record (stored in CalculatedState)
  @derive(decoder, encoder)
  case class NotificationRecord(
    validationId: String,
    sentAt: Long,
    recipientEmail: String,
    status: String
  )
  
  // Insurance Updates
  @derive(decoder, encoder)
  sealed trait InsuranceUpdate extends DataUpdate
  
  // Minimal test update to validate ingestion pipeline quickly
  @derive(decoder, encoder)
  case class TestUpdate(
    message: String
  ) extends InsuranceUpdate
  
  @derive(decoder, encoder)
  case class UploadContractTemplate(
    providerName: String,
    templateVersion: String,
    terms: List[ContractTerm],
    riskRules: List[RiskRule],
    uploadedBy: String,
    rewardAddress: String
  ) extends InsuranceUpdate
  
  @derive(decoder, encoder)
  case class ValidateCircumstance(
    providerName: String,
    circumstanceChange: String,
    userRequestId: String
  ) extends InsuranceUpdate
  
  @derive(decoder, encoder)
  case class RequestNotification(
    validationId: String,
    userInfo: UserContactInfo
  ) extends InsuranceUpdate
  
  // OnChain State (public - pushed to Hypergraph)
  @derive(decoder, encoder)
  case class InsuranceOnChainState(
    contractTemplateHashes: Map[String, String],
    validationRecords: Map[String, ValidationSummary],
    providerStats: Map[String, ProviderStats]
  ) extends DataOnChainState
  
  // Calculated State (private - ML0 only)
  @derive(decoder, encoder)
  case class InsuranceCalculatedState(
    contractTemplates: Map[String, InsuranceTemplate],
    validationHistory: Map[String, DetailedValidation],
    pendingNotifications: List[NotificationRequest],
    notificationHistory: Map[String, NotificationRecord]
  ) extends DataCalculatedState
  
  // API Response DTOs
  @derive(decoder, encoder)
  case class ProviderDetails(
    name: String,
    version: String,
    categories: List[String],
    totalRules: Int
  )
  
  @derive(decoder, encoder)
  case class ValidationResponse(
    id: String,
    provider: String,
    riskLevel: String,
    justification: String,
    matchedCategories: List[String],
    canNotify: Boolean
  )
  
  @derive(decoder, encoder)
  case class ValidationSummaryResponse(
    id: String,
    riskLevel: String,
    timestamp: Long,
    notificationSent: Boolean
  )
  
  @derive(decoder, encoder)
  case class PaginatedResponse[T](
    results: List[T],
    total: Int,
    limit: Int,
    offset: Int
  )
}
