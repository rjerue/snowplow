/*
 * Copyright (c) 2012-2019 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.enrich.common
package loaders

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.option._
import cats.syntax.validated._
import com.snowplowanalytics.snowplow.badrows._
import com.snowplowanalytics.snowplow.badrows.CPFormatViolationMessage._
import com.snowplowanalytics.snowplow.badrows.Failure.CPFormatViolation
import com.snowplowanalytics.snowplow.badrows.Payload.RawPayload
import com.snowplowanalytics.iglu.core._
import org.specs2.mutable.Specification
import org.specs2.matcher.{DataTables, ValidatedMatchers}

import SpecHelpers._

object LoaderSpec {
  val loader = new Loader[String] {
    // Make our trait whole
    override def toCP(line: String): ValidatedNel[BadRow, Option[CollectorPayload]] =
      BadRow(
        CPFormatViolation(Instant.now(), "test", FallbackCPFormatViolationMessage("FAIL")),
        RawPayload(line),
        Processor("sce", "1.0.0")
      ).invalidNel
  }
}

class LoaderSpec extends Specification with DataTables with ValidatedMatchers {
  import LoaderSpec._

  "getLoader" should {
    "return the CloudfrontLoader" in {
      Loader.getLoader("cloudfront") must beRight(CloudfrontLoader)
    }

    "return the CljTomcatLoader" in {
      Loader.getLoader("clj-tomcat") must beRight(CljTomcatLoader)
    }

    "return the ThriftLoader" in {
      Loader.getLoader("thrift") must beRight(ThriftLoader)
    }

    "return the NDJSON loader" in {
      Loader.getLoader("ndjson/example.test/v1") must beRight(NdjsonLoader("example.test/v1"))
    }
  }

  "toCollectorPayload" should {
    "turn bad rows into sd" in {
      val schemaKey = SchemaKey(
        "com.snowplowanalytics.snowplow.badrows",
        "collector_payload_format_violation",
        "jsonschema",
        SchemaVer.Full(1, 0, 0)
      )
      LoaderSpec.loader.toCollectorPayload("ah", Processor("sce", "1.0.0")) must beInvalid.like {
        case NonEmptyList(
            SelfDescribingData(
              sk,
              BadRow(
                CPFormatViolation(_, "test", FallbackCPFormatViolationMessage("FAIL")),
                RawPayload("ah"),
                Processor("sce", "1.0.0")
              )
            ),
            List()
            ) =>
          sk must_== schemaKey
      }
    }
  }

  "extractGetPayload" should {
    val Encoding = UTF_8
    // TODO: add more tests
    "return a Success-boxed NonEmptyList of NameValuePairs for a valid or empty querystring" in {

      "SPEC NAME" || "QUERYSTRING" | "EXP. NEL" |
        "Simple querystring #1" !! "e=pv&dtm=1376487150616&tid=483686".some ! toNameValuePairs(
          "e" -> "pv",
          "dtm" -> "1376487150616",
          "tid" -> "483686"
        ) |
        "Simple querystring #2" !! "page=Celestial%2520Tarot%2520-%2520Psychic%2520Bazaar&vp=1097x482&ds=1097x1973".some ! toNameValuePairs(
          "page" -> "Celestial%20Tarot%20-%20Psychic%20Bazaar",
          "vp" -> "1097x482",
          "ds" -> "1097x1973"
        ) |
        "Superfluous ? ends up in first param's name" !! "?e=pv&dtm=1376487150616&tid=483686".some ! toNameValuePairs(
          "?e" -> "pv",
          "dtm" -> "1376487150616",
          "tid" -> "483686"
        ) |
        "Empty querystring" !! None ! toNameValuePairs() |> { (_, qs, expected) =>
        {
          loader.parseQuerystring(qs, Encoding) must beRight(expected)
        }
      }
    }

    // TODO: test invalid querystrings
  }
}