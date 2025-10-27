package io.github.mbannour.mongo.codecs.models

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.MongoClient

case class Address(street: String, city: String, zipCode: Int, employeeId: EmployeeId)
