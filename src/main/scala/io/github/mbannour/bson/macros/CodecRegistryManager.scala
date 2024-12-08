package io.github.mbannour.bson.macros

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import scala.jdk.FunctionConverters.*

import java.util.concurrent.atomic.AtomicReference

object CodecRegistryManager:

  private val codecRegistryList: AtomicReference[List[CodecRegistry]] =
    new AtomicReference(List(CodecRegistries.fromCodecs()))

  def addCodecRegistry(registry: CodecRegistry): Unit =
    updateRegistryList(existingRegistries => registry :: existingRegistries)

  def addCodecRegistries(registries: List[CodecRegistry]): Unit =
    updateRegistryList(existingRegistries => registries ++ existingRegistries)

  def getCombinedCodecRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(codecRegistryList.get()*)

  private def updateRegistryList(updateFunction: List[CodecRegistry] => List[CodecRegistry]): Unit =
    codecRegistryList.updateAndGet(updateFunction.asJava)
end CodecRegistryManager
