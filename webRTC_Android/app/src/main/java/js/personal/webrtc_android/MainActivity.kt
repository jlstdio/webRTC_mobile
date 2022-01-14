package js.personal.webrtc_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.firebase.database.*
import js.personal.webrtc_android.databinding.ActivityMainBinding
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.HashMap

class MainActivity : AppCompatActivity() {
    private var isInitiator = false
    private var isChannelReady = false
    private var isStarted = false
    private val send: Button? = null
    private val text: EditText? = null

    var audioConstraints: MediaConstraints? = null
    var videoConstraints: MediaConstraints? = null
    var sdpConstraints: MediaConstraints? = null
    var videoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
    var audioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null

    private lateinit var binding: ActivityMainBinding

    private var peerConnection: PeerConnection? = null
    private lateinit var rootEglBase: EglBase
    private var factory: PeerConnectionFactory? = null
    private lateinit var videoTrackFromCamera: VideoTrack

    var me: String? = null
    var opposite: String? = null

    lateinit var dataChannel: DataChannel

    val permissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    var firebaseDatabase = FirebaseDatabase.getInstance()
    var databaseReference = firebaseDatabase.reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

       // checkPermissions()
        start()

        binding.onReady.setOnClickListener {
            me = binding.me.getText().toString()
            opposite = binding.opposite.getText().toString()
            SignalClient()
        }

        binding.offer.setOnClickListener {
            doCall()
        }

        binding.answer.setOnClickListener {
            doAnswer()
        }

        binding.send.setOnClickListener {
            val buff: String = binding.data.getText().toString()
            sendData(buff)
        }
    }

    override fun onDestroy() {

        super.onDestroy()
    }

    private fun checkPermissions() {
        //거절되었거나 아직 수락하지 않은 권한(퍼미션)을 저장할 문자열 배열 리스트
        var rejectedPermissionList = ArrayList<String>()

        //필요한 퍼미션들을 하나씩 끄집어내서 현재 권한을 받았는지 체크
        for(permission in permissions){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                //만약 권한이 없다면 rejectedPermissionList에 추가
                rejectedPermissionList.add(permission)
            }
        }
        //거절된 퍼미션이 있다면... 권한 요청
        if(rejectedPermissionList.isNotEmpty()){
            val array = arrayOfNulls<String>(rejectedPermissionList.size)
            ActivityCompat.requestPermissions(this, rejectedPermissionList.toArray(array), 89)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 89) {
            if(grantResults.isNotEmpty()) {
                for((i, permission) in permissions.withIndex()) {
                    if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                        finishAffinity()
                    }
                }
            }
            else {
                //start()
            }
        }
    }

    private fun start() {
        initializeSurfaceViews()
        initializePeerConnectionFactory()
        createVideoTrackFromCameraAndShowIt()
        initializePeerConnections()
        startStreamingVideo()
    }

    private fun SignalClient() {
        databaseReference.child("webRTC").child(me!!).child("sdp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val type = snapshot.child("type").getValue(String::class.java)
                    val sdp = snapshot.child("sdp").getValue(String::class.java)
                    if (type != null && sdp != null) {
                        if (type == "answer") {
                            peerConnection!!.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(SessionDescription.Type.ANSWER, sdp)
                            )
                        } else if (type == "offer") {
                            peerConnection!!.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(SessionDescription.Type.OFFER, sdp)
                            ) // implemented
                            //doAnswer();
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        databaseReference.child("webRTC").child(me!!).child("candidates")
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

    private fun doAnswer() {
        peerConnection!!.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription) {
                super.onCreateSuccess(p0)

                peerConnection!!.setLocalDescription(SimpleSdpObserver(), p0)
                val message = JSONObject()
                val map = HashMap<String, String>()
                try {
                    message.put("type", "answer")
                    message.put("sdp", p0.description)
                    map["type"] = "answer"
                    map["sdp"] = p0.description
                    databaseReference.child("webRTC").child(opposite!!).child("sdp").setValue(map)

                    //sendMessage(message);
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, MediaConstraints())
    }

    private fun maybeStart() {
        Log.d(TAG, "maybeStart: $isStarted $isChannelReady")
        if (!isStarted && isChannelReady) {
            isStarted = true
            if (isInitiator) {
                doCall()
            }
        }
    }

    private fun doCall() {
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
        peerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription) {
                super.onCreateSuccess(p0)

                Log.d(TAG, "onCreateSuccess: ")
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), p0)
                val message = JSONObject()
                val map = HashMap<String, String>()
                try {
                    message.put("type", "offer")
                    message.put("sdp", p0.description)
                    map["type"] = "offer"
                    map["sdp"] = p0.description
                    databaseReference.child("webRTC").child(opposite!!).child("sdp").setValue(map)

                    //sendMessage(message);
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, sdpMediaConstraints)
    }

    private fun initializeSurfaceViews() {
        rootEglBase = EglBase.create()
        binding.surfaceView.init(rootEglBase.getEglBaseContext(), null)
        binding.surfaceView.setEnableHardwareScaler(true)
        binding.surfaceView.setMirror(true)
        binding.surfaceView2.init(rootEglBase.getEglBaseContext(), null)
        binding.surfaceView2.setEnableHardwareScaler(true)
        binding.surfaceView2.setMirror(true)

        //add one more
    }

    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
        factory = PeerConnectionFactory(null)
        factory!!.setVideoHwAccelerationOptions(
            rootEglBase!!.eglBaseContext,
            rootEglBase!!.eglBaseContext
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
            }
        })
    }

    fun sendData(data: String) {
        val buffer = ByteBuffer.wrap(data.toByteArray())
        dataChannel!!.send(DataChannel.Buffer(buffer, false))
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
                val message = JSONObject()
                val map = HashMap<String, String>()
                try {
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)
                    map["sdpMLineIndex"] = Integer.toString(iceCandidate.sdpMLineIndex)
                    map["sdpMid"] = iceCandidate.sdpMid
                    map["sdp"] = iceCandidate.sdp
                    Log.d(
                        TAG,
                        "onIceCandidate: sending candidate $message"
                    )
                    databaseReference.child("webRTC").child(opposite!!).child("candidates").push()
                        .setValue(map)

                    //sendMessage(message);
                } catch (e: JSONException) {
                    e.printStackTrace()
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
                var dataChannel = dataChannel
                Log.d(TAG, "onDataChannel: ")
                dataChannel = dataChannel
                val channelName = dataChannel.label()
                dataChannel.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(l: Long) {}
                    override fun onStateChange() {}
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        val data = buffer.data
                        val bytes = ByteArray(data.remaining())
                        data[bytes]
                        val command = String(bytes)
                        Toast.makeText(
                            applicationContext,
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
        val videoCapturer: VideoCapturer?
        videoCapturer = if (useCamera2()) {
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
        private const val RC_CALL = 111
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 1280
        const val VIDEO_RESOLUTION_HEIGHT = 720
        const val FPS = 30
    }
}