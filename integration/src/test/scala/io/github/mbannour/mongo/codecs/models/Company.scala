package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.MongoClient

case class Company(name: String, employees: Option[Seq[Person]])

object Company:
  
  val defaultRegistry: CodecRegistry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .withCodecs(EmployeeId.employeeIdBsonCodec, DateField.dateFieldCodec)
    .registerAll[(Address, Person, Company)]
    .build

end Company
