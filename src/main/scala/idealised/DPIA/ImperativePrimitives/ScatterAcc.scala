package idealised.DPIA.ImperativePrimitives

import idealised.DPIA.Phrases._
import idealised.DPIA.Semantics.OperationalSemantics._
import idealised.DPIA.Types._
import idealised.DPIA._

import scala.xml.Elem

final case class ScatterAcc(n: Nat,
                            dt: DataType,
                            idxF: Phrase[ExpType -> ExpType],
                            array: Phrase[AccType])
  extends AccPrimitive
{
  override def `type`: AccType =
    (n: Nat) -> (dt: DataType) ->
      (idxF :: t"exp[idx($n)] -> exp[idx($n)]") ->
        (array :: acc"[$n.$dt]") ->
          acc"[$n.$dt]"

  override def eval(s: Store): AccIdentifier = ???

  override def prettyPrint: String =
    s"(scatterAcc ${PrettyPhrasePrinter(idxF)} ${PrettyPhrasePrinter(array)})"

  override def xmlPrinter: Elem =
    <scatterAcc n={ToString(n)} dt={ToString(dt)}>
      <idxF>{Phrases.xmlPrinter(idxF)}</idxF>
      <input>{Phrases.xmlPrinter(array)}</input>
    </scatterAcc>

  override def visitAndRebuild(f: VisitAndRebuild.Visitor): Phrase[AccType] =
    ScatterAcc(f(n), f(dt), VisitAndRebuild(idxF, f), VisitAndRebuild(array, f))
}