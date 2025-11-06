package com.my.nft.shared_data

import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.foldable.toFoldableOps
import cats.syntax.traverse.toTraverseOps
import com.my.nft.shared_data.types.Types._
import io.constellationnetwork.schema.address.Address
import io.constellationnetwork.security.SecurityProvider
import io.constellationnetwork.security.signature.signature.SignatureProof

import java.net.URL
import java.security.MessageDigest
import scala.util.Try

object Utils {
  def isValidURL(url: String): Boolean =
    Try(new URL(url).toURI).isSuccess

  def getAllAddressesFromProofs[F[_] : Async : SecurityProvider](
    proofs: NonEmptySet[SignatureProof]
  ): F[List[Address]] =
    proofs
      .map(_.id)
      .toList
      .traverse(_.toAddress[F])

  def getFirstAddressFromProofs[F[_] : Async : SecurityProvider](
    proofs: NonEmptySet[SignatureProof]
  ): F[Address] =
    proofs.head.id.toAddress[F]
  
  // Insurance-specific utilities
  def hashString(s: String): String = {
    MessageDigest
      .getInstance("SHA-256")
      .digest(s.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString
      .take(16)
  }
  
  def hashTemplate(template: InsuranceTemplate): String = {
    val content = s"${template.providerName}:${template.templateVersion}"
    MessageDigest
      .getInstance("SHA-256")
      .digest(content.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString
  }
  
  def generateValidationId(validate: ValidateCircumstance, ordinal: Long): String = {
    s"${validate.providerName}-${ordinal}-${hashString(validate.userRequestId).take(8)}"
  }
  
  def getProviderEmail(providerName: String): String = {
    s"notifications@${providerName.toLowerCase.replaceAll(" ", "")}.com"
  }
  
  def sortUpdates(updates: List[InsuranceUpdate]): List[InsuranceUpdate] = {
    updates.sortBy {
      case _: TestUpdate => -1
      case _: UploadContractTemplate => 0
      case _: ValidateCircumstance => 1
      case _: RequestNotification => 2
    }
  }
  
  def isValidEmail(email: String): Boolean = {
    email.contains("@") && email.contains(".")
  }
}

