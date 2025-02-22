package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.{CodecProviderMacro, PriorityCodec}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

// Scala Enumeration for Priority
object Priority extends Enumeration:
  type Priority = Value
  val Low, Medium, High = Value

// A case class that uses the Scala Enumeration
case class Task(_id: ObjectId, title: String, priority: Priority.Value)

object Task:

  private val taskProvider = CodecProviderMacro.createCodecProviderEncodeNone[Task]

  val defaultRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(taskProvider),
      CodecRegistries.fromCodecs(PriorityCodec),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )
    
  given CodecRegistry = defaultRegistry
end Task
