package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CallQualityPreset
import com.example.data.model.CallSession
import com.example.data.model.CallTarget
import com.example.data.model.IceCandidateSignal
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CallSignalingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing call signaling.")
    }

    private fun <T> Flow<T>.logFirestoreErrors(
        operationType: OperationType,
        path: String,
        expectedUid: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
        throw error
    }

    fun observeIncomingRingingCalls(userId: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(CALLS_COLLECTION)
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.calleeId == userId && it.status == CallSession.STATUS_RINGING }
                        .sortedByDescending { (it.createdAt ?: Timestamp(0, 0)).toDate().time }
                }
                .logFirestoreErrors(OperationType.LIST, CALLS_COLLECTION, uid)
        )
    }

    fun observeCallHistory(userId: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(CALLS_COLLECTION)
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { (it.createdAt ?: Timestamp(0, 0)).toDate().time }
                        .take(40)
                }
                .logFirestoreErrors(OperationType.LIST, CALLS_COLLECTION, uid)
        )
    }

    fun observeCallSession(callId: String): Flow<CallSession?> = flow {
        val uid = requireUserId()
        val path = "$CALLS_COLLECTION/$callId"
        emitAll(
            db.collection(CALLS_COLLECTION).document(callId)
                .snapshots()
                .map { it.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    suspend fun getCallSession(callId: String): Result<CallSession?> = runCatching {
        requireUserId()
        val snap = db.collection(CALLS_COLLECTION).document(callId).get().await()
        snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }

    fun observeRemoteCandidates(callId: String, currentUserId: String): Flow<List<IceCandidateSignal>> = flow {
        val uid = requireUserId()
        val path = "$CALLS_COLLECTION/$callId/$CANDIDATES_SUBCOLLECTION"
        emitAll(
            db.collection(CALLS_COLLECTION)
                .document(callId)
                .collection(CANDIDATES_SUBCOLLECTION)
                .whereArrayContains("participantIds", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(IceCandidateSignal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.senderId != currentUserId }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun allocateCallId(): String = db.collection(CALLS_COLLECTION).document().id

    suspend fun createCallSession(
        callId: String,
        callerProfile: UserProfile,
        target: CallTarget,
        callType: String,
        qualityPreset: CallQualityPreset,
        offerSdp: String
    ): Result<CallSession> = runCatching {
        val uid = requireUserId()
        require(uid == callerProfile.userId) { "Authenticated user mismatch." }
        require(target.userId.isNotBlank() && target.userId != uid) { "Select another user to call." }
        require(offerSdp.length in 10..25_000) { "Invalid WebRTC offer SDP." }

        val participantIds = listOf(uid, target.userId)
        val docRef = db.collection(CALLS_COLLECTION).document(callId)
        val payload = mutableMapOf<String, Any>(
            "callerId" to uid,
            "callerName" to callerProfile.displayName.take(60).ifBlank { "Member" },
            "calleeId" to target.userId,
            "calleeName" to target.displayName.take(60).ifBlank { "Member" },
            "participantIds" to participantIds,
            "conversationId" to target.conversationId,
            "callType" to callType,
            "qualityPreset" to qualityPreset.wireValue,
            "status" to CallSession.STATUS_RINGING,
            "offerSdp" to offerSdp,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        callerProfile.photoUrl.takeIf { it.isNotBlank() }?.let {
            payload["callerPhotoUrl"] = it.take(500)
        }
        target.photoUrl?.takeIf { it.isNotBlank() }?.let {
            payload["calleePhotoUrl"] = it.take(500)
        }

        try {
            docRef.set(payload).await()
            CallSession(
                id = callId,
                callerId = uid,
                callerName = payload["callerName"] as String,
                callerPhotoUrl = payload["callerPhotoUrl"] as String?,
                calleeId = target.userId,
                calleeName = payload["calleeName"] as String,
                calleePhotoUrl = payload["calleePhotoUrl"] as String?,
                participantIds = participantIds,
                conversationId = target.conversationId,
                callType = callType,
                qualityPreset = qualityPreset.wireValue,
                status = CallSession.STATUS_RINGING,
                offerSdp = offerSdp
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun acceptCall(callId: String, answerSdp: String): Result<Unit> = runCatching {
        requireUserId()
        require(answerSdp.length in 10..25_000) { "Invalid WebRTC answer SDP." }
        val docRef = db.collection(CALLS_COLLECTION).document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to CallSession.STATUS_ACCEPTED,
                    "answerSdp" to answerSdp,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun declineCall(callId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection(CALLS_COLLECTION).document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to CallSession.STATUS_DECLINED,
                    "endedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun endCall(callId: String, asMissed: Boolean = false): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection(CALLS_COLLECTION).document(callId)
        val status = if (asMissed) CallSession.STATUS_MISSED else CallSession.STATUS_ENDED
        try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "endedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateQualityPreset(callId: String, qualityPreset: CallQualityPreset): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection(CALLS_COLLECTION).document(callId)
        try {
            docRef.update(
                mapOf(
                    "qualityPreset" to qualityPreset.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun sendIceCandidate(
        callId: String,
        participantIds: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(participantIds.size == 2 && uid in participantIds) { "Invalid call participants." }
        val candidateRef = db.collection(CALLS_COLLECTION)
            .document(callId)
            .collection(CANDIDATES_SUBCOLLECTION)
            .document()
        val payload = mapOf(
            "callId" to callId,
            "senderId" to uid,
            "participantIds" to participantIds,
            "sdpMid" to sdpMid.take(64),
            "sdpMLineIndex" to sdpMLineIndex,
            "candidate" to candidate.take(2000),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            candidateRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, candidateRef.path, auth)
            throw e
        }
    }

    companion object {
        const val CALLS_COLLECTION = "calls"
        const val CANDIDATES_SUBCOLLECTION = "candidates"
    }
}
