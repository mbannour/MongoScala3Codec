package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.fields.MongoFieldMapper
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

object PersonFields:
  private val fieldMap = MongoFieldMapper.asMap[Person]

  def apply(field: String): String =
    fieldMap.getOrElse(field, throw new IllegalArgumentException(s"Unknown MongoDB field: $field"))

  val id = apply("_id")
  val name = apply("n")
  val age = apply("age")

  object address:
    val city = apply("address.city")
    val street = apply("address.street")
    val zipCode = apply("address.zipCode")
end PersonFields
