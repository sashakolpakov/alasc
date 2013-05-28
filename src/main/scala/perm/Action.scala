package com.faacets
package perm

import scala.util.Random

trait Action[-SE <: FiniteElementLike, DE <: PermElement[DE]] extends Function1[SE, DE] with HasTeX {
}

case class TrivialAction[E <: PermElement[E]]() extends Action[E, E] {
  def apply(e: E) = e
  def toTeX = TeX("\\text{id}")
}

case class ActionGroup[A <: Action[F, P], 
  G <: FiniteGroup[F],
  F <: FiniteElement[F],
  P <: PermElement[P]](g: G, a: A) extends PermGroup[ActionElement[A, F, P]] {
  type Element = ActionElement[A, F, P]
  def toTeX = a.toTeX + TeX("_{") + a.toTeX + TeX("}")
  def degree = a(g.identity).size
  def compatible(e: Element) = g.compatible(e.f)
  def contains(e: Element) = g.contains(e.f)
  def elements = g.elements.map(ActionElement(_, a))
  def generators = g.generators.map(ActionElement(_, a))
  def identity = ActionElement(g.identity, a)
  def order = g.order
  def random(implicit gen: Random) = ActionElement(g.random, a)
  def fromExplicit(p: Perm) = elements.find(_.explicit === p)
}

case class ActionElement[A <: Action[F, P], F <: FiniteElement[F], P <: PermElement[P]](f: F, a: A) extends PermElement[ActionElement[A, F, P]] {
  type Element = ActionElement[A, F, P]
  def toTeX = a.toTeX + TeX("_{") + f.toTeX + TeX("}")
  def compatible(that: Element) = a == that.a
  def *(that: Element) = {
    require_(compatible(that))
    ActionElement(f*(that.f), a)
  }
  def ===(that: Element) = {
    require_(compatible(that))
    f === that.f
  }
  override def hashCode() = f.hashCode()
  def inverse = ActionElement(f.inverse, a)
  def isIdentity = f.isIdentity
  def explicit = Perm(images)
  def image(k: Dom) = a(f).image(k)
  def invImage(k: Dom) = a(f).invImage(k)
  def images = a(f).images
  def size = a(f).size
}
