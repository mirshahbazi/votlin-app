package com.votlin.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.application.log
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import org.jetbrains.squash.connection.DatabaseConnection
import org.jetbrains.squash.connection.transaction
import org.jetbrains.squash.definition.*
import org.jetbrains.squash.dialects.h2.H2Connection
import org.jetbrains.squash.expressions.and
import org.jetbrains.squash.expressions.count
import org.jetbrains.squash.expressions.eq
import org.jetbrains.squash.query.groupBy
import org.jetbrains.squash.query.select
import org.jetbrains.squash.query.where
import org.jetbrains.squash.results.get
import org.jetbrains.squash.statements.*
import java.time.LocalDateTime
import kotlin.collections.set
import kotlin.coroutines.CoroutineContext

class Database(application: Application) {
    private val dispatcher: CoroutineContext
    private val connectionPool: HikariDataSource
    private val connection: DatabaseConnection

    init {
        val config = application.environment.config.config("database")
        val url = config.property("connection").getString()
        val poolSize = config.property("poolSize").getString().toInt()
        application.log.info("Connecting to database at '$url'")

        dispatcher = newFixedThreadPoolContext(poolSize, "database-pool")
        val cfg = HikariConfig()
        cfg.jdbcUrl = url
        cfg.maximumPoolSize = poolSize
        cfg.validate()

        connectionPool = HikariDataSource(cfg)

        connection = H2Connection { connectionPool.connection }
        connection.transaction {
            databaseSchema().create(listOf(Users, Favorites, Votes))
        }
    }

    suspend fun validateUser(uuid: String): Boolean = withContext(dispatcher) {
        connection.transaction {
            Users.select { Users.id.count() }.where { Users.uuid eq uuid }.execute().single().get<Int>(0) != 0
        }
    }

    suspend fun createUser(uuid: String, remote: String, timestamp: LocalDateTime): Boolean = withContext(dispatcher) {
        connection.transaction {
            val count = Users.select { Users.id.count() }.where { Users.uuid eq uuid }.execute().single().get<Int>(0)
            if (count == 0) {
                insertInto(Users).values {
                    it[Users.uuid] = uuid
                    it[Users.timestamp] = timestamp.toString()
                    it[Users.remote] = remote
                }.execute()
            }
            count == 0
        }
    }

    suspend fun usersCount(): Int = withContext(dispatcher) {
        connection.transaction {
            Users.select { Users.id.count() }.execute().single().get<Int>(0)
        }
    }

    suspend fun deleteFavorite(uuid: String, sessionId: String): Unit = withContext(dispatcher) {
        connection.transaction {
            deleteFrom(Favorites)
                    .where { (Favorites.uuid eq uuid) and (Favorites.sessionId eq sessionId) }
                    .execute()
        }
    }

    suspend fun createFavorite(uuid: String, sessionId: String) = withContext(dispatcher) {
        connection.transaction {
            val count = Favorites.select { Favorites.id.count() }
                    .where { (Favorites.uuid eq uuid) and (Favorites.sessionId eq sessionId) }
                    .execute().single().get<Int>(0)
            if (count == 0) {
                insertInto(Favorites).values {
                    it[Favorites.uuid] = uuid
                    it[Favorites.sessionId] = sessionId
                }.execute()
            }
            count == 0
        }
    }

    suspend fun changeVote(uuid: String, sessionId: String, rating: Int, timestamp: LocalDateTime): Boolean =
            withContext(dispatcher) {
                connection.transaction {
                    val count = Votes.select { Votes.id.count() }
                            .where { (Votes.uuid eq uuid) and (Votes.sessionId eq sessionId) }
                            .execute().single().get<Int>(0)
                    if (count == 0) {
                        insertInto(Votes).values {
                            it[Votes.uuid] = uuid
                            it[Votes.sessionId] = sessionId
                            it[Votes.rating] = rating
                            it[Votes.timestamp] = timestamp.toString()
                        }.execute()
                        true
                    } else {
                        update(Votes).where { (Votes.uuid eq uuid) and (Votes.sessionId eq sessionId) }.set {
                            it[Votes.rating] = rating
                        }.execute()
                        false
                    }
                }
            }

    suspend fun deleteVote(uuid: String, sessionId: String): Unit = withContext(dispatcher) {
        connection.transaction {
            deleteFrom(Votes)
                    .where { (Votes.uuid eq uuid) and (Votes.sessionId eq sessionId) }
                    .execute()

        }
    }

    suspend fun getVotesSummary(sessionId: String): Map<String, Int> = withContext(dispatcher) {
        connection.transaction {
            val votes = Votes.select(Votes.rating).select { Votes.id.count() }
                    .where { Votes.sessionId eq sessionId }
                    .groupBy(Votes.rating)
                    .execute()
            val map = votes.associateTo(mutableMapOf()) {
                val rating = when (it.get<Int>(0)) {
                    0 -> "soso"
                    1 -> "good"
                    -1 -> "bad"
                    else -> "unknown"
                }
                val count = it.get<Int>(1)
                rating to count
            }
            if ("bad" !in map) map["bad"] = 0
            if ("good" !in map) map["good"] = 0
            if ("soso" !in map) map["soso"] = 0
            map
        }
    }
}

object Users : TableDefinition() {
    val id = integer("id").autoIncrement().primaryKey()
    val uuid = varchar("uuid", 50).index()
    val remote = varchar("remote", 50)
    val timestamp = varchar("timestamp", 50)
}

object Favorites : TableDefinition() {
    val id = integer("id").autoIncrement().primaryKey()
    val uuid = varchar("uuid", 50).index()
    val sessionId = varchar("sessionId", 50)
}

object Votes : TableDefinition() {
    val id = integer("id").autoIncrement().primaryKey()
    val timestamp = varchar("timestamp", 50)
    val uuid = varchar("uuid", 50).index()
    val sessionId = varchar("sessionId", 50).index()
    val rating = integer("rating")
}
