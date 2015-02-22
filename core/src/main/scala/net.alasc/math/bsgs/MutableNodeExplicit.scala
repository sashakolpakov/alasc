package net.alasc.math
package bsgs

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.Random

import spire.syntax.action._
import spire.syntax.group._
import spire.syntax.eq._
import spire.syntax.cfor._

import net.alasc.algebra._
import net.alasc.util._
import net.alasc.ptrcoll
import ptrcoll.syntax.all._

import debox.external._

final class MutableNodeExplicit[P](
  var beta: Int,
  var transversal: SpecKeyMap[Int, P],
  var transversalInv: SpecKeyMap[Int, P],
  var nOwnGenerators: Int,
  var ownGeneratorsArray: Array[P],
  var ownGeneratorsArrayInv: Array[P],
  var prev: MutableStartOrNode[P] = null,
  var next: Chain[P] = null)(implicit val action: FaithfulPermutationAction[P]) extends MutableNode[P] {

  def ownGenerator(i: Int): P = ownGeneratorsArray(i)
  def ownGeneratorInv(i: Int): P = ownGeneratorsArrayInv(i)

  def orbitSize = transversal.size
  def inOrbit(b: Int) = transversal.contains(b)
  def foreachOrbit(f: Int => Unit): Unit = transversal.foreachKey(f)
  def orbitIterator = transversal.keysIterator

  def foreachU(f: P => Unit) = transversal.foreachValue(f)
  def uPair(b: Int) = InversePair(transversal(b), transversalInv(b))
  def u(b: Int) = transversal(b)
  def uInv(b: Int) = transversalInv(b)
  def iterable = new Iterable[(Int, InversePair[P])] {
    override def stringPrefix = "Iterable"
    def iterator = orbitIterator.map(b => (b, uPair(b)))
  }
  def randomU(rand: Random): P = u(randomOrbit(rand))

  protected def addTransversalElement(b: Int, u: P, uInv: P): Unit = {
    transversal.update(b, u)
    transversalInv.update(b, uInv)
  }

  protected[bsgs] def addToOwnGenerators(newGen: P, newGenInv: P)(implicit ev: FiniteGroup[P], ct: ClassTag[P]) = {
    if (nOwnGenerators == ownGeneratorsArray.length) {
      ownGeneratorsArray = arrayGrow(ownGeneratorsArray)
      ownGeneratorsArrayInv = arrayGrow(ownGeneratorsArrayInv)
    }
    ownGeneratorsArray(nOwnGenerators) = newGen
    ownGeneratorsArrayInv(nOwnGenerators) = newGenInv
    nOwnGenerators += 1
  }

  protected[bsgs] def addToOwnGenerators(newGens: Iterable[P], newGensInv: Iterable[P])(implicit ev: FiniteGroup[P], ct: ClassTag[P]) = {
    val it = newGens.iterator
    val itInv = newGensInv.iterator
    while (it.hasNext) {
      val g = it.next
      val gInv = itInv.next
      addToOwnGenerators(g, gInv)
    }
  }

  import mutable.ArrayBuffer

  /** Remove redundant generators from this node generators. */
  protected[bsgs] def removeRedundantGenerators: Unit = {}

  /*{
    /* Tests if the orbit stays complete after removing each generator successively. 
     * The redundant generators are removed from the
     * end of the `ownGeneratorsPairs`, the non-redundant are swapped at its beginning, 
     * at the position indicated by `swapHere`.
     */
    val os = orbitSize
    var swapHere = 0
    var ogpLength = ownGenerators.length
    while (swapHere < ogpLength) {
      val newOrbit = MutableBitSet(beta)
      var toCheck = MutableBitSet.empty
      var newAdded = MutableBitSet.empty
      @inline def swapBitsets: Unit = {
        var temp = toCheck
        toCheck = newAdded
        newAdded = temp
        newAdded.clear
      }
      {
        var j = 0
        while (j < ogpLength - 1) {
          val b = beta <|+| ownGenerators(j)
          newOrbit += b
          newAdded += b
          j += 1
        }
      }
      swapBitsets
      while (toCheck.nonEmpty) {
        toCheck.foreachFast { c =>
          var j = 0
          while (j < ogpLength - 1) {
            val cg = c <|+| ownGenerators(j)
            if (!newOrbit.contains(cg)) {
              newOrbit += cg
              newAdded += cg
            }
            j += 1
          }
          next.strongGeneratingSet.foreach { g =>
            val cg = c <|+| g
            if (!newOrbit.contains(cg)) {
              newOrbit += cg
              newAdded += cg
            }
          }
        }
        swapBitsets
      }
      if (newOrbit.size == os)
        ogpLength -= 1
      else {
        var temp = ownGenerators(swapHere)
        ownGenerators(swapHere) = ownGenerators(ogpLength - 1)
        ownGenerators(ogpLength - 1) = temp
        temp = ownGeneratorsInv(swapHere)
        ownGeneratorsInv(swapHere) = ownGeneratorsInv(ogpLength - 1)
        ownGeneratorsInv(ogpLength - 1) = temp
        swapHere += 1
      }
    }
    ownGenerators.reduceToSize(ogpLength)
    ownGeneratorsInv.reduceToSize(ogpLength)
  }*/

  protected[bsgs] def bulkAdd(beta: debox.Buffer[Int], u: ArrayBuffer[P], uInv: ArrayBuffer[P])(implicit ev: FiniteGroup[P]) = {
    cforRange(0 until beta.length) { i =>
      addTransversalElement(beta(i), u(i), uInv(i))
    }
  }

  protected[bsgs] def updateTransversal(newGen: P, newGenInv: P)(implicit ev: FiniteGroup[P]) = {
    var toCheck = ptrcoll.sets.BitSSet.empty[Int]
    val toAddBeta = debox.Buffer.empty[Int]
    val toAddU = ArrayBuffer.empty[P]
    val toAddUInv = ArrayBuffer.empty[P]
    foreachOrbit { b =>
      val newB = b <|+| newGen
      if (!inOrbit(newB)) {
        val newU = u(b) |+| newGen
        val newUInv = newGenInv |+| uInv(b)
        toAddBeta += newB
        toAddU += newU
        toAddUInv += newUInv
        toCheck += newB
      }
    }
    bulkAdd(toAddBeta, toAddU, toAddUInv)
    toAddBeta.clear
    toAddU.clear
    toAddUInv.clear
    while (!toCheck.isEmpty) {
      val newAdded = ptrcoll.sets.BitSSet.empty[Int]
      val iter = toCheck
      import iter.PtrTC
      var ptr = iter.pointer
      while (ptr.hasAt) {
        val b = ptr.at
        @tailrec def rec(current: Chain[P]): Unit = current match {
          case node: Node[P] =>
            cforRange(0 until node.nOwnGenerators) { i =>
              val g = node.ownGenerator(i)
              val gInv = node.ownGeneratorInv(i)
              val newB = b <|+| g
              if (!inOrbit(newB)) {
                val newU = u(b) |+| g
                val newUInv = gInv |+| uInv(b)
                toAddBeta += newB
                toAddU += newU
                toAddUInv += newUInv
                newAdded += newB
              }
            }
            rec(node.next)
          case _: Term[P] =>
        }
        rec(this)
        ptr = ptr.next
      }
      bulkAdd(toAddBeta, toAddU, toAddUInv)
      toAddBeta.clear
      toAddU.clear
      toAddUInv.clear
      toCheck = newAdded
    }
  }

  protected[bsgs] def updateTransversal(newGen: Iterable[P], newGenInv: Iterable[P])(implicit ev: FiniteGroup[P]) = {
    var toCheck = ptrcoll.sets.BitSSet.empty[Int]
    val toAddBeta = debox.Buffer.empty[Int]
    val toAddU = ArrayBuffer.empty[P]
    val toAddUInv = ArrayBuffer.empty[P]
    foreachOrbit { b =>
      val gIt = newGen.iterator
      val gInvIt = newGenInv.iterator
      while (gIt.hasNext) {
        val g = gIt.next
        val gInv = gInvIt.next
        val newB = b <|+| g
        if (!inOrbit(newB)) {
          val newU = u(b) |+| g
          val newUInv = gInv |+| uInv(b)
          toAddBeta += newB
          toAddU += newU
          toAddUInv += newUInv
          toCheck += newB
        }
      }
    }
    bulkAdd(toAddBeta, toAddU, toAddUInv)
    toAddBeta.clear
    toAddU.clear
    toAddUInv.clear
    while (!toCheck.isEmpty) {
      val newAdded = ptrcoll.sets.BitSSet.empty[Int]
      val iter = toCheck
      import iter.PtrTC
      var ptr = iter.pointer
      while (ptr.hasAt) {
        val b = ptr.at
        @tailrec def rec(current: Chain[P]): Unit = current match {
          case node: Node[P] =>
            cforRange(0 until node.nOwnGenerators) { i =>
              val g = node.ownGenerator(i)
              val gInv = node.ownGeneratorInv(i)
              val newB = b <|+| g
              if (!inOrbit(newB)) {
                val newU = u(b) |+| g
                val newUInv = gInv |+| uInv(b)
                toAddBeta += newB
                toAddU += newU
                toAddUInv += newUInv
                newAdded += newB
              }
            }
            rec(node.next)
          case _: Term[P] =>
        }
        rec(this)
        ptr = ptr.next
      }
      bulkAdd(toAddBeta, toAddU, toAddUInv)
      toAddBeta.clear
      toAddU.clear
      toAddUInv.clear
      toCheck = newAdded
    }
  }

  protected[bsgs] def conjugate(g: P, gInv: P)(implicit ev: FiniteGroup[P], ct: ClassTag[P]) = {
    beta = beta <|+| g
    val newTransversal = SpecKeyMap.empty[Int, P]
    val newTransversalInv = SpecKeyMap.empty[Int, P]
    transversal.foreachKey { k =>
      val newG: P = gInv |+| transversal(k) |+| g
      val newGInv: P = newG.inverse
      val newB = k <|+| g
      newTransversal.update(newB, newG)
      newTransversalInv.update(newB, newGInv)
    }
    transversal = newTransversal
    transversalInv = newTransversalInv
    cforRange(0 until nOwnGenerators) { i =>
      ownGeneratorsArray(i) = gInv |+| ownGeneratorsArray(i) |+| g
      ownGeneratorsArrayInv(i) = gInv |+| ownGeneratorsArrayInv(i) |+| g
    }
  }
}

class MutableNodeExplicitBuilder[P] extends NodeBuilder[P] {
  def standaloneClone(node: Node[P])(implicit algebra: FiniteGroup[P], classTag: ClassTag[P]) = node match {
    case mne: MutableNodeExplicit[P] =>
      new MutableNodeExplicit(mne.beta, mne.transversal.copy, mne.transversalInv.copy, mne.nOwnGenerators, mne.ownGeneratorsArray.clone, mne.ownGeneratorsArrayInv.clone)(mne.action)
    case _ => ??? /* TODO
      val newTransversal = SpecKeyMap.fromIterable[Int, InversePair[P]](node.iterable)
      val newOwnGeneratorsPairs = mutable.ArrayBuffer.empty[InversePair[P]] ++= node.ownGeneratorsPairs
      new MutableNodeExplicit(node.beta, newTransversal, newOwnGeneratorsPairs)(node.action)
                   */
  }
  def standalone(beta: Int)(implicit action: FaithfulPermutationAction[P], algebra: FiniteGroup[P], classTag: ClassTag[P]) =
    new MutableNodeExplicit(beta,
      SpecKeyMap(beta -> FiniteGroup[P].id),
      SpecKeyMap(beta -> FiniteGroup[P].id),
      0,
      Array.empty[P],
      Array.empty[P])
}
