package io.github.mbannour.mongo.codecs.models

import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}

import java.time.{Instant, ZoneId, ZonedDateTime}

opaque type DateField = ZonedDateTime

object DateField:
  def apply(time: ZonedDateTime): DateField = time

  extension (df: DateField) def time: ZonedDateTime = df

  given dateFieldCodec: Codec[DateField] with
    override def encode(writer: BsonWriter, value: DateField, encoderContext: EncoderContext): Unit =
      writer.writeDateTime(value.time.toInstant.toEpochMilli)

    override def decode(reader: BsonReader, decoderContext: DecoderContext): DateField =
      val epochMilli = reader.readDateTime()
      DateField(ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault()))

    override def getEncoderClass: Class[DateField] = classOf[ZonedDateTime].asInstanceOf[Class[DateField]]
  end dateFieldCodec
end DateField
