package com.my.nft.shared_data.calculated_state

import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.functor.toFunctorOps
import com.my.nft.shared_data.types.Types._
import io.circe.Json
import io.circe.syntax.EncoderOps
import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.security.hash.Hash

import java.nio.charset.StandardCharsets

trait CalculatedStateService[F[_]] {
  def getCalculatedState: F[CalculatedState]

  def setCalculatedState(
    snapshotOrdinal: SnapshotOrdinal,
    state          : InsuranceCalculatedState
  ): F[Boolean]

  def hashCalculatedState(
    state: InsuranceCalculatedState
  ): F[Hash]
}

object CalculatedStateService {
  def make[F[_] : Async]: F[CalculatedStateService[F]] = {
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      new CalculatedStateService[F] {
        override def getCalculatedState: F[CalculatedState] = stateRef.get

        override def setCalculatedState(
          snapshotOrdinal: SnapshotOrdinal,
          state          : InsuranceCalculatedState
        ): F[Boolean] =
          stateRef.update { _ =>
            CalculatedState(snapshotOrdinal, state)
          }.as(true)

        override def hashCalculatedState(
          state: InsuranceCalculatedState
        ): F[Hash] = Async[F].delay {
          def removeKey(json: Json, keyToRemove: String): Json =
            json.mapObject { obj =>
              obj.filterKeys(_ != keyToRemove).mapValues {
                case objValue: Json => removeKey(objValue, keyToRemove)
                case other => other
              }
            }.mapArray { arr =>
              arr.map(removeKey(_, keyToRemove))
            }


          val stateAsString = removeKey(state.asJson, "creationDateTimestamp")
            .deepDropNullValues
            .noSpaces

          Hash.fromBytes(stateAsString.getBytes(StandardCharsets.UTF_8))
        }
      }
    }
  }
}
