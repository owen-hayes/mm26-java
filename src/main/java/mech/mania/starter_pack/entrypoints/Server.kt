package mech.mania.starter_pack.entrypoints

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import mech.mania.engine.domain.model.CharacterProtos.CharacterDecision
import mech.mania.engine.domain.model.CharacterProtos.DecisionType
import mech.mania.starter_pack.domain.model.GameState
import mech.mania.engine.domain.model.PlayerProtos.PlayerTurn
import mech.mania.engine.domain.model.ProtoFactory
import mech.mania.starter_pack.domain.PlayerStrategy
import mech.mania.starter_pack.domain.Strategy
import mech.mania.starter_pack.domain.memory.MemoryObject
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.net.InetSocketAddress
import java.util.logging.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as SLF4Logger


/**
 * main function for running the server with no onReceive and onSend
 */
fun main(args: Array<String>) {
    val root: SLF4Logger = LoggerFactory.getLogger(SLF4Logger.ROOT_LOGGER_NAME) as SLF4Logger
    root.level = Level.INFO
    Server().startServer(args[0].toInt(), {}, {})
}

class Server {

    private val logger = Logger.getLogger(Server::class.toString())
    private val memory = MemoryObject()
    private val player: Strategy = PlayerStrategy(memory)

    /**
     * Starts a server using a specified port
     * @param port Port to start server on (localhost)
     * @param onReceive callback function that gets called when server receives turn
     * @param onSend callback function that gets called when server sends decision
     * @return non-zero if server fails to start, 0 if server starts properly
     */
    fun startServer(port: Int,
                    onReceive: (turn: PlayerTurn) -> Unit,
                    onSend: (decision: CharacterDecision) -> Unit): Int {
        try {
            // Create server on specified port
            val server = HttpServer.create(InetSocketAddress(port), 0)

            // Add handler to server endpoint which receives PlayerTurn and returns PlayerDecision
            server.createContext("/server") { exchange: HttpExchange ->
                // read in input from server
                // once the turn is parsed, use that turn to call a passed in function
                val turn = PlayerTurn.parseFrom(exchange.requestBody)
                logger.info("Received playerTurn for player: " + turn.playerName + ", turn: " + turn.gameState.stateId)
                onReceive(turn)

                // calculate what to do with turn
                val decision: CharacterDecision
                decision = try {
                    ProtoFactory.CharacterDecision(player.makeDecision(turn.playerName, GameState(turn.gameState)))
                } catch (e: Exception){
                    val buffer: Writer = StringWriter()
                    e.printStackTrace(PrintWriter(buffer))
                    logger.warning("Exception while making decision:\n")
                    logger.warning(buffer.toString())

                    // Default to NONE decision
                    CharacterDecision.newBuilder().setDecisionType(DecisionType.NONE).setIndex(-1).build()
                }
                val size: Long = decision.toByteArray().size.toLong()

                // send back response
                exchange.responseHeaders["Content-Type"] = "application/octet-stream"
                exchange.sendResponseHeaders(200, size)
                decision.writeTo(exchange.responseBody)
                exchange.responseBody.flush()
                exchange.responseBody.close()
                logger.info("Sent playerDecision")
                onSend(decision)
            }

            // Add handler to health endpoint which returns status code of 200
            server.createContext("/health") { exchange: HttpExchange ->
                val message = "200".toByteArray()
                exchange.sendResponseHeaders(200, message.size.toLong())
                exchange.responseBody.write(message)
            }

            // Add handler to shutdown endpoint to close this server
            server.createContext("/shutdown") { exchange: HttpExchange ->
                val message = "200".toByteArray()
                exchange.sendResponseHeaders(200, message.size.toLong())
                exchange.responseBody.write(message)

                // Close MemoryObject Redis connection
                memory.saveAndClose()

                // Wait up to 10 seconds to finish current exchanges,
                // then close this server
                server.stop(10000)
            }

            // Start server
            server.start()
            logger.info("Server started on port $port")
            return 0
        } catch (e: Exception) {
            logger.warning("Server failed to start on $port: ${e.message}")
            return 1
        }
    }
}
