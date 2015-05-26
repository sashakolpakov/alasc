package net.alasc
package math
package enum

import scala.annotation.tailrec

import scala.collection.BitSet

import spire.algebra.Order
import spire.algebra.partial.RightPartialAction
import spire.syntax.partialAction._
import spire.syntax.cfor._

import net.alasc.algebra._

/** Describes a sequence whose elements can be ordered. */
trait EnumerableOrdered[T, A] extends EnumerableSequence[T, A] {
  import collection.immutable.{BitSet, SortedMap}
  implicit def A: Order[A]
  def groups(t: T): SortedMap[A, Set[Int]] = {
    import spire.compat._
    val sz = size(t)
    @tailrec def build(currentMap: SortedMap[A, BitSet], k: Int): SortedMap[A, BitSet] =
      if (k >= sz) currentMap else {
        val a = element(t, k)
        currentMap.get(a) match {
          case Some(bitset) => build(currentMap.updated(a, bitset + k), k + 1)
          case None => build(currentMap + (a -> BitSet(k)), k + 1)
        }
      }
    build(SortedMap.empty[A, BitSet], 0)
  }
  override def partition(t: T): Domain#Partition = Domain.Partition(groups(t).values.toSeq: _*)
}

final class EnumerableOrderedSeq[A](implicit val A: Order[A]) extends EnumerableOrdered[Seq[A], A] {
  def size(t: Seq[A]) = t.size
  def element(t: Seq[A], k: Int) = t(k)
}

final class EnumerableOrderedSetInt[S <: Set[Int]](val domainSize: Int) extends EnumerableOrdered[S, Boolean] {
  implicit def A: Order[Boolean] = spire.std.boolean.BooleanStructure.reverse
  def element(t: S, idx: Int): Boolean = t(idx)
  def size(t: S): Int = domainSize
}

object EnumerableOrdered {
  implicit def seqWithOrder[A: Order]: EnumerableOrdered[Seq[A], A] = new EnumerableOrderedSeq[A]
  def setInt[S <: Set[Int]](domainSize: Int): EnumerableOrdered[S, Boolean] = new EnumerableOrderedSetInt[S](domainSize)
}