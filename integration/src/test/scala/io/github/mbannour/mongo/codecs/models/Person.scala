package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.models.Address.addressRegistry
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.annotations.BsonProperty
import io.github.mbannour.mongo.codecs.CodecProviderMacro

case class Person(
    _id: ObjectId,
    @BsonProperty("n") name: String,
    middleName: Option[String],
    age: Int,
    height: Double,
    married: Boolean,
    address: Option[Address],
    nicknames: Seq[String]
)

object Person:

  val personRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(CodecProviderMacro.createCodecProviderEncodeNone[Address](addressRegistry)),
      CodecRegistries.fromCodecs(EmployeeId.dealerIdBsonCodec),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )
