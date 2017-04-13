package hyponome.test

import org.scalacheck.{Arbitrary, Gen}

trait Generators {

  val genNEByteArray: Gen[Array[Byte]] = Gen.nonEmptyContainerOf[Array, Byte](Arbitrary.arbitrary[Byte])

  val genNEListOfNEByteArrays: Gen[List[Array[Byte]]] = Gen.nonEmptyContainerOf[List, Array[Byte]](genNEByteArray)
}
