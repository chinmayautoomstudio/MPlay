package com.autoomstudio.mp3studio.data.billing

import com.autoomstudio.mp3studio.data.plan.BillingRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {

    @Test
    fun indianNumbersNormalize() {
        assertEquals("9876543210", PhoneNumber.normalize("98765 43210"))
        assertEquals("9876543210", PhoneNumber.normalize("+91 98765-43210"))
        assertNull(PhoneNumber.normalize("12345"))
        assertNull(PhoneNumber.normalize("+14155551212"))
    }

    @Test
    fun internationalNumbersAcceptE164OrIndian() {
        assertEquals("+14155551212", PhoneNumber.normalizeIntl("+14155551212"))
        assertEquals("9876543210", PhoneNumber.normalizeIntl("+91 9876543210"))
        assertNull(PhoneNumber.normalizeIntl("14155551212"))
        assertEquals("+14155551212", PhoneNumber.normalize("+14155551212", BillingRegion.International))
        assertNull(PhoneNumber.normalize("+14155551212", BillingRegion.India))
    }
}
