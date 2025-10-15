package io.github.mbannour.mongo.codecs

import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter, BsonType}
import scala.util.{Try, Success, Failure}

/** Codec for scala.util.Try[T] that encodes as a discriminated union.
  *
  * Success[T] is encoded as: {"_tag": "Success", "value": T} Failure is encoded as: {"_tag": "Failure", "exception": String}
  */
class TryCodec[T](valueCodec: Codec[T]) extends Codec[Try[T]]:

  override def encode(writer: BsonWriter, value: Try[T], encoderContext: EncoderContext): Unit =
    writer.writeStartDocument()
    value match
      case Success(v) =>
        writer.writeString("_tag", "Success")
        writer.writeName("value")
        valueCodec.encode(writer, v, encoderContext)
      case Failure(exception) =>
        writer.writeString("_tag", "Failure")
        writer.writeString("exception", exception.toString)
    writer.writeEndDocument()

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Try[T] =
    reader.readStartDocument()

    var tag: String = null
    var result: Try[T] = null

    while reader.readBsonType() != BsonType.END_OF_DOCUMENT do
      val fieldName = reader.readName()
      fieldName match
        case "_tag" =>
          tag = reader.readString()
        case "value" if tag == "Success" =>
          result = Success(valueCodec.decode(reader, decoderContext))
        case "exception" if tag == "Failure" =>
          val exceptionMsg = reader.readString()
          result = Failure(new Exception(exceptionMsg))
        case _ =>
          reader.skipValue()

    reader.readEndDocument()

    if result == null then
      throw new IllegalStateException(s"Invalid Try document: missing or invalid tag")

    result

  override def getEncoderClass: Class[Try[T]] = classOf[Try[T]]

end TryCodec

/** Codec for Either[L, R] that encodes as a discriminated union.
  *
  * Left[L] is encoded as: {"_tag": "Left", "value": L} Right[R] is encoded as: {"_tag": "Right", "value": R}
  */
class EitherCodec[L, R](leftCodec: Codec[L], rightCodec: Codec[R]) extends Codec[Either[L, R]]:

  override def encode(writer: BsonWriter, value: Either[L, R], encoderContext: EncoderContext): Unit =
    writer.writeStartDocument()
    value match
      case Left(l) =>
        writer.writeString("_tag", "Left")
        writer.writeName("value")
        leftCodec.encode(writer, l, encoderContext)
      case Right(r) =>
        writer.writeString("_tag", "Right")
        writer.writeName("value")
        rightCodec.encode(writer, r, encoderContext)
    writer.writeEndDocument()

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Either[L, R] =
    reader.readStartDocument()

    var tag: String = null
    var result: Either[L, R] = null

    while reader.readBsonType() != BsonType.END_OF_DOCUMENT do
      val fieldName = reader.readName()
      fieldName match
        case "_tag" =>
          tag = reader.readString()
        case "value" =>
          tag match
            case "Left"  => result = Left(leftCodec.decode(reader, decoderContext))
            case "Right" => result = Right(rightCodec.decode(reader, decoderContext))
            case _       => reader.skipValue()
        case _ =>
          reader.skipValue()

    reader.readEndDocument()

    if result == null then
      throw new IllegalStateException(s"Invalid Either document: missing or invalid tag")

    result

  override def getEncoderClass: Class[Either[L, R]] = classOf[Either[L, R]]

end EitherCodec

