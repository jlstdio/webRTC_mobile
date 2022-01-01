package js.personal.webrtc_android

import android.app.Application
import android.content.ContentValues
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import js.personal.webrtc_android.databinding.ActivityMainBinding
import js.personal.webrtc_android.modules.*
import org.webrtc.*
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var rtcClient: RTCClient
    private lateinit var signallingClient: SignalingClient

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val databaseReference = firebaseDatabase.reference

    var me = ""
    var opposite = ""

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
//            signallingClient.send(p0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ready.setOnClickListener {
            me = binding.me.text.toString()
            opposite = binding.opposite.text.toString()
        }

        binding.offer.setOnClickListener {
            initWebRTC(application, false)
            signallingClient = SignalingClient(me, createSignallingClientListener())

            rtcClient.call(sdpObserver, opposite)
        }

        binding.answer.setOnClickListener {
            initWebRTC(application, true)
            signallingClient = SignalingClient(me, createSignallingClientListener())

           // rtcClient.answer(sdpObserver, opposite)
        }

        binding.send.setOnClickListener {

        }
    }

    private fun createSignallingClientListener() = object : SignalingClientListener {
        override fun onConnectionEstablished() {
            //binding.status.text = "ConnectionEstablished"
        }

        override fun onOfferReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            rtcClient.answer(sdpObserver, opposite)
            binding.status.text = "offerReceived"
        }

        override fun onAnswerReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            binding.status.text = "AnswerReceived"
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            rtcClient.addIceCandidate(iceCandidate)
            binding.status.text = "onIceCandidateReceived"

        }

        override fun onCallEnded() {
            if (!Constants.isCallEnded) {
                Constants.isCallEnded = true
                rtcClient.endCall(me, opposite)
                binding.status.text = "ended"
                finish()
            }
        }
    }

    private fun initWebRTC(context: Application, isJoin: Boolean) {
        rtcClient = RTCClient(
            context,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    signallingClient.sendIceCandidate(opposite, p0, isJoin)
                    rtcClient.addIceCandidate(p0)
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    Log.e("test", "onAddStream: $p0")
                    //p0?.videoTracks?.get(0)?.addSink(remote_view)
                }

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    Log.e("test", "onIceConnectionChange: $p0")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.e("test", "onIceConnectionReceivingChange: $p0")
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.e("test", "onConnectionChange: $newState")
                }

                override fun onDataChannel(p0: DataChannel?) {
                    Log.e("test", "onDataChannel: $p0")
                }

                override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.e("test", "onStandardizedIceConnectionChange: $newState")
                }

                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                    Log.e("test", "onAddTrack: $p0 \n $p1")
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    Log.e("test", "onTrack: $transceiver" )
                }
            },
            object : DataChannelObserver() {
                override fun onMessage(buffer: DataChannel.Buffer) {
                    val data: ByteBuffer = buffer.data
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    val command = String(bytes)
                    Log.d(ContentValues.TAG, "DataChannel: onMessage: " + command)
                }

                override fun onBufferedAmountChange(p0: Long) {

                }

                override fun onStateChange() {
                    Log.d(ContentValues.TAG, "DataChannel: onStateChange: ")
                }
            }
        )
    }
}