package js.personal.webrtc_android

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import js.personal.webrtc_android.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var sender = ""
        var receiver = ""

        binding.ready.setOnClickListener {
            sender = binding.sender.text.toString()
            sender = binding.receiver.text.toString()
        }

        binding.offer.setOnClickListener {

        }

        binding.answer.setOnClickListener {

        }

        binding.send.setOnClickListener {

        }
    }
}