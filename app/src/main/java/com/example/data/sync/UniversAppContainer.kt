package com.example.data.sync

import android.content.Context
import com.example.R
import com.example.data.local.UniversDao
import com.example.data.local.UniversDatabase
import com.example.data.repository.CallSignalingRepository
import com.example.data.repository.MessagingRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class UniversAppContainer private constructor(context: Context) {

    private val appContext = context.applicationContext

    val database: UniversDatabase = UniversDatabase.getInstance(appContext)
    val dao: UniversDao = database.universDao()
    val connectivityMonitor: ConnectivityMonitor = ConnectivityMonitor(appContext)

    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        appContext.getString(R.string.firestore_database_id)
    )

    val messagingRepository: MessagingRepository = MessagingRepository(
        db = firestore,
        auth = Firebase.auth,
        localDao = dao
    )

    val callSignalingRepository: CallSignalingRepository = CallSignalingRepository(
        db = firestore,
        auth = Firebase.auth
    )

    val presenceManager: PresenceManager = PresenceManager(
        repository = messagingRepository,
        connectivityMonitor = connectivityMonitor,
        auth = Firebase.auth
    )

    val syncCoordinator: RealtimeSyncCoordinator = RealtimeSyncCoordinator(
        appContext = appContext,
        repository = messagingRepository,
        dao = dao,
        connectivityMonitor = connectivityMonitor,
        presenceManager = presenceManager,
        auth = Firebase.auth
    )

    companion object {
        @Volatile
        private var instance: UniversAppContainer? = null

        fun get(context: Context): UniversAppContainer {
            return instance ?: synchronized(this) {
                instance ?: UniversAppContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
