package com.example.billing.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Aap ki company ki pehchan. Yahan ek jagah badlein, poori app me badal jayega. */
object Branding {
    /** Aap ki company ka naam (Developed by ...) */
    const val COMPANY = "Zavion Technologies"

    /** Aap ka rabta number. APNA NUMBER YAHAN LIKHEIN */
    const val PHONE = "0316-6993252"

    /** true = bill (PDF) ke aakhir me chhota sa "Software by ..." likha aaye */
    const val SHOW_ON_INVOICE = true

    /**
     * Customer (dukaan) ka naam. Ye Firebase ke shop ke "shopName" se aata hai
     * aur "Licensed to: ..." me dikhta hai. App khud bharti hai, aap ko kuch nahi karna.
     */
    var licensedTo by mutableStateOf("")
}
