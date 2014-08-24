package net.alasc.math
package bsgs
package algorithms

import scala.annotation.tailrec
import scala.reflect.ClassTag

import spire.algebra.Order
import spire.syntax.groupAction._
import spire.syntax.group._
import spire.math.Sorting

import net.alasc.algebra.{PermutationAction, Subgroup}
import net.alasc.syntax.check._
import net.alasc.syntax.subgroup._
import net.alasc.util._

trait SubgroupTest[P] extends AnyRef {
  def test(b: Int, orbitImage: Int, currentG: P, node: Node[P])(implicit action: PermutationAction[P]): RefOption[SubgroupTest[P]]
}

class TrivialSubgroupTest[P] extends SubgroupTest[P] {
  def test(b: Int, orbitImage: Int, currentG: P, node: Node[P])(implicit action: PermutationAction[P]) = RefSome(this)
}

trait SubgroupSearch[P] {
  def generalSearch(givenChain: Chain[P], predicate: P => Boolean, givenTest: SubgroupTest[P])(
    implicit action: PermutationAction[P]): Iterator[P]

  def subgroupSearch(givenChain: Chain[P], predicate: P => Boolean, test: SubgroupTest[P])(
    implicit action: PermutationAction[P]): MutableChain[P]

  def intersection(givenChain1: Chain[P], givenChain2: Chain[P])(implicit action: PermutationAction[P]): MutableChain[P]
}

trait SubgroupSearchImpl[P] extends Orders[P] with SchreierSims[P] with BaseChange[P] {
  def generalSearch(givenChain: Chain[P], predicate: P => Boolean, givenTest: SubgroupTest[P])(
    implicit action: PermutationAction[P]) : Iterator[P] = {
    val chain = withAction(givenChain, action)
    val bo = baseOrder(chain.base)
    def rec(currentChain: Chain[P], currentG: P, currentTest: SubgroupTest[P]): Iterator[P] = currentChain match {
      case node: Node[P] =>
        val sortedOrbit = node.orbit.toSeq.sorted(Order.ordering(imageOrder(bo, currentG)))
        for {
          b <- sortedOrbit.iterator
          orbitImage = b <|+| currentG
          newTestOpt = currentTest.test(b, orbitImage, currentG, node) if newTestOpt.nonEmpty
          newTest = newTestOpt.get
          newG = node.u(b) |+| currentG
          g <- rec(node.next, newG, newTest)
        } yield g
      case _: Term[P] =>
        if (predicate(currentG)) Iterator(currentG) else Iterator.empty
    }
    rec(givenChain, algebra.id, givenTest)
  }

  case class SubgroupSearchResult(restartFrom: Int, levelCompleted: Int)

  def subgroupSearch(givenChain: Chain[P], predicate: P => Boolean, test: SubgroupTest[P])(
    implicit action: PermutationAction[P]): MutableChain[P] = {
    val chain = withAction(givenChain, action)
    val bo = baseOrder(chain.base)
    val length = givenChain.nodesNext.size
    val orbits = givenChain.nodesNext.map(_.orbit.toArray).toArray
    val scratch = new Array[Int](orbits.map(_.length).max)
    val subgroupChain = emptyChainWithBase(givenChain.base)
    def rec(level: Int, levelCompleted: Int, currentChain: Chain[P], currentSubgroup: Chain[P], currentG: P, currentTest: SubgroupTest[P]): SubgroupSearchResult = (currentChain, currentSubgroup) match {
      case (_: Term[P], _) =>
        if (predicate(currentG) && !currentG.isId) {
          insertGenerators(subgroupChain, Iterable(currentG))
          SubgroupSearchResult(levelCompleted - 1, levelCompleted)
        } else
          SubgroupSearchResult(level - 1, levelCompleted)
      case (node: Node[P], IsMutableNode(subgroupNode)) =>
        var newLevelCompleted = levelCompleted
        val orbit = orbits(level)
        Sorting.sort(orbit)(imageOrder(bo, currentG), implicitly[ClassTag[Int]])
        var sPrune = orbit.length
        var n = orbit.length
        var i = 0
        while (i < n) {
          val deltaP = orbit(i)
          val delta = deltaP <|+| currentG
          val newTestOpt = currentTest.test(deltaP, delta, currentG, node)
          if (newTestOpt.nonEmpty) {
            val newTest = newTestOpt.get
            val newG = node.u(deltaP) |+| currentG
            if (sPrune < subgroupNode.orbitSize)
              return SubgroupSearchResult(level - 1, level)
            val SubgroupSearchResult(subRestartFrom, subLevelCompleted) = rec(level + 1, newLevelCompleted, node.next, subgroupNode.next, newG, newTest)
            newLevelCompleted = subLevelCompleted
            if (subRestartFrom < level)
              return SubgroupSearchResult(subRestartFrom, newLevelCompleted)
            sPrune -= 1
          }
          i += 1
        }
        SubgroupSearchResult(level - 1, level)
      case _ => sys.error("Invalid argument")
    }
    val SubgroupSearchResult(restartFrom, levelCompleted) = rec(0, length, givenChain, subgroupChain.start.next, algebra.id, test)
    assert(levelCompleted == 0)
    subgroupChain
  }

  def intersection(givenChain1: Chain[P], givenChain2: Chain[P])(implicit action: PermutationAction[P]): MutableChain[P] =
    if (givenChain1.length < givenChain2.length)
      intersection(givenChain2, givenChain1)
    else {
      val chain1 = withAction(givenChain1, action)
      val chain2 = mutableCopyWithAction(givenChain2, action)
      changeBase(chain2, chain1.base)
      class IntersectionTest(level: Int, chain2: Chain[P], prev2: P) extends SubgroupTest[P] {
        def test(b: Int, orbitImage: Int, currentG: P, node: Node[P])(implicit action: PermutationAction[P]): RefOption[IntersectionTest] = {
          val b2 = orbitImage <|+| (prev2.inverse)
          val node2 = chain2.asInstanceOf[Node[P]]
          if (node2.inOrbit(b2))
            RefSome(new IntersectionTest(level + 1, node2.next, node2.u(b2) |+| prev2))
          else
            RefNone
        }
      }
      subgroupSearch(chain1, g => chain2.start.next.contains(g), new IntersectionTest(0, chain2.start.next, algebra.id))
    }
}