package io.github.mbannour.mongo.codecs

import scala.quoted._
import scala.reflect.ClassTag

import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}

/** Type class for BSON encoding and decoding.
  *
  * This provides a more functional and composable alternative to directly working with MongoDB codecs. It allows for better type safety and
  * easier testing.
  *
  * @tparam T
  *   The type to encode/decode
  */
trait BsonCodec[T]:
  /** Encode a value to BSON */
  def encode(writer: BsonWriter, value: T, context: EncoderContext): Unit

  /** Decode a value from BSON */
  def decode(reader: BsonReader, context: DecoderContext): T

  /** The runtime class for this codec */
  def encoderClass: Class[T]

  /** Convert this BsonCodec to a MongoDB Codec */
  def toCodec: Codec[T] = new Codec[T]:
    override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit =
      BsonCodec.this.encode(writer, value, encoderContext)

    override def decode(reader: BsonReader, decoderContext: DecoderContext): T =
      BsonCodec.this.decode(reader, decoderContext)

    override def getEncoderClass: Class[T] = BsonCodec.this.encoderClass
end BsonCodec

object BsonCodec:
  /** Summon a BsonCodec instance from implicit scope */
  def apply[T](using codec: BsonCodec[T]): BsonCodec[T] = codec

  /** Create a BsonCodec from a MongoDB Codec */
  def fromCodec[T](codec: Codec[T]): BsonCodec[T] = new BsonCodec[T]:
    override def encode(writer: BsonWriter, value: T, context: EncoderContext): Unit =
      codec.encode(writer, value, context)

    override def decode(reader: BsonReader, context: DecoderContext): T =
      codec.decode(reader, context)

    override def encoderClass: Class[T] = codec.getEncoderClass

  /** Create a BsonCodec using inline macro derivation for case classes */
  inline def derived[T](using ct: ClassTag[T], cfg: CodecConfig): BsonCodec[T] =
    ${ derivedImpl[T]('ct, 'cfg) }

  private def derivedImpl[T: Type](ct: Expr[ClassTag[T]], cfg: Expr[CodecConfig])(using Quotes): Expr[BsonCodec[T]] =
    import quotes.reflect.*

    val tpeSym = TypeRepr.of[T].typeSymbol
    if !tpeSym.flags.is(Flags.Case) then
      report.errorAndAbort(s"${tpeSym.name} is not a case class. BsonCodec.derived only works with case classes.")

    '{
      val codec = CaseClassCodecGenerator.generateCodec[T](
        $cfg,
        org.bson.codecs.configuration.CodecRegistries.fromCodecs()
      )(using $ct)
      BsonCodec.fromCodec(codec)
    }
  end derivedImpl

  /** Map this codec to a different type */
  extension [A](codec: BsonCodec[A])
    def imap[B](f: A => B)(g: B => A)(using ClassTag[B]): BsonCodec[B] = new BsonCodec[B]:
      override def encode(writer: BsonWriter, value: B, context: EncoderContext): Unit =
        codec.encode(writer, g(value), context)

      override def decode(reader: BsonReader, context: DecoderContext): B =
        f(codec.decode(reader, context))

      override def encoderClass: Class[B] = summon[ClassTag[B]].runtimeClass.asInstanceOf[Class[B]]
end BsonCodec
