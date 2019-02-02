package fr.gospeak.libs.scalautils.domain

/**
  * Type to signify computation completion when there is no result
  * It's intended to replace Unit which accept anything and may cause some trouble
  */
sealed abstract class Done

case object Done extends Done
