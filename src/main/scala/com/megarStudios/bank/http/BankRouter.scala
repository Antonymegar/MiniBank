package com.megarStudios.bank.http
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import com.megarStudios.bank.actors.PersistentBankAccount.Command
import com.megarStudios.bank.actors.PersistentBankAccount.Response
import com.megarStudios.bank.actors.PersistentBankAccount.Response._
import com.megarStudios.bank.actors.PersistentBankAccount.Command._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}



case class BankAccountCreationRequest(user: String, currency: String, balance:Double) {
  def toCommand(replyTo: ActorRef[Response]) : Command = CreateBankAccount(user, currency, balance, replyTo)
}

case class BankAccountUpdateRequest(currency: String, amount:Double) {
  def toCommand(id: String, replyTo: ActorRef[Response]): Command = UpdateBalance(id, currency, amount, replyTo)
}

case class FailureResponse(reason:String)

class BankRouter(bank:ActorRef[Command])(implicit system: ActorSystem[_]) {

  implicit val timeout: Timeout = Timeout(5.seconds)

  def createBankAccount(request: BankAccountCreationRequest): Future[Response] =
    bank.ask(replyTo => request.toCommand(replyTo))

  def getBankAccount(id: String): Future[Response] =
    bank.ask(replyTo => GetBankAccount(id, replyTo))

  def updateBankAccount(id:String, request:BankAccountUpdateRequest):Future[Response] =
  bank.ask(replyTo => request.toCommand(id,replyTo))
  /*
  POST /bank/
  payload: bank account creation request as JSON
  Response:
  201 Created
  Location: /bank/uuid

   */

  val routes =
    pathPrefix("bank") {
      pathEndOrSingleSlash {
        post {
          //parse the payload
          entity(as[BankAccountCreationRequest]) {
            request =>
              /*
                -convert the request into a command for the bank actor
                -send the command to the bank
                -expect a reply
                 */
              onSuccess(createBankAccount(request)) {
                case BankAccountCreatedResponse(id) =>
                  respondWithHeader(Location(s"/bank/$id")) {
                    complete(StatusCodes.Created)
                  }
              }

          }
        }
      } ~ path(Segment) { id =>
        get{
        /*
             -send command to the bank
             -expect a reply

            */
        onSuccess(getBankAccount(id)) {
          case GetBankAccountResponse(Some(account)) =>
            complete(account)
          case GetBankAccountResponse(None) =>
            complete(StatusCodes.NotFound, FailureResponse(s"Bank account $id cannot be found"))
        }
        }~
          put{
            entity(as[BankAccountUpdateRequest]) {
              request =>
                /*
                -transform the request to a command
                -send the command to the bank
                -expect a reply
                 */

                onSuccess(updateBankAccount(id, request)) {
                  case BankAccountBalanceUpdatedResponse(Success(account)) =>
                    complete(account)
                  case BankAccountBalanceUpdatedResponse(Failure(ex)) =>
                    complete(StatusCodes.BadRequest, FailureResponse(s"${ex.getMessage}"))
                }
            }

          }
    }
}
}
