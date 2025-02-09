package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.ZonedDateTimeCodec
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

import java.time.ZonedDateTime

case class Event(_id: ObjectId, title: String, time: ZonedDateTime)

object Event {

  val eventRegistry: CodecRegistry = CodecRegistries.fromRegistries(
    CodecRegistries.fromCodecs(new ZonedDateTimeCodec),
    MongoClient.DEFAULT_CODEC_REGISTRY
  )

}
