package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.CodecProviderMacro
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.MongoClient

case class Company(name: String, employees: Option[Seq[Person]])

object Company:

  val companyRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(CodecProviderMacro.createCodecProviderEncodeNone[Person](Person.personRegistry)),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )
