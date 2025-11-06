package com.my.nft.shared_data.combiners

import com.my.nft.shared_data.types.Types._
import com.my.nft.shared_data.Utils
import io.constellationnetwork.currency.dataApplication.DataState

object Combiners {
  // Insurance Combiner Methods
  def combineUploadContractTemplate(
    update: UploadContractTemplate,
    state: DataState[InsuranceOnChainState, InsuranceCalculatedState],
    currentOrdinal: Long
  ): DataState[InsuranceOnChainState, InsuranceCalculatedState] = {
    val template = InsuranceTemplate(
      providerName = update.providerName,
      templateVersion = update.templateVersion,
      uploadedAtOrdinal = currentOrdinal,
      terms = update.terms,
      riskRules = update.riskRules
    )

    val templateHash = Utils.hashTemplate(template)

    val newOnChain = state.onChain.copy(
      contractTemplateHashes = state.onChain.contractTemplateHashes + (update.providerName -> templateHash)
    )

    val newCalculated = state.calculated.copy(
      contractTemplates = state.calculated.contractTemplates + (update.providerName -> template)
    )

    DataState(newOnChain, newCalculated)
  }

  def combineValidateCircumstance(
    update: ValidateCircumstance,
    state: DataState[InsuranceOnChainState, InsuranceCalculatedState],
    currentOrdinal: Long
  ): DataState[InsuranceOnChainState, InsuranceCalculatedState] = {
    val template = state.calculated.contractTemplates(update.providerName)

    println(s"Processing validation for ${update.providerName}")

    val (riskScore, matchedTerms, justification) = assessRisk(update.circumstanceChange, template)

    println(s"Risk assessment complete: ${riskScore} (matched: ${matchedTerms.mkString(", ")})")

    val validationId = Utils.generateValidationId(update, currentOrdinal)

    val summary = ValidationSummary(
      id = validationId,
      providerName = update.providerName,
      riskScore = riskScore,
      circumstanceHash = Utils.hashString(update.circumstanceChange),
      validatedAtOrdinal = currentOrdinal,
      notificationSent = false
    )

    val detailed = DetailedValidation(
      id = validationId,
      providerName = update.providerName,
      circumstanceChange = update.circumstanceChange,
      matchedTerms = matchedTerms,
      riskScore = riskScore,
      riskJustification = justification,
      userInfo = None,
      timestamp = System.currentTimeMillis(),
      validatedAtOrdinal = currentOrdinal,
      userRequestId = update.userRequestId
    )

    val currentStats = state.onChain.providerStats.getOrElse(update.providerName, ProviderStats(0, 0, 0))
    val newStats = currentStats.copy(
      totalValidations = currentStats.totalValidations + 1,
      highRiskCount = if (riskScore == RiskLevel.High) currentStats.highRiskCount + 1 else currentStats.highRiskCount
    )

    val newOnChain = state.onChain.copy(
      validationRecords = state.onChain.validationRecords + (validationId -> summary),
      providerStats = state.onChain.providerStats + (update.providerName -> newStats)
    )

    val newCalculated = state.calculated.copy(
      validationHistory = state.calculated.validationHistory + (validationId -> detailed)
    )

    DataState(newOnChain, newCalculated)
  }

  def combineRequestNotification(
    update: RequestNotification,
    state: DataState[InsuranceOnChainState, InsuranceCalculatedState],
    currentOrdinal: Long
  ): DataState[InsuranceOnChainState, InsuranceCalculatedState] = {
    val validation = state.calculated.validationHistory(update.validationId)

    val notificationReq = NotificationRequest(
      validationId = update.validationId,
      userInfo = update.userInfo,
      providerEmail = Utils.getProviderEmail(validation.providerName),
      circumstanceDetails = validation.circumstanceChange,
      queuedAtOrdinal = currentOrdinal
    )

    val summary = state.onChain.validationRecords(update.validationId)
    val updatedSummary = summary.copy(notificationSent = true)

    val stats = state.onChain.providerStats(validation.providerName)
    val updatedStats = stats.copy(
      notificationsSent = stats.notificationsSent + 1
    )

    val newOnChain = state.onChain.copy(
      validationRecords = state.onChain.validationRecords + (update.validationId -> updatedSummary),
      providerStats = state.onChain.providerStats + (validation.providerName -> updatedStats)
    )

    val newCalculated = state.calculated.copy(
      pendingNotifications = state.calculated.pendingNotifications :+ notificationReq
    )

    DataState(newOnChain, newCalculated)
  }

  private def assessRisk(
    circumstance: String,
    template: InsuranceTemplate
  ): (RiskLevel, List[String], String) = {
    val circumstanceLower = circumstance.toLowerCase
    var totalScore = 0
    var matchedTerms = List.empty[String]
    var explanations = List.empty[String]

    template.riskRules.foreach { rule =>
      val hasMatch = rule.keywords.exists(keyword => circumstanceLower.contains(keyword.toLowerCase))
      if (hasMatch) {
        totalScore += rule.riskWeight
        matchedTerms = matchedTerms :+ rule.category
        explanations = explanations :+ rule.explanation
      }
    }

    val maxPossibleScore = template.riskRules.map(_.riskWeight).sum
    val normalizedScore = if (maxPossibleScore > 0) ((totalScore.toDouble / maxPossibleScore) * 100).toInt else 0

    val riskLevel = RiskLevel.fromScore(normalizedScore)
    val justification = if (explanations.isEmpty) {
      "No specific risk factors detected in your circumstance description."
    } else {
      s"Risk factors detected: ${explanations.mkString("; ")}"
    }

    (riskLevel, matchedTerms.distinct, justification)
  }
}
