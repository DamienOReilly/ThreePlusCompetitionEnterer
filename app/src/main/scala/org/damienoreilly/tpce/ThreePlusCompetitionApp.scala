package org.damienoreilly.tpce

import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

object ThreePlusCompetitionApp extends IOApp with StrictLogging {

  override def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource
      .use(start)
      .as(ExitCode.Success)

  private def start(client: Client[IO]) = {
    implicit val c: Client[IO] = client

    import pureconfig.module.http4s._
    import pureconfig.generic.auto._
    val config = pureconfig.loadConfigOrThrow[AppConfig]

    val service = new CompetitionEntererService(config)

    service.enterCompetitions.value.flatMap(
      _.fold(
        err => IO.raiseError(ThreePlusEntererException(handleError(err))),
        suc => handleCompetitionResults(suc)
      )
    )
  }

  private def handleCompetitionResults(suc: CompetitionResults) = {
    suc
      .traverse(compAndResult => compAndResult._2.value.map(comp => (compAndResult._1, comp)))
      .map(
        _.map {
          case (c: Competition, Left(e))  => s"Entering competition [${c.id}] - ${c.title} failed ${handleError(e)}"
          case (c: Competition, Right(_)) => s"Successfully entered ${c.title}"
        }
      )
      .flatMap(x => IO(logger.info(x.mkString("\n"))))
  }

  private def handleError(e: ThreePlusError) = {
    "Failed, reason " + {
      e match {
        case e: RequestError             => s"${e.error_description}, ${e.error}"
        case e: FatalError               => s"${e.message}"
        case e: CompetitionEnteringError => s"${e.message}"
        case e: UnknownResponse          => s"${e.reason}"
      }
    }
  }
}
