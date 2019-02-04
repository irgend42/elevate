package idealised.DPIA.FunctionalPrimitives

import idealised.DPIA.IntermediatePrimitives.MapSeqI
import idealised.DPIA.Phrases._
import idealised.DPIA.Types._
import idealised.DPIA._

final case class MapSeq(n: Nat,
                        dt1: DataType,
                        dt2: DataType,
                        f: Phrase[ExpType -> ExpType],
                        array: Phrase[ExpType])
  extends AbstractMapLoop(n, dt1, dt2, f, array)
{
  override def makeMap = MapSeq
  override def makeMapI = MapSeqI.apply
}
