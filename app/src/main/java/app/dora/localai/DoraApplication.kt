package app.dora.localai

import android.app.Application
import app.dora.localai.data.DoraDatabase

class DoraApplication : Application() {
    val database: DoraDatabase by lazy { DoraDatabase.get(this) }
}
