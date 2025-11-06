package com.my.nft.shared_data.serializers

import com.my.nft.shared_data.types.Types._
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import io.constellationnetwork.currency.dataApplication.DataUpdate
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationBlock
import io.constellationnetwork.security.signature.Signed

import java.nio.charset.StandardCharsets

object Serializers {
  private def serialize[A: Encoder](
    serializableData: A
  ): Array[Byte] = {
    serializableData.asJson.deepDropNullValues.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  def serializeState(
    state: InsuranceOnChainState
  ): Array[Byte] =
    serialize[InsuranceOnChainState](state)

  def serializeUpdate(
    update: InsuranceUpdate
  ): Array[Byte] =
    serialize[InsuranceUpdate](update)

  def serializeBlock(
    state: Signed[DataApplicationBlock]
  )(implicit e: Encoder[DataUpdate]): Array[Byte] =
    serialize[Signed[DataApplicationBlock]](state)

  def serializeCalculatedState(
    state: InsuranceCalculatedState
  ): Array[Byte] =
    serialize[InsuranceCalculatedState](state)
}