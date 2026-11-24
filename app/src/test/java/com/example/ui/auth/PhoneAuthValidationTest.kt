package com.example.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PhoneAuthValidationTest {

    @Test
    fun formatE164PhoneNumber_stripsLeadingZeroAndSpaces() {
        assertEquals("+919876543210", formatE164PhoneNumber("+91", "09876 543210"))
        assertEquals("+15551234567", formatE164PhoneNumber("+1", "555-123-4567"))
    }

    @Test
    fun formatE164PhoneNumber_preservesItalyTrunkZeroAndDeduplicatesNanpAreaCode() {
        assertEquals("+390612345678", formatE164PhoneNumber("+39", "06 1234 5678"))
        assertEquals("+12425551234", formatE164PhoneNumber("+1242", "555-1234"))
        assertEquals("+12425551234", formatE164PhoneNumber("+1242", "242-555-1234"))
    }

    @Test
    fun formatE164PhoneNumber_preservesExplicitPlusPrefix() {
        assertEquals("+447911123456", formatE164PhoneNumber("+1", "+44 7911 123456"))
    }

    @Test
    fun isValidE164PhoneNumber_validatesDigitBounds() {
        assertTrue(isValidE164PhoneNumber("+15551234567"))
        assertTrue(isValidE164PhoneNumber("+919876543210"))
        assertTrue(isValidE164PhoneNumber("+2901234"))
        assertFalse(isValidE164PhoneNumber("15551234567"))
        assertFalse(isValidE164PhoneNumber("+12345"))
        assertFalse(isValidE164PhoneNumber("+1234567890123456"))
    }

    @Test
    fun allCountryCodes_coversWorldwideCountriesWithValidIsoAndDialCodes() {
        assertTrue(AllCountryCodes.size >= 230)
        val uniqueIsos = AllCountryCodes.map { it.iso }.toSet()
        assertEquals(AllCountryCodes.size, uniqueIsos.size)

        AllCountryCodes.forEach { country ->
            assertEquals(2, country.iso.length)
            assertTrue(country.dialCode.startsWith("+"))
            assertTrue(country.dialCode.drop(1).all { it.isDigit() })
            assertTrue(country.name.isNotBlank())
            assertFalse(country.flag == "🌐")
        }

        assertEquals("+91", defaultCountryDialCode(Locale("en", "IN")).dialCode)
        assertEquals("+82", defaultCountryDialCode(Locale("ko", "KR")).dialCode)
        assertEquals("+1", defaultCountryDialCode(Locale("en", "ZZ")).dialCode)
    }

    @Test
    fun mapOperationNotAllowedMessage_distinguishesRegionPolicyAndBilling() {
        val regionMsg = mapOperationNotAllowedMessage(
            "This operation is not allowed. [ SMS unable to be sent until this region enabled by the app developer. ]"
        )
        assertTrue(regionMsg.contains("SMS region policy"))

        val billingMsg = mapOperationNotAllowedMessage("BILLING_NOT_ENABLED")
        assertTrue(billingMsg.contains("Blaze billing plan"))

        val genericMsg = mapOperationNotAllowedMessage(null)
        assertTrue(genericMsg.contains("ERROR_OPERATION_NOT_ALLOWED"))
    }
}
