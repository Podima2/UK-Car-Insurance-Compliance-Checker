package com.my.nft.shared_data.validations

import com.my.nft.shared_data.errors.Errors._
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object TypeValidators {
  def validateStringMaxSize(
    value    : String,
    maxSize  : Long,
    fieldName: String
  ): DataApplicationValidationErrorOr[Unit] =
    InvalidFieldSize(fieldName, maxSize).whenA(value.length > maxSize)

  def validateMapMaxSize(
    value    : Map[String, String],
    maxSize  : Long,
    fieldName: String
  ): DataApplicationValidationErrorOr[Unit] =
    InvalidFieldSize(fieldName, maxSize).whenA(value.size > maxSize)
}

