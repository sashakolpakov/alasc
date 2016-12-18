package net.alasc.laws

import spire.algebra.{Eq, Group}

import org.scalacheck.{Arbitrary, Gen}

import net.alasc.finite._
import spire.math.max

import Arbitrary.arbitrary

object Grps {

  def genRandomElement[G](grp: Grp[G]): Gen[G] = Gen.parameterized { params => grp.randomElement(params.rng) }

  def genSubgrp[G:Eq:Group:GrpGroup](grp: Grp[G]): Gen[Grp[G]] =
    fromElements(genRandomElement(grp))

  def fromElements[G:Eq:Group:GrpGroup](elements: Gen[G]): Gen[Grp[G]] =
    for {
      n <- Gen.choose(0, 4)
      generators <- Gen.containerOfN[Seq, G](n, elements)
      c <- elements
    } yield Grp(generators: _*).conjugatedBy(c)

  def conjugatedFromElements[G:Eq:Group:GrpGroup](elements: Gen[G], conjugateBy: Gen[G]): Gen[Grp[G]] =
    for {
      grp <- fromElements(elements)
      c <- conjugateBy
    } yield grp.conjugatedBy(c)

  implicit def arbGrp[G:Arbitrary:Eq:Group:GrpGroup](implicit arbSmallG: Arbitrary[Small[G]]): Arbitrary[Grp[G]] =
    Arbitrary(conjugatedFromElements(arbSmallG.arbitrary.map(_.underlying), arbitrary[G]))

  def arbSubgrp[GG <: Grp[G] with Singleton, G:Eq:Group:GrpGroup](implicit witness: shapeless.Witness.Aux[GG]): Arbitrary[Grp[G]] =
    Arbitrary(genSubgrp(witness.value: GG))

  implicit def instances[G:Instances:Eq:Group:GrpGroup]: Instances[Grp[G]] =
    Instances[G].map(Grp(_))

}
