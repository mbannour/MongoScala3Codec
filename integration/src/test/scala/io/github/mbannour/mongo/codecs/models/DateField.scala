package io.github.mbannour.mongo.codecs.models

import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}

import java.time.{Instant, ZoneId, ZonedDateTime}

case class DateField(time: ZonedDateTime) extends AnyVal

object DateField:
  given dateFieldCodec: Codec[DateField] with
    override def encode(writer: BsonWriter, value: DateField, encoderContext: EncoderContext): Unit =
      writer.writeDateTime(value.time.toInstant.toEpochMilli)

    override def decode(reader: BsonReader, decoderContext: DecoderContext): DateField =
      val epochMilli = reader.readDateTime()
      DateField(ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault()))

    override def getEncoderClass: Class[DateField] = classOf[DateField]
  end dateFieldCodec
end DateField
