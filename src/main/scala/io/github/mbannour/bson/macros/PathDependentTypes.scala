package io.github.mbannour.bson.macros

import scala.annotation.tailrec
import scala.quoted.*

/** Rejects models that can only be named through a specific instance.
  *
  * Codec derivation builds its trees from symbols, so a member of a class is re-referenced as `Owner.this.Member`. That is the right type
  * wherever the enclosing `this` is in scope, but not when the call site names the member through an instance instead - `c.Paint` and
  * `Container.this.Paint` are different types, and splicing one where the other is expected fails deep inside macro expansion.
  *
  * Detecting it up front turns that crash into an ordinary diagnostic.
  */
object PathDependentTypes:

  /** Aborts with a diagnostic when `T` is reached through an instance rather than through a stable path.
    *
    * The test is on the type's prefix, not on its owner chain: a model declared in a class body and used from inside that class has a
    * non-module owner yet is perfectly derivable, because its prefix is the enclosing `this` that the generated trees reproduce. Only a
    * prefix that selects through a term which is neither an object nor a package - a `val`, a parameter - names a type the macro cannot
    * reconstruct.
    */
  def rejectIfPathDependent[T: Type](using Quotes): Unit =
    import quotes.reflect.*

    /** The name of the first instance-valued term in the prefix chain, if any. Objects and packages are stable, so they are skipped;
      * `ThisType` and `NoPrefix` end the walk.
      */
    @tailrec
    def instanceTerm(prefix: TypeRepr): Option[String] = prefix match
      case TermRef(qualifier, name) =>
        val symbol = prefix.termSymbol
        if symbol.flags.is(Flags.Module) || symbol.flags.is(Flags.Package) then instanceTerm(qualifier)
        else Some(name)
      case _ => None

    val tpe = TypeRepr.of[T].dealias

    val viaInstance = tpe match
      case TypeRef(prefix, _) => instanceTerm(prefix)
      case _                  => None

    viaInstance.foreach { termName =>
      // The default printer keeps the instance prefix ('c.Paint'); the short printer would drop it and print just 'Paint'.
      val shownType = tpe.show
      val memberName = tpe.typeSymbol.name
      val ownerName = tpe.typeSymbol.maybeOwner.name

      report.errorAndAbort(
        s"Cannot derive a codec for path-dependent type '$shownType'." +
          s"\n\n'$memberName' is a member of the class '$ownerName', so naming it through the instance '$termName' makes its type" +
          s" depend on that instance. Derivation can only refer to '$memberName' as '$ownerName.this.$memberName', which is a" +
          " different type, so path-dependent types are unsupported." +
          "\n\nSuggestions:" +
          s"\n  • Move '$memberName' out of the class '$ownerName', to a top-level or object scope" +
          s"\n  • Or make '$ownerName' an object, so '$memberName' is reached through a stable path" +
          s"\n  • Or derive from inside '$ownerName', where '$memberName' is reached through 'this' rather than through '$termName'" +
          "\n  • Or supply a codec explicitly with CodecRegistries.fromCodecs(...)"
      )
    }
  end rejectIfPathDependent
end PathDependentTypes
