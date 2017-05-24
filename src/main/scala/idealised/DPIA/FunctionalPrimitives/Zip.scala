package idealised.DPIA.FunctionalPrimitives

import idealised.DPIA.Semantics.OperationalSemantics._
import idealised.DPIA.Compilation.RewriteToImperative
import idealised.DPIA.Phrases._
import idealised.DPIA.Semantics.OperationalSemantics
import idealised.DPIA.Types._
import idealised.DPIA.{Phrases, _}
import idealised.DPIA.DSL._
import idealised.DPIA.ImperativePrimitives.{ZipAcc1, ZipAcc2}

import scala.xml.Elem

final case class Zip(n: Nat,
                     dt1: DataType,
                     dt2: DataType,
                     e1: Phrase[ExpType],
                     e2: Phrase[ExpType])
  extends ExpPrimitive {

  override lazy val `type` = exp"[$n.($dt1 x $dt2)]"

  override def typeCheck(): Unit = {
    import TypeChecker._
    (n: Nat) -> (dt1: DataType) -> (dt2: DataType) ->
      (e1 :: exp"[$n.$dt1]") ->
      (e2 :: exp"[$n.$dt2]") ->
      `type`
  }

  override def visitAndRebuild(f: VisitAndRebuild.Visitor): Phrase[ExpType] = {
    Zip(f(n), f(dt1), f(dt2), VisitAndRebuild(e1, f), VisitAndRebuild(e2, f))
  }

  override def eval(s: Store): Data = {
    (OperationalSemantics.eval(s, e1), OperationalSemantics.eval(s, e2)) match {
      case (ArrayData(lhsE), ArrayData(rhsE)) =>
        ArrayData((lhsE zip rhsE) map { p =>
          RecordData(p._1, p._2)
        })

      case _ => throw new Exception("This should not happen")
    }
  }

  override def prettyPrint: String =
    s"(zip ${PrettyPhrasePrinter(e1)} ${PrettyPhrasePrinter(e2)})"

  override def xmlPrinter: Elem =
    <zip n={ToString(n)} dt1={ToString(dt1)} dt2={ToString(dt2)}>
      <lhs type={ToString(ExpType(ArrayType(n, dt1)))}>
        {Phrases.xmlPrinter(e1)}
      </lhs>
      <rhs type={ToString(ExpType(ArrayType(n, dt2)))}>
        {Phrases.xmlPrinter(e2)}
      </rhs>
    </zip>

  override def acceptorTranslation(A: Phrase[AccType]): Phrase[CommandType] = {
    import RewriteToImperative._

    acc(e1)(ZipAcc1(n, dt1, dt2, A)) `;` acc(e2)(ZipAcc2(n, dt1, dt2, A))
  }

  override def continuationTranslation(C: Phrase[ExpType -> CommandType]): Phrase[CommandType] = {
    import RewriteToImperative._

    con(e1)(λ(exp"[$n.$dt1]")(x =>
      con(e2)(λ(exp"[$n.$dt2]")(y =>
        C(Zip(n, dt1, dt2, x, y)) )) ))
  }
}