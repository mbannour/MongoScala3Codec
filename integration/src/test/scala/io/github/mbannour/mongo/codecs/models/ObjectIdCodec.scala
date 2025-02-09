package io.github.mbannour.mongo.codecs.models

import org.bson.codecs.{DecoderContext, EncoderContext, IntegerCodec, ObjectIdCodec, StringCodec, Codec as BSONCodec}
import org.bson.types.ObjectId
import org.bson.{BsonReader, BsonWriter}

import scala.reflect.ClassTag

/** Creates a new BSON codec for a wrapper type `T` that wraps a basic type `V`.
  *
  * @param fromV
  *   Function to build a `T` from a `V`
  * @param toV
  *   Function to extract the underlying `V` from a `T`
  * @param codecV
  *   A codec for the underlying type `V`
  * @tparam T
  *   The wrapper type (often an AnyVal or a case class)
  * @tparam V
  *   The underlying basic type
  * @return
  *   A BSONCodec for `T`
  */
def typedWrapperBSONCodec[T: ClassTag, V](fromV: V => T, toV: T => V, codecV: BSONCodec[V]): BSONCodec[T] =
  new BSONCodec[T]:
    override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit =
      codecV.encode(writer, toV(value), encoderContext)

    override def decode(reader: BsonReader, decoderContext: DecoderContext): T =
      fromV(codecV.decode(reader, decoderContext))
    override def getEncoderClass: Class[T] =
      summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]

/** Creates a new BSON codec for a wrapper type `T` that wraps an `ObjectId`.
  *
  * @param fromObjectId
  *   Function to build a `T` from an `ObjectId`
  * @param toObjectId
  *   Function to extract the `ObjectId` from a `T`
  * @tparam T
  *   The wrapper type (an AnyVal or a case class)
  * @return
  *   A BSONCodec for `T`
  */
def typedObjectIdBSONCodec[T: ClassTag](fromObjectId: ObjectId => T, toObjectId: T => ObjectId): BSONCodec[T] =
  // Here we use the provided ObjectIdCodec from the MongoDB driver.
  typedWrapperBSONCodec[T, ObjectId](fromObjectId, toObjectId, new ObjectIdCodec())
