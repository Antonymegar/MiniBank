package com.megarStudios.bank.app
import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.megarStudios.bank.actors.Bank
import com.megarStudios.bank.actors.PersistentBankAccount.Command
import com.megarStudios.bank.http.BankRouter
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


object BankApp {
  def startHttpServer(bank:ActorRef[Command])(implicit system:ActorSystem[_]):Unit ={
    implicit val ec:ExecutionContext = system.executionContext
    val router = new BankRouter(bank)
    val routes = router.routes

   val httpBindingFuture = Http().newServerAt("localhost", 7000).bind(routes)
    httpBindingFuture.onComplete{
      case Success(binding) =>
      val address= binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
      case Failure(ex) =>
        system.log.error(s"Failed to bind HTTP server, because: $ex")
        system.terminate()
    }
  }
  def main(args: Array[String]):Unit = {
    trait RootCommand
    case class RetrieveBankActor(replyTo: ActorRef[ActorRef[Command]]) extends RootCommand
    val rootBehavior : Behavior[RootCommand]= Behaviors.setup {
      context =>
        val bankActor = context.spawn(Bank() , "bank")

        Behaviors.receiveMessage{
          case RetrieveBankActor(replyTo)=>
            replyTo ! bankActor
            Behaviors.same
        }
    }
     implicit val system:ActorSystem[RootCommand] = ActorSystem(rootBehavior,"BankSystem")
     implicit val timeout: Timeout = Timeout(5.seconds)
     implicit val ec: ExecutionContext = system.executionContext
    val bankActorFuture: Future[ActorRef[Command]] = system.ask(replyTo => RetrieveBankActor(replyTo))
    bankActorFuture.foreach(startHttpServer)
  }

}