package com.my.nft.data_l1

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.option.catsSyntaxOptionId
import com.my.nft.shared_data.LifecycleSharedFunctions
import com.my.nft.data_l1.decoders.InsuranceDecoders
import com.my.nft.shared_data.calculated_state.CalculatedStateService
import com.my.nft.shared_data.deserializers.Deserializers
import com.my.nft.shared_data.errors.Errors.valid
import com.my.nft.shared_data.serializers.Serializers
import com.my.nft.shared_data.types.Types._
import io.circe.{Decoder, Encoder}
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import io.constellationnetwork.BuildInfo
import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.dataApplication.dataApplication._
import io.constellationnetwork.currency.l1.CurrencyL1App
import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.schema.cluster.ClusterId
import io.constellationnetwork.schema.semver.{MetagraphVersion, TessellationVersion}
import io.constellationnetwork.security.hash.Hash
import io.constellationnetwork.security.signature.Signed

import java.util.UUID

object Main
  extends CurrencyL1App(
    "currency-data_l1",
    "currency data L1 node",
    ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
    metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
    tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
  ) {
  private def makeBaseDataApplicationL1Service(
    calculatedStateService: CalculatedStateService[IO]
  ): BaseDataApplicationL1Service[IO] = BaseDataApplicationL1Service(new DataApplicationL1Service[IO, InsuranceUpdate, InsuranceOnChainState, InsuranceCalculatedState] {

    override def validateUpdate(
      update: InsuranceUpdate
    )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
      LifecycleSharedFunctions.validateUpdate[IO](update)

    override def serializeState(
      state: InsuranceOnChainState
    ): IO[Array[Byte]] =
      IO(Serializers.serializeState(state))

    override def serializeUpdate(update: InsuranceUpdate): IO[Array[Byte]] =
      IO(Serializers.serializeUpdate(update))

    override def serializeBlock(
      block: Signed[DataApplicationBlock]
    ): IO[Array[Byte]] =
      IO(Serializers.serializeBlock(block)(dataEncoder.asInstanceOf[Encoder[DataUpdate]]))

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

    override def routes(implicit context: L1NodeContext[IO]): HttpRoutes[IO] = {
      import org.http4s.dsl.io._
      import org.http4s.circe.CirceEntityCodec._
      import io.circe.syntax._
      import io.circe.generic.auto._
      import org.http4s.server.middleware.CORS
      
      val insuranceRoutes = HttpRoutes.of[IO] {
        case GET -> Root / "insurance" / "providers" =>
          calculatedStateService.getCalculatedState.flatMap { cs =>
            Ok(cs.state.contractTemplates.keys.toList.asJson)
          }
        
        case GET -> Root / "insurance" / "provider" / providerName =>
          calculatedStateService.getCalculatedState.flatMap { cs =>
            cs.state.contractTemplates.get(providerName) match {
              case Some(template) => Ok(template.asJson)
              case None => NotFound(s"Provider not found")
            }
          }
        
        case GET -> Root / "insurance" / "templates" =>
          calculatedStateService.getCalculatedState.flatMap { cs =>
            Ok(cs.state.asJson)
          }
      }
      
      CORS.policy.withAllowOriginAll.withAllowMethodsAll.withAllowHeadersAll.httpRoutes(insuranceRoutes)
    }

    override def signedDataEntityDecoder: EntityDecoder[IO, Signed[InsuranceUpdate]] =
      circeEntityDecoder

    override def serializeCalculatedState(
      state: InsuranceCalculatedState
    ): IO[Array[Byte]] =
      IO(Serializers.serializeCalculatedState(state))

    override def deserializeCalculatedState(
      bytes: Array[Byte]
    ): IO[Either[Throwable, InsuranceCalculatedState]] =
      IO(Deserializers.deserializeCalculatedState(bytes))
  })

  private def makeL1Service: IO[BaseDataApplicationL1Service[IO]] =
    CalculatedStateService.make[IO].map(makeBaseDataApplicationL1Service)

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL1Service[IO]]] =
    makeL1Service.asResource.some
}
