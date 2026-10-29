package be.hottestclimb.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import java.net.URI
import java.net.URLDecoder

data class DatabaseConfig(val jdbcUrl: String, val user: String?, val password: String?) {
    companion object {
        // Hosts like Render and Heroku hand out postgres://user:pass@host/db, which JDBC can't parse.
        fun from(url: String, user: String?, password: String?): DatabaseConfig {
            if (url.startsWith("jdbc:")) return DatabaseConfig(url, user, password)

            val uri = URI(url)
            val port = if (uri.port == -1) "" else ":${uri.port}"
            val query = uri.rawQuery?.let { "?$it" } ?: ""
            val credentials = uri.rawUserInfo?.split(":", limit = 2)?.map(::decode)
            return DatabaseConfig(
                jdbcUrl = "jdbc:postgresql://${uri.host}$port${uri.rawPath}$query",
                user = credentials?.getOrNull(0) ?: user,
                password = credentials?.getOrNull(1) ?: password,
            )
        }

        // URLDecoder would turn a literal '+' in a password into a space.
        private fun decode(value: String) = URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    }
}

object Databases {
    fun connect(config: DatabaseConfig): Database {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            config.user?.let { username = it }
            config.password?.let { password = it }
            maximumPoolSize = 5
        })
        Flyway.configure().dataSource(dataSource).load().migrate()
        return Database.connect(dataSource)
    }
}
