package com.syxhen.cardndfixer

import android.app.Application
import com.syxhen.cardndfixer.data.AppPreferencesRepository
import com.syxhen.cardndfixer.diagnostics.DiagnosticLogCollector
import com.syxhen.cardndfixer.dnd.AndroidDndGateway
import com.syxhen.cardndfixer.dnd.DndCycleCoordinator
import com.syxhen.cardndfixer.service.CarCompanionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class CarDndApplication : Application() {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var preferencesRepository: AppPreferencesRepository
        private set

    lateinit var diagnosticLogCollector: DiagnosticLogCollector
        private set

    lateinit var dndGateway: AndroidDndGateway
        private set

    lateinit var companionManager: CarCompanionManager
        private set

    lateinit var dndCycleCoordinator: DndCycleCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = AppPreferencesRepository(applicationContext)
        diagnosticLogCollector = DiagnosticLogCollector(applicationContext)
        dndGateway = AndroidDndGateway(applicationContext)
        companionManager = CarCompanionManager(applicationContext, diagnosticLogCollector)
        dndCycleCoordinator = DndCycleCoordinator(
            dndGateway = dndGateway,
            settingsStore = preferencesRepository,
            scope = applicationScope,
            diagnostics = diagnosticLogCollector,
        )

        applicationScope.launch {
            preferencesRepository.settings
                .map { it.diagnosticLoggingEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    diagnosticLogCollector.setEnabled(enabled)
                }
        }

        diagnosticLogCollector.record(
            "App",
            "onCreate (version=${BuildConfig.VERSION_NAME}, buildType=${BuildConfig.BUILD_TYPE})",
        )
        companionManager.ensureObservingAll()
    }
}
