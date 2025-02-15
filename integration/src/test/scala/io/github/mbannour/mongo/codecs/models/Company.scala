package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.CodecProviderMacro
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.MongoClient

case class Company(name: String, employees: Option[Seq[Person]])

object Company {

  private val addressProvider = CodecProviderMacro.createCodecProviderEncodeNone[Address]
  private val personProvider = CodecProviderMacro.createCodecProviderEncodeNone[Person]
  private val companyProvider = CodecProviderMacro.createCodecProviderEncodeNone[Company]

  private val customCodecs = CodecRegistries.fromCodecs(
    EmployeeId.dealerIdBsonCodec,
    DateField.dateFieldCodec
  )

  val defaultRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(addressProvider, personProvider, companyProvider),
      customCodecs,
      MongoClient.DEFAULT_CODEC_REGISTRY
    )

  given CodecRegistry = defaultRegistry
  
}
