package js.personal.webrtc_android

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.firebase.database.*
import js.personal.webrtc_android.databinding.ActivityMainBinding
import org.webrtc.*
import org.webrtc.PeerConnection.*
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.HashMap

class MainActivity : AppCompatActivity() {

    private var audioConstraints: MediaConstraints? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var peerConnection: PeerConnection? = null
    private lateinit var rootEglBase: EglBase
    private var factory: PeerConnectionFactory? = null
    private lateinit var videoTrackFromCamera: VideoTrack

    private lateinit var binding: ActivityMainBinding
    
    private lateinit var me: String
    private lateinit var opposite: String

    private lateinit var dataChannel: DataChannel

    private var firebaseDatabase = FirebaseDatabase.getInstance()
    private var databaseReference = firebaseDatabase.reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        Toast.makeText(this, "We don't check permissions, go to setting if it doesn't work", Toast.LENGTH_LONG).show()
        start()

        binding.onReady.setOnClickListener {
            me = binding.me.text.toString()
            opposite = binding.opposite.text.toString()
            SignalClient()
        }

        binding.offer.setOnClickListener {
            Offer()
        }

        binding.answer.setOnClickListener {
            Answer()
        }

        binding.send.setOnClickListener {
            val buff: String = binding.data.text.toString()
            sendData(buff)
        }
    }

    override fun onDestroy() {

        databaseReference.child("webRTC").child(me).removeValue()
        databaseReference.child("webRTC").child(opposite).removeValue()
        super.onDestroy()
    }

    private fun start() {
        initializeSurfaceViews()
        initializePeerConnectionFactory()
        createVideoTrackFromCameraAndShowIt()
        initializePeerConnections()
        startStreamingVideo()
    }

    private fun SignalClient() {
        databaseReference.child("webRTC").child(me).child("sdp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val type = snapshot.child("type").getValue(String::class.java)
                    val sdp = snapshot.child("sdp").getValue(String::class.java)
                    if (type != null && sdp != null) {
                        if (type == "answer") {
                            peerConnection!!.setRemoteDescription(
                                SdpObserver(),
                                SessionDescription(SessionDescription.Type.ANSWER, sdp)
                            )
                        } else if (type == "offer") {
                            peerConnection!!.setRemoteDescription(
                                SdpObserver(),
                                SessionDescription(SessionDescription.Type.OFFER, sdp)
                            )
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        databaseReference.child("webRTC").child(me).child("candidates")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    Log.d(TAG, "connectToSignallingServer: receiving candidates")
                    val id = snapshot.child("sdpMid").getValue(String::class.java)
                    val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(
                        String::class.java
                    )!!.toInt()
                    val sdpCandidate = snapshot.child("sdp").getValue(
                        String::class.java
                    )
                    val candidateData = IceCandidate(id, sdpMLineIndex, sdpCandidate)
                    peerConnection!!.addIceCandidate(candidateData)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun Answer() {
        peerConnection!!.createAnswer(object : SdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription) {
                super.onCreateSuccess(p0)

                peerConnection!!.setLocalDescription(SdpObserver(), p0)
                val map = HashMap<String, String>()
                try {
                    map["type"] = "answer"
                    map["sdp"] = p0.description
                    databaseReference.child("webRTC").child(opposite).child("sdp").setValue(map)

                } catch (e: Exception) {

                }
            }
        }, MediaConstraints())
    }

    private fun Offer() {
        val sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio",
                "true"
            )
        )
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo",
                "true"
            )
        )
        peerConnection!!.createOffer(object : SdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription) {
                super.onCreateSuccess(p0)

                Log.d(TAG, "onCreateSuccess: ")
                peerConnection!!.setLocalDescription(SdpObserver(), p0)
                val map = HashMap<String, String>()
                try {
                    map["type"] = "offer"
                    map["sdp"] = p0.description
                    databaseReference.child("webRTC").child(opposite).child("sdp").setValue(map)

                } catch (e: Exception) { }
            }
        }, sdpMediaConstraints)
    }

    private fun initializeSurfaceViews() {
        rootEglBase = EglBase.create()
        binding.surfaceView.init(rootEglBase.eglBaseContext, null)
        binding.surfaceView.setEnableHardwareScaler(true)
        binding.surfaceView.setMirror(true)
        binding.surfaceView2.init(rootEglBase.eglBaseContext, null)
        binding.surfaceView2.setEnableHardwareScaler(true)
        binding.surfaceView2.setMirror(true)

        //add one more
    }

    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
        factory = PeerConnectionFactory(null)
        factory!!.setVideoHwAccelerationOptions(
            rootEglBase.eglBaseContext,
            rootEglBase.eglBaseContext
        )
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        audioConstraints = MediaConstraints()
        val videoCapturer = createVideoCapturer()
        val videoSource = factory!!.createVideoSource(videoCapturer)
        videoCapturer!!.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)
        videoTrackFromCamera = factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera.setEnabled(true)
        videoTrackFromCamera.addRenderer(VideoRenderer(binding.surfaceView))

        //create an AudioSource instance
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudioTrack = factory!!.createAudioTrack("101", audioSource)
    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory)
        val dcInit = DataChannel.Init()
        dcInit.id = 1
        dataChannel = peerConnection!!.createDataChannel("1", dcInit)
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(l: Long) {}
            override fun onStateChange() {
                Log.d("test", "state changed")
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data[bytes]
                val command = String(bytes)
                //Toast.makeText(getBaseContext(), "incoming2 : " + command, Toast.LENGTH_SHORT).show();
                Log.d("test", command)
                //Toast.makeText(this, "We don't check permissions, go to setting if it doesn't work", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun sendData(data: String) {
        val buffer = ByteBuffer.wrap(data.toByteArray())
        dataChannel.send(DataChannel.Buffer(buffer, false))
    }

    private fun startStreamingVideo() {
        val mediaStream = factory!!.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera)
        mediaStream.addTrack(localAudioTrack)
        peerConnection!!.addStream(mediaStream)

        //sendMessage("got user media");
    }

    private fun createPeerConnection(factory: PeerConnectionFactory?): PeerConnection {
        val iceServers = ArrayList<IceServer>()
        val URL = "stun:stun.l.google.com:19302"
        iceServers.add(IceServer(URL))
        val rtcConfig = RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: Observer = object : Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                Log.d(TAG, "onSignalingChange: ")
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ")
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.d(TAG, "onIceConnectionReceivingChange: ")
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d(TAG, "onIceCandidate: ")
                val map = HashMap<String, String>()
                try {
                    map["sdpMLineIndex"] = iceCandidate.sdpMLineIndex.toString()
                    map["sdpMid"] = iceCandidate.sdpMid
                    map["sdp"] = iceCandidate.sdp
                    Log.d(TAG, "onIceCandidate: sending candidate")
                    databaseReference.child("webRTC").child(opposite).child("candidates").push().setValue(map)
                } catch (e: Exception) {

                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.d(TAG, "onIceCandidatesRemoved: ")
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size)
                val remoteVideoTrack = mediaStream.videoTracks[0]
                val remoteAudioTrack = mediaStream.audioTracks[0]
                remoteAudioTrack.setEnabled(true)
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addRenderer(VideoRenderer(binding.surfaceView2))
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d(TAG, "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d(TAG, "onDataChannel: ")
                dataChannel.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(l: Long) {}
                    override fun onStateChange() {}
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        val data = buffer.data
                        val bytes = ByteArray(data.remaining())
                        data[bytes]
                        val command = String(bytes)
                        Toast.makeText(
                            this@MainActivity,
                            "incoming : $command",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ")
            }
        }
        return factory!!.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer? = if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
        return videoCapturer
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }

    companion object {
        private const val TAG = "CompleteActivity"
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 1280
        const val VIDEO_RESOLUTION_HEIGHT = 720
        const val FPS = 30
    }
}