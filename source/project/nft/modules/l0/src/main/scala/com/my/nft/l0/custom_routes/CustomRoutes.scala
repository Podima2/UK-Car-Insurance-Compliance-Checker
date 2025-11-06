package com.my.nft.l0.custom_routes

import cats.effect.Async
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import com.my.nft.shared_data.calculated_state.CalculatedStateService
import com.my.nft.shared_data.types.Types._
import eu.timepit.refined.auto._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import io.constellationnetwork.ext.http4s.AddressVar
import io.constellationnetwork.routes.internal.{InternalUrlPrefix, PublicRoutes}
import io.constellationnetwork.schema.address.Address

case class CustomRoutes[F[_] : Async](calculatedStateService: CalculatedStateService[F]) extends Http4sDsl[F] with PublicRoutes[F] {


  // NFT collection endpoints are disabled in Insurance build; return empty to keep UI stable
  private def getAllCollections: F[Response[F]] = {
    Ok(List.empty[String])
  }

  private def getCollectionById(
    collectionId: String
  ): F[Response[F]] = {
    NotFound()
  }

  private def getCollectionNFTs(
    collectionId: String
  ): F[Response[F]] = {
    NotFound()
  }

  private def getCollectionNFTById(
    collectionId: String,
    nftId       : Long
  ): F[Response[F]] = {
    NotFound()
  }

  private def getAllCollectionsOfAddress(
    address: Address
  ): F[Response[F]] = {
    Ok(List.empty[String])
  }

  private def getAllNFTsOfAddress(
    address: Address
  ): F[Response[F]] = {
    Ok(List.empty[String])
  }

  // Insurance-specific routes
  private def getInsuranceState: F[InsuranceCalculatedState] =
    calculatedStateService.getCalculatedState.map(_.state)
  
  private def getProviders: F[Response[F]] = {
    getInsuranceState.flatMap { state =>
      val providers = state.contractTemplates.keys.toList
      Ok(providers)
    }
  }
  
  private def getProviderDetails(providerName: String): F[Response[F]] = {
    getInsuranceState.flatMap { state =>
      state.contractTemplates.get(providerName) match {
        case Some(template) =>
          val details = ProviderDetails(
            name = template.providerName,
            version = template.templateVersion,
            categories = template.terms.map(_.category).distinct,
            totalRules = template.riskRules.size
          )
          Ok(details)
        case None =>
          NotFound(s"Provider $providerName not found")
      }
    }
  }
  
  private def getValidationResult(validationId: String): F[Response[F]] = {
    getInsuranceState.flatMap { state =>
      state.validationHistory.get(validationId) match {
        case Some(validation) =>
          val canNotify = validation.riskScore == RiskLevel.High && 
            !state.pendingNotifications.exists(_.validationId == validationId)
          
          val response = ValidationResponse(
            id = validation.id,
            provider = validation.providerName,
            riskLevel = validation.riskScore.toString,
            justification = validation.riskJustification,
            matchedCategories = validation.matchedTerms,
            canNotify = canNotify
          )
          Ok(response)
        case None =>
          NotFound(s"Validation $validationId not found")
      }
    }
  }
  
  private def getProviderStats(providerName: String): F[Response[F]] = {
    getInsuranceState.flatMap { state =>
      // Compute stats from calculated state as a fallback when OnChain is not directly accessible
      val providerValidations = state.validationHistory.values.filter(_.providerName == providerName)
      val total = providerValidations.size.toLong
      val high = providerValidations.count(_.riskScore == RiskLevel.High).toLong
      val sent = state.notificationHistory.values.count { nr =>
        state.validationHistory.get(nr.validationId).exists(_.providerName == providerName)
      }.toLong
      val stats = ProviderStats(total, high, sent)
      Ok(stats)
    }
  }
  
  object OptionalLimitQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")
  object OptionalOffsetQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("offset")
  
  private def getProviderValidations(
    providerName: String,
    limit: Option[Int],
    offset: Option[Int]
  ): F[Response[F]] = {
    getInsuranceState.flatMap { state =>
      val allValidations = state.validationHistory.values
        .filter(_.providerName == providerName)
        .toList
        .sortBy(-_.validatedAtOrdinal)
      
      val limitVal = limit.getOrElse(20)
      val offsetVal = offset.getOrElse(0)
      
      val paginatedResults = allValidations
        .slice(offsetVal, offsetVal + limitVal)
        .map { v =>
          ValidationSummaryResponse(
            id = v.id,
            riskLevel = v.riskScore.toString,
            timestamp = v.timestamp,
            notificationSent = state.pendingNotifications.exists(_.validationId == v.id)
          )
        }
      
      val response = PaginatedResponse(
        results = paginatedResults,
        total = allValidations.size,
        limit = limitVal,
        offset = offsetVal
      )
      
      Ok(response)
    }
  }

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    // Insurance routes
    case GET -> Root / "insurance" / "providers" => getProviders
    case GET -> Root / "insurance" / "providers" / providerName => getProviderDetails(providerName)
    case GET -> Root / "insurance" / "validations" / validationId => getValidationResult(validationId)
    case GET -> Root / "insurance" / "stats" / providerName => getProviderStats(providerName)
    case GET -> Root / "insurance" / "providers" / providerName / "validations" 
      :? OptionalLimitQueryParamMatcher(limit) +& OptionalOffsetQueryParamMatcher(offset) =>
      getProviderValidations(providerName, limit, offset)

    // Aliases for simplified frontend access
    case GET -> Root / "providers" => getProviders
    case GET -> Root / "providers" / providerName => getProviderDetails(providerName)
    case GET -> Root / "validations" / validationId => getValidationResult(validationId)
    case GET -> Root / "stats" / providerName => getProviderStats(providerName)
    
    // Original NFT routes
    case GET -> Root / "collections" => getAllCollections
    case GET -> Root / "collections" / collectionId => getCollectionById(collectionId)
    case GET -> Root / "collections" / collectionId / "nfts" => getCollectionNFTs(collectionId)
    case GET -> Root / "collections" / collectionId / "nfts" / nftId => getCollectionNFTById(collectionId, nftId.toLong)
    case GET -> Root / "addresses" / AddressVar(address) / "collections" => getAllCollectionsOfAddress(address)
    case GET -> Root / "addresses" / AddressVar(address) / "nfts" => getAllNFTsOfAddress(address)
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowOriginAll
      .withAllowMethodsAll
      .withAllowHeadersAll
      .withAllowCredentials(false)
      .httpRoutes(routes)

  override protected def prefixPath: InternalUrlPrefix = "/"
}
