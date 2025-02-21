package io.github.mbannour.mongo.codecs.models

import org.bson.types.ObjectId
import org.mongodb.scala.bson.annotations.BsonProperty

case class Person(
    _id: ObjectId,
    @BsonProperty("n") name: String,
    employeeId: Map[String, EmployeeId],
    middleName: Option[String],
    age: Int,
    height: Double,
    married: Boolean,
    address: Option[Address],
    nicknames: Seq[String]
)
