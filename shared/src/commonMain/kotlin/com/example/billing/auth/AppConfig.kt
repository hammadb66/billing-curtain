package com.example.billing.auth

/** Yahan sirf apni Firebase ki cheezen bharni hain. */
object AppConfig {
    /** Firebase Console > Realtime Database > Data tab ke upar jo link hai (aakhir me "/" nahi) */
    const val DB_URL = "https://billing-licenses-default-rtdb.asia-southeast1.firebasedatabase.app"

    /** Firebase Console > Project settings (gear) > General > "Web API Key" */
    const val API_KEY = "AIzaSyCsEao-4FGFiGLMpuWAnFwN7GcHmyHUvoA"

    /**
     * Ye aapka Main (Super Admin) email hai.
     * Ye hamesha chalega, chahe Firebase me kuch bhi ho.
     */
    const val DEV_EMAIL = "hammadb193@gmail.com"

    /** Internet na ho to purana "license ON" kitne din tak chalne dena hai */
    const val OFFLINE_GRACE_DAYS = 7L

    /** Software chalte rehne par kitne minute baad dobara license check ho */
    const val RECHECK_MINUTES = 30L

    /**
     * Online mode me bhejna baqi ho (net nahi tha) to kitne second baad dobara koshish. Jab kuch baqi na ho
     * to koi request nahi jati (Firebase ka kharcha nahi).
     */
    const val SYNC_RETRY_SECONDS = 30L

    /**
     * 0 = background me apne aap Firebase se data NAHI mangta (sirf bill banne / edit / delete par, app khulte hi,
     * aur History ke Refresh button par). Kisi shop me dusre laptop ka data jaldi chahiye to 5-10 rakh den.
     */
    const val BACKGROUND_PULL_MINUTES = 0L

    /** Naye shop ke liye default kitne laptops/phones ki ijazat ho */
    const val DEFAULT_MAX_DEVICES = 3

    /**
     * Laptop ki pehchan banane me mix hota hai. Apni marzi ka koi lamba random text likhein.
     * DHYAN: ek dafa likh kar baad me MAT badalna, warna sab laptops "naye" ban jayenge.
     */
    const val APP_SALT = "a26f6539368943eaa5a898d64eda018ff00685bc4cdc42fb8140e8e5381d15a6"

    /**
     * Local saved license status par "mohar" lagane ke liye. Koi lamba random text likhein.
     * Is se database file haath se edit karna mushkil ho jata hai.
     */
    const val LOCAL_SECRET = "b395376a4a9640bba6c088c4ee9a980aa2eaaedbae114b1fb96729be035dfd63"
}