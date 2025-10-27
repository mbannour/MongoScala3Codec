package io.github.mbannour.mongo.codecs.models

import org.bson.codecs.{Codec as BSONCodec}
import org.bson.types.ObjectId

opaque type EmployeeId = ObjectId

object EmployeeId:
  def apply(value: ObjectId): EmployeeId = value

  def apply(): EmployeeId = new ObjectId

  extension (id: EmployeeId)
    def value: ObjectId = id

  val employeeIdBsonCodec: BSONCodec[EmployeeId] =
    typedObjectIdBSONCodec(EmployeeId.apply, _.value)
