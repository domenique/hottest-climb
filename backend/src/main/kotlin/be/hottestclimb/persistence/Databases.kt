package be.hottestclimb.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

data class DatabaseConfig(val jdbcUrl: String, val user: String, val password: String)

object Databases {
    fun connect(config: DatabaseConfig): Database {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            maximumPoolSize = 5
        })
        Flyway.configure().dataSource(dataSource).load().migrate()
        return Database.connect(dataSource)
    }
}
