package js.personal.webrtc_android.modules

import org.webrtc.DataChannel

open class DataChannelObserver: DataChannel.Observer {
    override fun onMessage(buffer: DataChannel.Buffer) {
    }

    override fun onBufferedAmountChange(p0: Long) {
    }

    override fun onStateChange() {
    }
}