package be.hottestclimb.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DatabaseConfigTest {
    @Test
    fun `turns a Render style connection string into a jdbc url with credentials`() {
        DatabaseConfig.from("postgresql://climb:s3cret@dpg-abc123-a/hottestclimb", user = null, password = null) shouldBe
            DatabaseConfig("jdbc:postgresql://dpg-abc123-a/hottestclimb", "climb", "s3cret")
    }

    @Test
    fun `keeps port and query parameters and accepts the postgres scheme`() {
        DatabaseConfig.from("postgres://climb:s3cret@db.example.com:5433/hottestclimb?sslmode=require", null, null) shouldBe
            DatabaseConfig("jdbc:postgresql://db.example.com:5433/hottestclimb?sslmode=require", "climb", "s3cret")
    }

    @Test
    fun `decodes url encoded credentials`() {
        DatabaseConfig.from("postgresql://climb:p%40ss%2Fw0rd@host/db", null, null).password shouldBe "p@ss/w0rd"
    }

    @Test
    fun `credentials in the url win over separately configured ones`() {
        DatabaseConfig.from("postgresql://climb:s3cret@host/db", user = "other", password = "other").user shouldBe "climb"
    }

    @Test
    fun `passes a jdbc url through and uses the separate credentials`() {
        DatabaseConfig.from("jdbc:postgresql://localhost:5432/hottestclimb", "hottestclimb", "pw") shouldBe
            DatabaseConfig("jdbc:postgresql://localhost:5432/hottestclimb", "hottestclimb", "pw")
    }

    @Test
    fun `a jdbc url without separate credentials leaves them to the url`() {
        DatabaseConfig.from("jdbc:postgresql://host/db?user=climb&password=s3cret", null, null) shouldBe
            DatabaseConfig("jdbc:postgresql://host/db?user=climb&password=s3cret", null, null)
    }
}
