package js.personal.webrtc_android.modules

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SignalingClient(private val me: String, private val listener: SignalingClientListener) : CoroutineScope {

    companion object {
        private const val HOST_ADDRESS = "192.168.0.12"
    }

    var jsonObject : JSONObject?= null

    private val job = Job()

    val TAG = "SignallingClient"

    var SDPtype : String? = null
    override val coroutineContext = Dispatchers.IO + job

    private val sendChannel = ConflatedBroadcastChannel<String>()

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val databaseReference = firebaseDatabase.reference

    init { addListener(me) }

    fun addListener(me: String) {
        launch {
            listener.onConnectionEstablished()

            databaseReference.child("webRTC").child(me).child("sdp").addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    val type = snapshot.child("type").value as? String ?: "null"
                    val sdp = snapshot.child("sdp").value as? String ?: "null"

                    Log.d("test", snapshot.children.toString())
                    Log.d("test : sdp.sdp", sdp)
                    Log.d("test : sdp.type", type)

                    if (type == "OFFER") {
                        listener.onOfferReceived(SessionDescription(SessionDescription.Type.OFFER, sdp))
                        SDPtype = "Offer"
                    } else if (type == "ANSWER") {
                        listener.onAnswerReceived(SessionDescription(SessionDescription.Type.ANSWER, sdp))
                        SDPtype = "Answer"
                    } else if (!Constants.isIntiatedNow && sdp == "END_CALL") {
                        listener.onCallEnded()
                        SDPtype = "End Call"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    TODO("Not yet implemented")
                }

            })

            databaseReference.child("webRTC").child(me).child("candidates").addChildEventListener(object : ChildEventListener {
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {


                    val CANDIDATEtype = snapshot.child("type").value as? String ?: ""
                    val sdpMid = snapshot.child("sdpMid").value as? String ?: ""
                    val sdpMLineIndex = snapshot.child("sdpMLineIndex").value as? String ?: ""
                    val sdpCandidate = snapshot.child("sdpCandidate").value as? String ?: ""

                    Log.d("test: candidates.type", CANDIDATEtype)

                    if (CANDIDATEtype.isNullOrEmpty()) {
                        if (SDPtype == "Offer" && CANDIDATEtype == "offerCandidate") {
                            listener.onIceCandidateReceived(IceCandidate(sdpMid, Math.toIntExact(sdpMLineIndex as Long), sdpCandidate))
                        } else if (SDPtype == "Answer" && CANDIDATEtype == "answerCandidate") {
                            listener.onIceCandidateReceived(IceCandidate(sdpMid, Math.toIntExact(sdpMLineIndex as Long), sdpCandidate))
                        }
                        //Log.e(TAG, "candidateQuery: ${snapshot.key}" )
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}

                override fun onChildRemoved(snapshot: DataSnapshot) {}

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

                override fun onCancelled(error: DatabaseError) {}

            })
        }
    }

    fun sendIceCandidate(opposite: String, candidate: IceCandidate?, isJoin : Boolean) = runBlocking {
        var type = ""

        when(isJoin) {
            true -> {
                type = "answerCandidate"
            }
            false -> {
                type = "offerCandidate"
            }
        }
        val candidateConstant = hashMapOf(
            "serverUrl" to candidate?.serverUrl,
            "sdpMid" to candidate?.sdpMid,
            "sdpMLineIndex" to candidate?.sdpMLineIndex,
            "sdpCandidate" to candidate?.sdp,
            "type" to type
        )

        databaseReference.child("webRTC").child(opposite).child("candidates").push().setValue(candidateConstant as Map<String, Any>)
    }

    fun destroy() {
//        client.close()
        job.complete()
    }
}