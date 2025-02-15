package io.github.mbannour.mongo.codecs.models

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.MongoClient

case class Address(street: String, city: String, zipCode: Int, employeeId: EmployeeId)

object Address:

  val addressRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromCodecs(EmployeeId.dealerIdBsonCodec),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )
end Address
