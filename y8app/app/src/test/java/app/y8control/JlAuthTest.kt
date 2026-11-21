package app.y8control

import org.junit.Assert.assertTrue
import org.junit.Test

class JlAuthTest {
    @Test
    fun e1MatchesCapturedVector() {
        assertTrue("Cifrario di autenticazione JieLi non corrisponde al vettore reale", JlAuth.selfTest())
    }
}
