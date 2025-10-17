package io.github.mbannour.fields

import scala.quoted.*
import org.mongodb.scala.bson.annotations.BsonProperty
import scala.compiletime.uninitialized
import scala.annotation.tailrec

/** Macro-powered extractor for case-class field paths that map to MongoDB document keys.
  *
  * Core ideas:
  *   - Accepts a selector lambda like `_.address.zipCode` and returns a dot-separated path (e.g. "address.zip").
  *   - Honors `@BsonProperty` on constructor parameters and uses the overridden name when present.
  *   - Supports a transparent hop over `Option` via the `.?` syntax imported from `MongoPath.syntax`.
  *
  * Usage: import io.github.mbannour.fields.MongoPath import io.github.mbannour.fields.MongoPath.syntax.?
  *
  * case class Address(street: String, @BsonProperty("zip") zipCode: Int) case class User(address: Option[Address])
  *
  * MongoPath.of[User](_.address) // "address" MongoPath.of[User](_.address.?.zipCode) // "address.zip"
  *
  * Notes:
  *   - The `.?` method is never executed at runtime; it only helps the lambda typecheck and be recognized by the macro.
  *   - If the macro cannot detect a valid case-class field selection chain, it will abort compilation with an error.
  */
object MongoPath:

  /** Syntax helpers for transparent `Option` hops inside selector lambdas.
    *
    * The `.?` method lets you write `_.opt.?.field` to mean "walk into the option when present" without contributing anything to the
    * resulting path. It is defined `inline` and its body is never evaluated.
    */
  object syntax:

    extension [A](inline opt: Option[A])
      /** Transparent hop over Option in a field selector lambda. See [[MongoPath]] docs. */
      inline def ? : A = uninitialized

  /** Compute a Mongo-style dot-separated field path for a given case-class selector.
    *
    * Example: MongoPath.of[Address](_.zipCode) // "zip" if annotated with @BsonProperty("zip")
    *
    * @tparam T
    *   the root case class
    * @param pick
    *   a lambda selecting a field chain, e.g. `_.a.b.c`
    * @return
    *   the resolved dot-separated path
    */
  inline def of[T](inline pick: T => Any): String =
    ${ ofImpl[T]('pick) }

  private def ofImpl[T: Type](pick: Expr[T => Any])(using Quotes): Expr[String] =
    import quotes.reflect.*

    val bsonPropSym = TypeRepr.of[BsonProperty].typeSymbol

    @tailrec
    def strip(term: Term): Term = term match
      case Inlined(_, _, x) => strip(x)
      case Typed(x, _)      => strip(x)
      case x                => x

    def unwrapLambdaBody(term: Term): Term = strip(term) match
      case Lambda(_, body) => body

      case Block(List(DefDef(_, _, _, Some(body))), Closure(_, _)) => body
      case _ => report.errorAndAbort("Expected a lambda of the form (x: T) => x.field")

    inline def arglessTransparentOps: Set[String] =
      Set("?")

    def collectSelects(term0: Term): List[(TypeRepr, Symbol)] =
      val term = strip(term0)
      term match
        // Transparent ops WITHOUT a function argument (e.g. .? inline extension)
        case Apply(Select(qual, op), _) if arglessTransparentOps.contains(op) =>
          collectSelects(qual)

        case Apply(TypeApply(Select(qual, op), _), _) if arglessTransparentOps.contains(op) =>
          collectSelects(qual)

        // Plain Select for transparent ops (like .? extension method)
        case Select(qual, op) if arglessTransparentOps.contains(op) =>
          collectSelects(qual)

        // Handle inlined extension methods like .? - extract the qualifier from the inlined call
        case Select(Inlined(Some(Apply(TypeApply(Ident(op), _), List(qual))), _, _), _) if arglessTransparentOps.contains(op) =>
          // The .? unwraps Option[T] to T, so we need to get the inner type
          // then continue processing as if we had qual.field directly
          val innerType = qual.tpe.widen match
            case AppliedType(_, List(inner)) => inner // Option[Inner] -> Inner
            case other                       => other
          collectSelects(qual) :+ (innerType, term.symbol)

        // Plain field selection: Select(qual, field)
        case Select(qual, _) =>
          if arglessTransparentOps.contains(term.symbol.name) then collectSelects(qual)
          else
            val owner = strip(qual).tpe.widen
            collectSelects(qual) :+ (owner, term.symbol)

        // Identifier (lambda parameter root) – stop
        case Ident(_) => Nil

        case Block(_, expr) =>
          // keep walking into the expr; statements may be synthetic defs for the lambda
          collectSelects(expr)

        case _ =>
          // Anything else doesn't contribute to a field path
          Nil
      end match
    end collectSelects

    def annotationName(param: Symbol): Option[String] =
      param.getAnnotation(bsonPropSym).map {
        case Apply(_, List(Literal(StringConstant(v)))) => v
        case other =>
          report.errorAndAbort(s"Unexpected BsonProperty annotation on ${param.name}: ${other.show}")
      }

    // Convert (owner, selectedSymbol) to displayed segment:
    // keep only real constructor params; prefer @BsonProperty override.
    def segment(owner: TypeRepr, fieldSym: Symbol): Option[String] =
      val params = owner.typeSymbol.primaryConstructor.paramSymss.flatten
      params.find(_.name == fieldSym.name) match
        case Some(p) => annotationName(p).orElse(Some(p.name))
        case None    => None // drop synthetic hops like .value/.get/etc.

    val body = unwrapLambdaBody(pick.asTerm)
    val pairs = collectSelects(body)
    val names = pairs.flatMap { case (own, sym) => segment(own, sym) }

    if names.isEmpty then
      report.errorAndAbort(
        s"MongoPath: expected a simple case-class field selection like '_.field' or '_.nested.field'.\n" +
          s"- For Option fields, import 'io.github.mbannour.fields.MongoPath.syntax.?' and use '_.opt.?.field'.\n" +
          s"- Paths are validated at compile time to prevent runtime failures in Filters/Updates/Deletes/etc."
      )

    Expr(names.mkString("."))
  end ofImpl
end MongoPath
