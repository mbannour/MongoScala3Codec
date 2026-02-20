package io.github.mbannour.mongo.codecs

/** Configuration for BSON codec generation behavior.
  *
  * This configuration object encapsulates all codec generation options, making the API more type-safe and extensible than using boolean
  * flags.
  *
  * @param noneHandling
  *   Strategy for handling `None` values in `Option` fields.
  * @param discriminatorField
  *   The field name used for storing type discriminators in sealed trait encoding (default: "_type").
  * @param discriminatorStrategy
  *   Strategy for generating discriminator values from type names.
  *
  * ===Example Usage===
  * {{{
  *   val config = CodecConfig(
  *     noneHandling = NoneHandling.Ignore,
  *     discriminatorField = "_type",
  *     discriminatorStrategy = DiscriminatorStrategy.SimpleName
  *   )
  * }}}
  */
case class CodecConfig(
    noneHandling: NoneHandling = NoneHandling.Encode,
    discriminatorField: String = "_type",
    discriminatorStrategy: DiscriminatorStrategy = DiscriminatorStrategy.SimpleName,
    private[codecs] val writeDiscriminator: Boolean = false
):
  /** Returns true if None values should be encoded as BSON null. */
  def shouldEncodeNone: Boolean = noneHandling == NoneHandling.Encode

  /** Helper method to set None handling to Ignore - fluent API for functional configuration.
    *
    * @example
    *   {{{
    *   builder.configure(_.withIgnoreNone)
    *   }}}
    */
  def withIgnoreNone: CodecConfig = copy(noneHandling = NoneHandling.Ignore)

  /** Helper method to set None handling to Encode - fluent API for functional configuration.
    *
    * @example
    *   {{{
    *   builder.configure(_.withEncodeNone)
    *   }}}
    */
  def withEncodeNone: CodecConfig = copy(noneHandling = NoneHandling.Encode)

  /** Helper method to set custom discriminator field name.
    *
    * @example
    *   {{{
    *   builder.configure(_.withDiscriminatorField("_class"))
    *   }}}
    */
  def withDiscriminatorField(fieldName: String): CodecConfig = copy(discriminatorField = fieldName)

  /** Helper method to set discriminator strategy.
    *
    * @example
    *   {{{
    *   builder.configure(_.withDiscriminatorStrategy(DiscriminatorStrategy.FullyQualifiedName))
    *   }}}
    */
  def withDiscriminatorStrategy(strategy: DiscriminatorStrategy): CodecConfig = copy(discriminatorStrategy = strategy)
end CodecConfig

/** Strategy for handling `None` values in `Option` fields during BSON encoding.
  */
enum NoneHandling:
  /** Encode `None` as BSON `null`. */
  case Encode

  /** Omit fields with `None` values from the BSON document. */
  case Ignore
end NoneHandling

/** Strategy for generating discriminator values for sealed trait encoding.
  *
  * The discriminator value is used to identify which concrete type to instantiate when decoding a sealed trait field.
  */
enum DiscriminatorStrategy:
  /** Use the simple class name (e.g., "Completed" for class "com.example.Completed").
    *
    * This is the default strategy as it provides readable BSON documents while being sufficiently unique in most cases.
    */
  case SimpleName

  /** Use the fully qualified class name (e.g., "com.example.Completed").
    *
    * Use this strategy when you have name collisions across packages or need guaranteed uniqueness.
    */
  case FullyQualifiedName

  /** Use a custom discriminator mapping.
    *
    * @param mapping
    *   A map from runtime class to discriminator string.
    *
    * Use this strategy for custom discriminator values, legacy compatibility, or optimized storage.
    */
  case Custom(mapping: Map[Class[?], String])
end DiscriminatorStrategy
