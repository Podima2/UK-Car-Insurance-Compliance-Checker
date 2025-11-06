package com.my.nft.data_l1.decoders

import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.all._
import com.my.nft.shared_data.types.Types._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser._
import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.security.hex.Hex
import io.constellationnetwork.security.signature.Signed
import io.constellationnetwork.security.signature.signature.{Signature, SignatureProof}
import org.http4s.{DecodeResult, EntityDecoder, MediaType}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.util.Base64
import java.nio.charset.StandardCharsets

object InsuranceDecoders {
  def logger[F[_]: Async] = Slf4jLogger.getLogger[F]
  
  case class SignatureRequest(content: String, metadata: Option[Json])
  case class SignedEnvelope(value: String, proofs: List[ProofData])
  case class ProofData(id: String, signature: String)

  def signedDataEntityDecoder[F[_]: Async]: EntityDecoder[F, Signed[InsuranceUpdate]] = {
    EntityDecoder.decodeBy(MediaType.application.json, MediaType.text.plain) { msg =>
      DecodeResult.success(msg.as[String].flatMap(parseSignedEnvelope[F]))
    }
  }

  private def parseSignedEnvelope[F[_]: Async](jsonString: String): F[Signed[InsuranceUpdate]] = {
    implicit val sigReqDecoder: Decoder[SignatureRequest] = (c: HCursor) => for {
      content <- c.downField("content").as[String]
      metadata <- c.downField("metadata").as[Option[Json]]
    } yield SignatureRequest(content, metadata)
    
    implicit val proofDecoder: Decoder[ProofData] = (c: HCursor) => for {
      id <- c.downField("id").as[String]
      sig <- c.downField("signature").as[String]
    } yield ProofData(id, sig)
    
    implicit val envDecoder: Decoder[SignedEnvelope] = (c: HCursor) => for {
      value <- c.downField("value").as[String]
      proofs <- c.downField("proofs").as[List[ProofData]]
    } yield SignedEnvelope(value, proofs)
    
    for {
      _ <- logger[F].info(s"=== Starting data ingestion ===")
      _ <- logger[F].info(s"Payload (first 300): ${jsonString.take(300)}")
      
      // Parse envelope
      envelope <- Async[F].fromEither(
        decode[SignedEnvelope](jsonString)
          .leftMap(e => new Exception(s"Failed to parse envelope: ${e.getMessage}"))
      )
      
      _ <- logger[F].info(s"Envelope has ${envelope.proofs.size} proof(s)")
      _ <- logger[F].info(s"Value (base64, first 100): ${envelope.value.take(100)}")
      
      // Decode base64 to get SignatureRequest
      sigReqBytes <- Async[F].delay(Base64.getDecoder.decode(envelope.value))
      sigReqJson = new String(sigReqBytes, StandardCharsets.UTF_8)
      _ <- logger[F].info(s"SignatureRequest JSON (first 200): ${sigReqJson.take(200)}")
      
      // Parse SignatureRequest
      sigReq <- Async[F].fromEither(
        decode[SignatureRequest](sigReqJson)
          .leftMap(e => new Exception(s"Failed to parse SignatureRequest: ${e.getMessage}"))
      )
      
      _ <- logger[F].info(s"Content (first 150): ${sigReq.content.take(150)}")
      
      // Parse InsuranceUpdate from content
      update <- Async[F].fromEither(
        decode[InsuranceUpdate](sigReq.content)
          .leftMap(e => new Exception(s"Failed to decode InsuranceUpdate: ${e.getMessage}"))
      )
      
      _ <- logger[F].info(s"Parsed: ${update.getClass.getSimpleName}")
      
      // Build signature proof
      proof = SignatureProof(
        Id(Hex(envelope.proofs.head.id)),
        Signature(Hex(envelope.proofs.head.signature))
      )
      proofs = NonEmptySet.one(proof)
      
      _ <- logger[F].info(s"=== Ingestion complete ===")
      
    } yield Signed(update, proofs)
  }

}
