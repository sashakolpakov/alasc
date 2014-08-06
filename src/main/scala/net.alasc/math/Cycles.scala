package net.alasc.math

import scala.language.implicitConversions
import spire.algebra._
import spire.syntax.eq._
import spire.syntax.groupAction._
import spire.syntax.signed._
import net.alasc.algebra._
import scala.runtime.RichInt

import scala.collection.immutable.BitSet

/** Description of a permutation as a product of disjoint cycles in the canonical form.
  * 
  * Canonical form means:
  * 
  * - each disjoint cycle start with its minimal element,
  * - cycles are sorted in the canonical order, i.e. by length first, and then
  *   by first elements.
  */
class Cycles private[alasc](val seq: Seq[Cycle]) {
  override def toString: String = seq.mkString
  def toStringUsing(symbols: Int => String) =
    seq.map(_.toStringUsing(symbols(_))).mkString
  def apply(cycle: Int*) = Cycles.Algebra.op(this, Cycles(cycle: _*))
}

class CyclesPermutation extends BuildablePermutation[Cycles] {
  implicit val seqEq: Eq[Seq[Cycle]] = spire.std.seq.SeqEq[Cycle, Seq]

  def supportMaxElement = Int.MaxValue

  def fromImages(images: Seq[Int]): Cycles = fromSupportAndImages(BitSet(0 until images.size:_*), images(_))
  def fromSupportAndImages(support: BitSet, image: Int => Int): Cycles = {
    @scala.annotation.tailrec def rec(cycles: List[Cycle], remSupport: BitSet): Cycles = 
      remSupport.isEmpty match {
        case true => fromDisjointCycles(cycles)
        case false =>
          val k = remSupport.head
          val newCycle = Cycle.orbit(k, image)
          rec(newCycle :: cycles, remSupport -- newCycle.seq)
      }
    rec(Nil, support)
  }
  def fromDisjointCycles(cycles: Seq[Cycle]) = {
    import spire.compat._
    new Cycles(cycles.filter(_.length > 1).sorted)
  }

  def eqv(x: Cycles, y: Cycles) = x.seq === y.seq

  def id = new Cycles(Seq.empty[Cycle])
  def op(x: Cycles, y: Cycles) = 
    Cycles.Algebra.fromSupportAndImages(support(x) ++ support(y), i => actr(actr(i, x), y))
  def inverse(a: Cycles) = Cycles.Algebra.fromDisjointCycles(a.seq.map(_.inverse))

  def actr(k: Int, g: Cycles) = (k /: g.seq) { case (kIt, cycle) => kIt <|+| cycle }
  def actl(g: Cycles, k: Int) = (k /: g.seq) { case (kIt, cycle) => cycle |+|> kIt }

  def signum(c: Cycles) = (1 /: c.seq) { case (sIt, cycle) => sIt * cycle.signum }

  def plus(c: Cycles, n: Int) = new Cycles(c.seq.map(_ + n))
  def minus(c: Cycles, n: Int) = new Cycles(c.seq.map(_ - n))

  def supportMin(c: Cycles) = c.seq.flatMap(_.seq).reduceOption(_.min(_)).getOrElse(-1)
  def supportMax(c: Cycles) = c.seq.flatMap(_.seq).reduceOption(_.max(_)).getOrElse(-1)
  def support(c: Cycles): BitSet =
    (BitSet.empty /: c.seq) { case (set, cycle) => set union cycle.support }
}

object Cycles {
  implicit val Algebra = new CyclesPermutation
  def apply(seq: Int*) = seq.size match {
    case 0 | 1 => Algebra.id
    case _ => new Cycles(Seq(Cycle(seq: _*)))
  }
  implicit def cycleToCycles(c: Cycle): Cycles = new Cycles(Seq(c))
}