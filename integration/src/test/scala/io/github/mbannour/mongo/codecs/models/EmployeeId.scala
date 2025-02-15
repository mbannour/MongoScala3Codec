package io.github.mbannour.mongo.codecs.models

import org.bson.codecs.{Codec as BSONCodec}
import org.bson.types.ObjectId

final case class EmployeeId(value: ObjectId) extends AnyVal

object EmployeeId:
  def apply(): EmployeeId = EmployeeId(new ObjectId)

  val dealerIdBsonCodec: BSONCodec[EmployeeId] =
    typedObjectIdBSONCodec(EmployeeId.apply, _.value)
