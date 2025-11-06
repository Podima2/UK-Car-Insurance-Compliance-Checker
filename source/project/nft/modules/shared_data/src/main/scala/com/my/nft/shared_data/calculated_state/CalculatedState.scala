package com.my.nft.shared_data.calculated_state

import com.my.nft.shared_data.types.Types._
import eu.timepit.refined.types.all.NonNegLong
import io.constellationnetwork.schema.SnapshotOrdinal

case class CalculatedState(ordinal: SnapshotOrdinal, state: InsuranceCalculatedState)

object CalculatedState {
  def empty: CalculatedState =
    CalculatedState(
      SnapshotOrdinal(NonNegLong(0L)),
      InsuranceCalculatedState(
        contractTemplates = Map.empty,
        validationHistory = Map.empty,
        pendingNotifications = List.empty,
        notificationHistory = Map.empty
      )
    )
}
