package io.github.mbannour.mongo.codecs.models

import org.mongodb.scala.MongoClient
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.Codec
import io.github.mbannour.bson.macros.*
import io.github.mbannour.mongo.codecs.{CodecProviderMacro, RegistryBuilder}

object DefaultCodecRegistries:

  val defaultRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withCodecs(EmployeeId.employeeIdBsonCodec, DateField.dateFieldCodec)
      .registerAll[(Address, Person)]
      .build

end DefaultCodecRegistries
