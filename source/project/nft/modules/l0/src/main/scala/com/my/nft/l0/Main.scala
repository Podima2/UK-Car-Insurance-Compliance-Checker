package com.my.nft.l0

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.option.catsSyntaxOptionId
import com.my.nft.l0.custom_routes.CustomRoutes
import com.my.nft.shared_data.LifecycleSharedFunctions
import com.my.nft.shared_data.calculated_state.CalculatedStateService
import com.my.nft.shared_data.deserializers.Deserializers
import com.my.nft.shared_data.errors.Errors.valid
import com.my.nft.shared_data.serializers.Serializers
import com.my.nft.shared_data.types.Types._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.http4s.server.middleware.CORS
import io.constellationnetwork.BuildInfo
import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.dataApplication.dataApplication._
import io.constellationnetwork.currency.l0.CurrencyL0App
import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.schema.cluster.ClusterId
import io.constellationnetwork.schema.semver.{MetagraphVersion, TessellationVersion}
import io.constellationnetwork.security.hash.Hash
import io.constellationnetwork.security.signature.Signed

import java.util.UUID

object Main
  extends CurrencyL0App(
    "currency-l0",
    "currency L0 node",
    ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
    metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
    tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
  ) {
  private def makeBaseDataApplicationL0Service(
    calculatedStateService: CalculatedStateService[IO]
  ): BaseDataApplicationL0Service[IO] = BaseDataApplicationL0Service(new DataApplicationL0Service[IO, InsuranceUpdate, InsuranceOnChainState, InsuranceCalculatedState] {
    override def genesis: DataState[InsuranceOnChainState, InsuranceCalculatedState] =
      DataState(
        InsuranceOnChainState(Map.empty, Map.empty, Map.empty),
        InsuranceCalculatedState(Map.empty, Map.empty, List.empty, Map.empty)
      )

    override def validateData(
      state  : DataState[InsuranceOnChainState, InsuranceCalculatedState],
      updates: NonEmptyList[Signed[InsuranceUpdate]]
    )(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
      LifecycleSharedFunctions.validateData[IO](state, updates)

    override def combine(
      state  : DataState[InsuranceOnChainState, InsuranceCalculatedState],
      updates: List[Signed[InsuranceUpdate]]
    )(implicit context: L0NodeContext[IO]): IO[DataState[InsuranceOnChainState, InsuranceCalculatedState]] =
      LifecycleSharedFunctions.combine[IO](state, updates)

    override def serializeState(
      state: InsuranceOnChainState
    ): IO[Array[Byte]] =
      IO(Serializers.serializeState(state))

    override def serializeUpdate(
      update: InsuranceUpdate
    ): IO[Array[Byte]] = {
      import io.circe.syntax._
      import io.circe.Json
      import java.nio.charset.StandardCharsets
      import java.util.Base64
      
      // Reconstruct SignatureRequest that was signed
      val signatureRequest = Json.obj(
        "content" -> Json.fromString(update.asJson.noSpaces),
        "metadata" -> Json.obj(
          "type" -> Json.fromString("InsuranceUpdate")
        )
      )
      
      // Convert to JSON string
      val jsonString = signatureRequest.noSpaces
      
      // Base64 encode (matching frontend)
      val base64Encoded = Base64.getEncoder.encodeToString(
        jsonString.getBytes(StandardCharsets.UTF_8)
      )
      
      // Return as bytes for signature validation
      IO(base64Encoded.getBytes(StandardCharsets.UTF_8))
    }

    override def serializeBlock(
      block: Signed[DataApplicationBlock]
    ): IO[Array[Byte]] = IO(Serializers.serializeBlock(block)(dataEncoder.asInstanceOf[Encoder[DataUpdate]]))

    override def deserializeState(
      bytes: Array[Byte]
    ): IO[Either[Throwable, InsuranceOnChainState]] =
      IO(Deserializers.deserializeState(bytes))

    override def deserializeUpdate(
      bytes: Array[Byte]
    ): IO[Either[Throwable, InsuranceUpdate]] =
      IO(Deserializers.deserializeUpdate(bytes))

    override def deserializeBlock(
      bytes: Array[Byte]
    ): IO[Either[Throwable, Signed[DataApplicationBlock]]] =
      IO(Deserializers.deserializeBlock(bytes)(dataDecoder.asInstanceOf[Decoder[DataUpdate]]))

    override def dataEncoder: Encoder[InsuranceUpdate] =
      implicitly[Encoder[InsuranceUpdate]]

    override def dataDecoder: Decoder[InsuranceUpdate] =
      implicitly[Decoder[InsuranceUpdate]]

    override def calculatedStateEncoder: Encoder[InsuranceCalculatedState] =
      implicitly[Encoder[InsuranceCalculatedState]]

    override def calculatedStateDecoder: Decoder[InsuranceCalculatedState] =
      implicitly[Decoder[InsuranceCalculatedState]]

    override def routes(implicit context: L0NodeContext[IO]): HttpRoutes[IO] = {
      val base = CustomRoutes[IO](calculatedStateService).public
      CORS.policy
        .withAllowOriginAll
        .withAllowMethodsAll
        .withAllowHeadersAll
        .withAllowCredentials(false)
        .httpRoutes(base)
    }

    override def signedDataEntityDecoder: EntityDecoder[IO, Signed[InsuranceUpdate]] =
      circeEntityDecoder

    override def getCalculatedState(implicit context: L0NodeContext[IO]): IO[(SnapshotOrdinal, InsuranceCalculatedState)] =
      calculatedStateService.getCalculatedState.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

    override def setCalculatedState(
      ordinal: SnapshotOrdinal,
      state  : InsuranceCalculatedState
    )(implicit context: L0NodeContext[IO]): IO[Boolean] =
      calculatedStateService.setCalculatedState(ordinal, state)

    override def hashCalculatedState(
      state: InsuranceCalculatedState
    )(implicit context: L0NodeContext[IO]): IO[Hash] =
      calculatedStateService.hashCalculatedState(state)

    override def serializeCalculatedState(
      state: InsuranceCalculatedState
    ): IO[Array[Byte]] =
      IO(Serializers.serializeCalculatedState(state))

    override def deserializeCalculatedState(
      bytes: Array[Byte]
    ): IO[Either[Throwable, InsuranceCalculatedState]] =
      IO(Deserializers.deserializeCalculatedState(bytes))
  })

  private def makeL0Service: IO[BaseDataApplicationL0Service[IO]] =
    CalculatedStateService.make[IO].map(makeBaseDataApplicationL0Service)

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL0Service[IO]]] =
    makeL0Service.asResource.some
}
