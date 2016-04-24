package net.alasc.std

import scala.reflect.ClassTag

import spire.algebra.partial._
import spire.syntax.action._
import spire.syntax.cfor._
import spire.util.Opt

import net.alasc.algebra._
import net.alasc.syntax.permutationAction._

class ArrayPermutationAction[A: ClassTag, P: PermutationAction] extends PartialAction[Array[A], P] {

  override def actlIsDefined(p: P, s: Array[A]) = p.largestMovedPoint.getOrElseFast(-1) < s.length
  override def actrIsDefined(s: Array[A], p: P) = p.largestMovedPoint.getOrElseFast(-1) < s.length

  def partialActl(p: P, s: Array[A]): Opt[Array[A]] =
    if (p.largestMovedPoint.getOrElseFast(-1) >= s.length) Opt.empty[Array[A]] else {
      val b = new Array[A](s.length)
      cforRange(0 until s.length) { i =>
        b(i) = s(i <|+| p)
      }
      Opt(b)
    }

  def partialActr(s: Array[A], p: P): Opt[Array[A]] =
    if (p.largestMovedPoint.getOrElseFast(-1) >= s.length) Opt.empty[Array[A]] else {
      val b = new Array[A](s.length)
      cforRange(0 until s.length) { i =>
        b(i <|+| p) = s(i)
      }
      Opt(b)
    }
}

trait ArrayInstances0 {
  implicit def ArrayPermutationAction[A: ClassTag, P: PermutationAction]: PartialAction[Array[A], P] = new ArrayPermutationAction[A, P]
}

trait ArrayInstances extends ArrayInstances0