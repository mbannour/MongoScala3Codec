package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class TestCaseClass(name: String, age: Int)


class CodecProviderMacroSpec extends AnyFlatSpec with Matchers {

  "createCodecProvider" should "create a CodecProvider for a valid case class" in {

    val codecRegistry: CodecRegistry = new CodecRegistry {
      override def get[T](clazz: Class[T]): Codec[T] = null

      override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] = null
    }


    val codecProvider = CodecProviderMacro.createCodecProvider[TestCaseClass](codecRegistry, encodeNone = true)
    val codec = codecProvider.get(classOf[TestCaseClass], codecRegistry)
    codec should not be null
  }

  it should "return null for a non-matching class" in {
    val codecRegistry: CodecRegistry = new CodecRegistry {
      override def get[T](clazz: Class[T]): Codec[T] = null

      override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] = null
    }

    val codecProvider = CodecProviderMacro.createCodecProvider[TestCaseClass](codecRegistry, encodeNone = true)
    val codec = codecProvider.get(classOf[String], codecRegistry)
    codec shouldBe null
  }

}

