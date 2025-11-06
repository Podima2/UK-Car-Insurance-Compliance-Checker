package com.my.nft.shared_data.validations

import cats.syntax.all._
import com.my.nft.shared_data.errors.Errors._
import io.constellationnetwork.currency.dataApplication.DataApplicationValidationError
import com.my.nft.shared_data.types.Types._
import com.my.nft.shared_data.Utils
import io.constellationnetwork.currency.dataApplication.DataState
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object Validations {
  // Insurance Validation Methods
  def uploadContractTemplateValidations(
    update: UploadContractTemplate,
    maybeState: Option[DataState[InsuranceOnChainState, InsuranceCalculatedState]]
  ): DataApplicationValidationErrorOr[Unit] = {
    if (update.providerName.isEmpty)
      return GenericValidationError("Provider name cannot be empty").invalid

    if (update.terms.isEmpty)
      return GenericValidationError("Contract must have at least one term").invalid

    if (update.riskRules.isEmpty)
      return GenericValidationError("Contract must have at least one risk rule").invalid

    val totalWeight = update.riskRules.map(_.riskWeight).sum
    if (totalWeight == 0)
      return GenericValidationError("Risk rules must have non-zero weights").invalid

    maybeState match {
      case Some(state) =>
        state.calculated.contractTemplates.get(update.providerName) match {
          case Some(existing) if existing.templateVersion == update.templateVersion =>
            GenericValidationError(s"Template version ${update.templateVersion} already exists").invalid
          case _ => valid
        }
      case None => valid
    }
  }

  def validateCircumstanceValidations(
    update: ValidateCircumstance,
    maybeState: Option[DataState[InsuranceOnChainState, InsuranceCalculatedState]]
  ): DataApplicationValidationErrorOr[Unit] = {
    if (update.circumstanceChange.trim.isEmpty)
      return GenericValidationError("Circumstance change cannot be empty").invalid

    if (update.circumstanceChange.length < 10)
      return GenericValidationError("Please provide more details (at least 10 characters)").invalid

    if (update.circumstanceChange.length > 5000)
      return GenericValidationError("Circumstance description too long (max 5000 chars)").invalid

    maybeState match {
      case Some(state) =>
        if (!state.onChain.contractTemplateHashes.contains(update.providerName)) {
          val available = state.onChain.contractTemplateHashes.keys.mkString(", ")
          return GenericValidationError(s"Provider '${update.providerName}' not found. Available: ${available}").invalid
        }

        if (!state.calculated.contractTemplates.contains(update.providerName))
          return GenericValidationError("Provider template not loaded in state").invalid

        val isDuplicate = state.calculated.validationHistory.values.exists { v =>
          v.userRequestId == update.userRequestId && v.providerName == update.providerName
        }
        if (isDuplicate)
          return GenericValidationError("Duplicate validation request").invalid

        valid
      case None => valid
    }
  }

  def requestNotificationValidations(
    update: RequestNotification,
    maybeState: Option[DataState[InsuranceOnChainState, InsuranceCalculatedState]]
  ): DataApplicationValidationErrorOr[Unit] = {
    if (!Utils.isValidEmail(update.userInfo.email))
      return GenericValidationError("Invalid email format").invalid

    if (update.userInfo.policyNumber.isEmpty)
      return GenericValidationError("Policy number required").invalid

    maybeState match {
      case Some(state) =>
        if (!state.onChain.validationRecords.contains(update.validationId))
          return GenericValidationError(s"Validation ${update.validationId} not found").invalid

        val validation = state.onChain.validationRecords(update.validationId)

        if (validation.riskScore != RiskLevel.High)
          return GenericValidationError("Notifications only available for high-risk validations").invalid

        if (validation.notificationSent)
          return GenericValidationError("Notification already sent for this validation").invalid

        val alreadyQueued = state.calculated.pendingNotifications.exists(_.validationId == update.validationId)
        if (alreadyQueued)
          return GenericValidationError("Notification already queued").invalid

        valid
      case None => valid
    }
  }
}

