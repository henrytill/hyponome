/*
 * Copyright 2017 Henry Till
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hyponome.json

import io.circe._
import io.circe.generic.extras._
import shapeless._
import shapeless.labelled.{FieldType, field}

/**
  * References:
  * - [[http://immutables.pl/2017/02/25/customizing-circes-auto-generic-derivation/]]
  * - [[https://stackoverflow.com/questions/37011894/circe-instances-for-encoding-decoding-sealed-trait-instances-of-arity-0/37015184]]
  */
private[json] object auto extends AutoDerivation {

  implicit val configuration: Configuration =
    Configuration.default.withDefaults.withDiscriminator("type")

  trait IsEnum[C <: Coproduct] {
    def to(c: C): String
    def from(s: String): Option[C]
  }

  object IsEnum {

    implicit val cnilIsEnum: IsEnum[CNil] =
      new IsEnum[CNil] {
        def to(c: CNil): String           = sys.error("impossible")
        def from(s: String): Option[CNil] = None
      }

    @SuppressWarnings(Array("org.wartremover.warts.ExplicitImplicitTypes"))
    implicit def cconsIsEnum[K <: Symbol, H <: Product, T <: Coproduct](implicit withK: Witness.Aux[K],
                                                                        withH: Witness.Aux[H],
                                                                        gen: Generic.Aux[H, HNil],
                                                                        tie: IsEnum[T]): IsEnum[FieldType[K, H] :+: T] =
      new IsEnum[FieldType[K, H] :+: T] {
        def to(c: FieldType[K, H] :+: T): String =
          c match {
            case Inl(h) => withK.value.name
            case Inr(t) => tie.to(t)
          }
        @SuppressWarnings(Array("org.wartremover.warts.Equals"))
        def from(s: String): Option[FieldType[K, H] :+: T] =
          if (s == withK.value.name)
            Some(Inl(field[K](withH.value)))
          else
            tie.from(s).map(Inr(_))
      }
  }

  implicit def encodeEnum[A, C <: Coproduct](implicit gen: LabelledGeneric.Aux[A, C], rie: IsEnum[C]): Encoder[A] =
    Encoder[String].contramap[A](a => rie.to(gen.to(a)))

  implicit def decodeEnum[A, C <: Coproduct](implicit gen: LabelledGeneric.Aux[A, C], rie: IsEnum[C]): Decoder[A] =
    Decoder[String].emap(s => rie.from(s).map(gen.from).toRight("enum"))

}
