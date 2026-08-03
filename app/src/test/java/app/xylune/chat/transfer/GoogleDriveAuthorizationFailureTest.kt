package app.xylune.chat.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveAuthorizationFailureTest {
    @Test
    fun unregisteredConsoleStatusIsRecognized() {
        assertEquals(
            GoogleDriveAuthorizationFailureKind.UNREGISTERED_ON_API_CONSOLE,
            classifyGoogleDriveAuthorizationFailure(
                "8: [8] Unknown error [status=UNREGISTERED_ON_API_CONSOLE].",
            ),
        )
    }

    @Test
    fun cancellationAndOrdinaryErrorsRemainDistinct() {
        assertEquals(
            GoogleDriveAuthorizationFailureKind.CANCELED,
            classifyGoogleDriveAuthorizationFailure("Connection cancelled"),
        )
        assertEquals(
            GoogleDriveAuthorizationFailureKind.OTHER,
            classifyGoogleDriveAuthorizationFailure("Network unavailable"),
        )
    }
}
