package idealised.DPIA.FunctionalPrimitives

import idealised.DPIA.Compilation.{TranslationContext, TranslationToImperative}
import idealised.DPIA.DSL.{λ, _}
import idealised.DPIA.Phrases.{ExpPrimitive, Phrase, VisitAndRebuild}
import idealised.DPIA.Semantics.OperationalSemantics.{Data, Store}
import idealised.DPIA.Types.{AccType, CommType, DataType, ExpType, _}
import idealised.DPIA.{->:, Nat, Phrases, _}

import scala.language.reflectiveCalls
import scala.xml.Elem

// this takes n many elements from an array of n + m elements
final case class Take(n: Nat,
                      m: Nat,
                      w: AccessType,
                      dt: DataType,
                      array: Phrase[ExpType])
  extends ExpPrimitive {

  override val t: ExpType =
    (n: Nat) ->: (m: Nat) ->: (w: AccessType) ->: (dt: DataType) ->:
      (array :: exp"[${n + m}.$dt, $w]") ->: exp"[$n.$dt, $w]"

  override def eval(s: Store): Data = ???

  override def visitAndRebuild(fun: VisitAndRebuild.Visitor): Phrase[ExpType] = {
    Take(fun.nat(n), fun.nat(m), fun.access(w), fun.data(dt), VisitAndRebuild(array, fun))
  }

  override def acceptorTranslation(A: Phrase[AccType])
                                  (implicit context: TranslationContext): Phrase[CommType] = {
    import TranslationToImperative._
    ???
  }

  override def continuationTranslation(C: Phrase[->:[ExpType, CommType]])
                                      (implicit context: TranslationContext): Phrase[CommType] = {
    import TranslationToImperative._
    con(array)(λ(exp"[${n + m}.$dt, $read]")(x => C(Take(n, m, w, dt, x))))
  }

  override def xmlPrinter: Elem =
    <take n={n.toString} m={m.toString} dt={dt.toString}>
      {Phrases.xmlPrinter(array)}
    </take>

  override def prettyPrint: String = s"(take $array)"

}
