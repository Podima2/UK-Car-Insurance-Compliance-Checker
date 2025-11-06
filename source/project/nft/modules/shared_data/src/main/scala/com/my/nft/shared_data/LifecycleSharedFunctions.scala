package com.my.nft.shared_data

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import com.my.nft.shared_data.Utils._
import com.my.nft.shared_data.combiners.Combiners._
import com.my.nft.shared_data.types.Types._
import com.my.nft.shared_data.validations.Validations._
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import io.constellationnetwork.currency.dataApplication.{DataState, L0NodeContext}
import io.constellationnetwork.security.SecurityProvider
import io.constellationnetwork.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LifecycleSharedFunctions {
  private def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("ClusterApi")

  def validateUpdate[F[_] : Async](
    update: InsuranceUpdate
  ): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    update match {
      case _: TestUpdate               => com.my.nft.shared_data.errors.Errors.valid
      case u: UploadContractTemplate   => uploadContractTemplateValidations(u, None)
      case v: ValidateCircumstance     => validateCircumstanceValidations(v, None)
      case r: RequestNotification      => requestNotificationValidations(r, None)
    }
  }

  def validateData[F[_] : Async](
    state  : DataState[InsuranceOnChainState, InsuranceCalculatedState],
    updates: NonEmptyList[Signed[InsuranceUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    implicit val sp: SecurityProvider[F] = context.securityProvider
    updates.traverse { signedUpdate =>
      getAllAddressesFromProofs(signedUpdate.proofs)
        .flatMap { addresses =>
          Async[F].delay {
            signedUpdate.value match {
              case _: TestUpdate =>
                com.my.nft.shared_data.errors.Errors.valid
              case u: UploadContractTemplate =>
                uploadContractTemplateValidations(u, state.some)
              case v: ValidateCircumstance =>
                validateCircumstanceValidations(v, state.some)
              case r: RequestNotification =>
                requestNotificationValidations(r, state.some)
            }
          }
        }
    }.map(_.reduce)
  }

  def combine[F[_] : Async](
    state  : DataState[InsuranceOnChainState, InsuranceCalculatedState],
    updates: List[Signed[InsuranceUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[InsuranceOnChainState, InsuranceCalculatedState]] = {
    val newStateF = DataState(
      InsuranceOnChainState(
        contractTemplateHashes = state.onChain.contractTemplateHashes,
        validationRecords = state.onChain.validationRecords,
        providerStats = state.onChain.providerStats
      ),
      state.calculated
    ).pure[F]

    if (updates.isEmpty) {
      logger.info("Snapshot without any updates, updating the state to empty updates") >> newStateF
    } else {
      newStateF.flatMap(newState => {
        updates.foldLeftM(newState) { (acc, signedUpdate) => {
          signedUpdate.value match {
            case _: TestUpdate =>
              Async[F].pure(acc)
            case u: UploadContractTemplate =>
              Async[F].delay(combineUploadContractTemplate(u, acc, 0L))
            case v: ValidateCircumstance =>
              Async[F].delay(combineValidateCircumstance(v, acc, 0L))
            case r: RequestNotification =>
              Async[F].delay(combineRequestNotification(r, acc, 0L))
          }
        }
        }
      })
    }
  }
}