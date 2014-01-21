package net.alasc

import org.scalacheck._
import org.scalatest.FunSuite

object PermGenerators {
  implicit val r = scala.util.Random
  val genPerm = for {
    k <- Gen.choose(1, 20)
  } yield Sym(k).random
  val genPermPair = for {
    k <- Gen.choose(1, 20)
  } yield (Sym(k).random, Sym(k).random)
}

object PermSpec extends Properties("Perm") {
  import PermGenerators._

  property("*/inverse/isIdentity") = Prop.forAll(genPerm) {
    pp => (pp*pp.inverse).isIdentity
  }

  property("inverse") = Prop.forAll(genPerm) {
    pp => pp === pp.inverse.inverse
  }

  property("===") = Prop.forAll(genPerm) { pp => pp === pp }

  property("*/inverse/===") = Prop.forAll(genPermPair) {
    case (p1, p2) => ((p1*p2).inverse) === (p2.inverse)*(p1.inverse)
  }

  property("image/inverse") = Prop.forAll(genPerm) {
    pp => pp.domain.forall( i => pp.inverse.image(i) === pp.invImage(i) )
  }
}

class MurmurHash3Test extends FunSuite {
  test("MurmurHash3 hashCode does not depend on Array number type") {
    val intArray: Array[Int] = Array(1,2,3,100)
    val longArray: Array[Long] = Array(1L,2L,3L,100L)
    val byteArray: Array[Byte] = intArray.map(_.toByte)
    val shortArray: Array[Short] = intArray.map(_.toShort)
    import scala.util.hashing.MurmurHash3.arrayHash
    assert(arrayHash(intArray) == arrayHash(longArray))
    assert(arrayHash(intArray) == arrayHash(byteArray))
    assert(arrayHash(intArray) == arrayHash(shortArray))
  }
}
