package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.codecs.configuration.CodecRegistries.{fromRegistries, fromProviders, fromCodecs}
import scala.reflect.ClassTag

/** `RegistryBuilder` is a fluent utility for constructing a MongoDB [[org.bson.codecs.configuration.CodecRegistry]] in Scala 3.
  *
  * It wraps MongoDB's codec/registry APIs in a type-safe builder that plays nicely with Scala 3 inline macros for deriving codecs for case
  * classes.
  *
  * ===Features===
  *   - Choose between **encode `None` as `null`** or **ignore `None` fields**.
  *   - Add individual codecs (`addCodec`) or many at once (`addCodecs`).
  *   - Automatically derive codecs for **case classes** with `derive[T]`.
  *   - Works seamlessly with nested case classes and value classes.
  *   - Configure discriminator field names for sealed hierarchies.
  *
  * ===Example Usage===
  * {{{
  *   val registry =
  *     RegistryBuilder.Builder.base(MongoClient.DEFAULT_CODEC_REGISTRY)
  *       .withConfig(CodecConfig(noneHandling = NoneHandling.Ignore))
  *       .addCodec(employeeIdCodec)     // custom codec for a value class
  *       .addCodecs(addressCodec, somethingElseCodec) // bulk add
  *       .derive[Address]               // derive codecs for case classes
  *       .derive[Person]
  *       .build
  *
  *   val db: MongoDatabase = MongoClient("mongodb://localhost")
  *     .getDatabase("test_db")
  *     .withCodecRegistry(registry)
  * }}}
  */
object RegistryBuilder:

  /** Fluent builder for a [[CodecRegistry]].
    *
    * @param base
    *   The base registry (e.g., `MongoClient.DEFAULT_CODEC_REGISTRY`).
    * @param config
    *   Configuration for codec generation behavior.
    * @param providers
    *   Collected list of [[CodecProvider]]s added via `derive`.
    * @param codecs
    *   Collected list of explicit [[Codec]]s added via `addCodec` or `addCodecs`.
    */
  final class Builder private (
      private val base: CodecRegistry,
      private val config: CodecConfig,
      private val providers: List[CodecProvider],
      private val codecs: List[Codec[?]]
  ):

    /** Set the codec configuration.
      *
      * @param newConfig
      *   The codec configuration to use.
      */
    def withConfig(newConfig: CodecConfig): Builder = 
      copy(base, newConfig, providers, codecs)

    /** Switch policy: encode `None` as BSON `null`.
      *
      * @deprecated Use `withConfig(config.copy(noneHandling = NoneHandling.Encode))` instead.
      */
    def encodeNonePolicy: Builder = 
      copy(base, config.copy(noneHandling = NoneHandling.Encode), providers, codecs)

    /** Switch policy: omit `None` fields entirely.
      *
      * @deprecated Use `withConfig(config.copy(noneHandling = NoneHandling.Ignore))` instead.
      */
    def ignoreNonePolicy: Builder = 
      copy(base, config.copy(noneHandling = NoneHandling.Ignore), providers, codecs)

    /** Set the discriminator field name for sealed hierarchies.
      *
      * @param fieldName
      *   The field name to use for type discriminators.
      */
    def withDiscriminatorField(fieldName: String): Builder =
      copy(base, config.copy(discriminatorField = fieldName), providers, codecs)

    /** Add a single explicit codec.
      *
      * Useful for value classes or third-party types where automatic derivation is not possible.
      */
    def addCodec[A](c: Codec[A]): Builder =
      copy(base, config, providers, c :: codecs)

    /** Add a list of codecs. */
    def addCodecs(cs: List[Codec[?]]): Builder =
      copy(base, config, providers, cs.reverse ::: codecs)

    /** Add multiple codecs via varargs. */
    def addCodecs(cs: Codec[?]*): Builder =
      addCodecs(cs.toList)

    /** Derive a codec provider for a case class `T`.
      *
      * Relies on Scala 3 inline macros to auto-generate the BSON codec. Works for nested case classes and sealed hierarchies.
      */
    inline def derive[T](using ct: ClassTag[T]): Builder =
      val current = currentRegistry
      val p: CodecProvider = CodecProviderMacro.createCodecProvider[T](using ct, config, current)
      prependProvider(p)

    /** Build the final [[CodecRegistry]]. */
    def build: CodecRegistry =
      merge(base, providers, codecs)

    private def merge(base: CodecRegistry, provs: List[CodecProvider], cds: List[Codec[?]]): CodecRegistry =
      val parts = List.newBuilder[CodecRegistry]
      parts += base
      if cds.nonEmpty then parts += fromCodecs(cds.reverse*)
      if provs.nonEmpty then parts += fromProviders(provs.reverse*)
      fromRegistries(parts.result()*)

    private def currentRegistry: CodecRegistry =
      merge(base, providers, codecs)

    private def copy(
        newBase: CodecRegistry,
        newConfig: CodecConfig,
        newProviders: List[CodecProvider],
        newCodecs: List[Codec[?]]
    ): Builder =
      new Builder(newBase, newConfig, newProviders, newCodecs)

    private def prependProvider(p: CodecProvider): Builder =
      copy(base, config, p :: providers, codecs)
  end Builder

  object Builder:
    /** Start building from a base registry.
      *
      * @param base
      *   Usually `MongoClient.DEFAULT_CODEC_REGISTRY`
      */
    def base(base: CodecRegistry): Builder =
      new Builder(base, CodecConfig(), providers = Nil, codecs = Nil)
      
    /** Start building from a base registry with custom configuration.
      *
      * @param base
      *   Usually `MongoClient.DEFAULT_CODEC_REGISTRY`
      * @param config
      *   The codec configuration to use
      */
    def base(base: CodecRegistry, config: CodecConfig): Builder =
      new Builder(base, config, providers = Nil, codecs = Nil)
end RegistryBuilder
